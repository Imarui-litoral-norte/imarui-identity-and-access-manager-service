package br.com.imarui.ima.identity.infra.persistence.affiliation;

import br.com.imarui.ima.identity.core.application.command.identity.ActivateIdentityCommand;
import br.com.imarui.ima.identity.core.application.command.identity.SelfDeleteIdentityCommand;
import br.com.imarui.ima.identity.core.domain.enums.affiliation.AffiliationStatus;
import br.com.imarui.ima.identity.core.domain.enums.affiliation.AffiliationType;
import br.com.imarui.ima.identity.core.domain.enums.identity.IdentityKind;
import br.com.imarui.ima.identity.core.domain.model.affiliation.AffiliationId;
import br.com.imarui.ima.identity.core.domain.model.identity.IdentityId;
import br.com.imarui.ima.identity.support.IdentityIntegrationSupport;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.*;

class AffiliationHistoryIntegrationTest extends IdentityIntegrationSupport {
    static Stream<Arguments> identityAndAffiliationKinds() {
        return Arrays.stream(IdentityKind.values()).flatMap(kind ->
                Arrays.stream(AffiliationType.values()).map(type -> Arguments.of(kind, type)));
    }

    @ParameterizedTest
    @MethodSource("identityAndAffiliationKinds")
    void repeatedDeletionPreservesSeparateAuditAndReleasesAllNaturalKeys(IdentityKind kind, AffiliationType type) {
        UUID firstIdentity = null;
        UUID firstHistory = null;
        for (int cycle = 0; cycle < 2; cycle++) {
            actor(reviewer.getId());
            var applicationId = request(kind, "reusable@example.test");
            var approved = approve.execute(approval(applicationId, type, "REUSABLE-CODE"));
            var identityId = IdentityId.from(approved.resolvedIdentityId());
            var affiliationId = AffiliationId.from(approved.resultingAffiliationId());
            activate.execute(new ActivateIdentityCommand(identityId));
            var before = affiliations.findById(affiliationId).orElseThrow();
            actor(identityId);
            var lowerBound = Instant.now().minusSeconds(1);
            delete.execute(new SelfDeleteIdentityCommand(" audit cycle " + cycle + " "));

            assertTrue(identities.findById(identityId).isEmpty());
            assertTrue(affiliations.findById(affiliationId).isEmpty());
            assertEquals(0, jdbc.queryForObject("select count(*) from ima_affiliations where identity_id = ?", Integer.class, identityId.value()));
            assertTrue(tenants.findById(tenant.getId()).isPresent());
            assertTrue(identities.findById(reviewer.getId()).isPresent());
            var identitySnapshot = history.findAll().stream()
                    .filter(row -> row.getOriginalIdentityId().equals(identityId.value())).findFirst().orElseThrow();
            var snapshots = affiliationHistory.findByIdentityHistoryId(identitySnapshot.getId());
            assertEquals(1, snapshots.size());
            var snapshot = snapshots.get(0);
            assertNotEquals(affiliationId.value(), snapshot.getId());
            assertEquals(affiliationId.value(), snapshot.getOriginalAffiliationId());
            assertEquals(identityId.value(), snapshot.getIdentityId());
            assertEquals(tenant.getId().value(), snapshot.getTenantId());
            assertEquals(type, snapshot.getType());
            assertEquals(AffiliationStatus.ACTIVE, snapshot.getPreviousStatus());
            assertEquals(before.getStartedAt(), snapshot.getStartedAt());
            assertEquals(before.getUpdatedAt(), snapshot.getUpdatedAt());
            assertNull(snapshot.getEndedAt());
            assertEquals(0, snapshot.getOriginalVersion());
            assertEquals(identityId.value(), snapshot.getActorId());
            assertEquals("audit cycle " + cycle, snapshot.getReason());
            assertEquals(identitySnapshot.getDeletedAt(), snapshot.getDeletedAt());
            assertFalse(snapshot.getDeletedAt().isBefore(lowerBound));
            assertFalse(snapshot.getDeletedAt().isAfter(Instant.now()));
            assertEquals(type == AffiliationType.EMPLOYEE ? "REUSABLE-CODE" : null, snapshot.getRegistration());
            assertEquals(type == AffiliationType.CUSTOMER ? "REUSABLE-CODE" : null, snapshot.getCustomerCode());
            assertEquals(type == AffiliationType.SUPPLIER ? "REUSABLE-CODE" : null, snapshot.getSupplierCode());
            var application = applications.findById(applicationId).orElseThrow();
            assertTrue(application.isApproved());
            assertEquals(identityId, application.getResolvedIdentityId());
            assertEquals(affiliationId, application.getResultingAffiliationId());
            if (cycle == 0) {
                firstIdentity = identityId.value();
                firstHistory = snapshot.getId();
            } else {
                assertNotEquals(firstIdentity, identityId.value());
                assertNotEquals(firstHistory, snapshot.getId());
                assertEquals(firstIdentity, affiliationHistory.findById(firstHistory).orElseThrow().getIdentityId());
                assertEquals("audit cycle 0", affiliationHistory.findById(firstHistory).orElseThrow().getReason());
            }
        }
        assertEquals(2, history.count());
        assertEquals(2, affiliationHistory.count());
    }

    @ParameterizedTest
    @EnumSource(AffiliationType.class)
    void inactiveAffiliationsAreArchivedWithTheirActualLifecycle(AffiliationType type) {
        var applicationId = request(IdentityKind.SERVICE, "inactive@example.test");
        var approved = approve.execute(approval(applicationId, type, "INACTIVE-CODE"));
        var identityId = IdentityId.from(approved.resolvedIdentityId());
        activate.execute(new ActivateIdentityCommand(identityId));
        var affiliation = affiliations.findById(AffiliationId.from(approved.resultingAffiliationId())).orElseThrow();
        affiliation.deactivate(Instant.now());
        affiliations.save(affiliation);
        affiliation = affiliations.findById(affiliation.getId()).orElseThrow();
        actor(identityId);
        delete.execute(new SelfDeleteIdentityCommand(null));
        var snapshot = affiliationHistory.findAll().get(0);
        assertEquals(AffiliationStatus.INACTIVE, snapshot.getPreviousStatus());
        assertEquals(affiliation.getEndedAt(), snapshot.getEndedAt());
        assertEquals(affiliation.getUpdatedAt(), snapshot.getUpdatedAt());
        assertEquals(1, snapshot.getOriginalVersion());
        assertNull(snapshot.getReason());
        assertTrue(affiliations.findById(affiliation.getId()).isEmpty());
    }

    @Test
    void affiliationHistoryFailureRollsBackBothArchivesDeletionAndRevocation() {
        var approved = approve.execute(approval(request(IdentityKind.SERVICE, "rollback@example.test"), AffiliationType.EMPLOYEE, "ROLLBACK"));
        var identityId = IdentityId.from(approved.resolvedIdentityId());
        var affiliationId = AffiliationId.from(approved.resultingAffiliationId());
        activate.execute(new ActivateIdentityCommand(identityId));
        actor(identityId);
        jdbc.execute("create function fail_affiliation_history() returns trigger language plpgsql as $$ begin raise exception 'simulated affiliation history failure'; end $$");
        jdbc.execute("create trigger fail_affiliation_history before insert on ima_affiliations__history for each row execute function fail_affiliation_history()");
        try {
            assertThrows(RuntimeException.class, () -> delete.execute(new SelfDeleteIdentityCommand(null)));
        } finally {
            jdbc.execute("drop trigger fail_affiliation_history on ima_affiliations__history");
            jdbc.execute("drop function fail_affiliation_history()");
        }
        assertTrue(identities.findById(identityId).isPresent());
        assertTrue(affiliations.findById(affiliationId).isPresent());
        assertEquals(0, history.count());
        assertEquals(0, affiliationHistory.count());
        assertEquals(0, revocations.count());
    }

    @Test
    void affiliationDeleteFailureRollsBackBothArchivesAndPreservesSpecialization() {
        var approved = approve.execute(approval(request(IdentityKind.SERVICE, "delete-failure@example.test"), AffiliationType.SUPPLIER, "ROLLBACK"));
        var identityId = IdentityId.from(approved.resolvedIdentityId());
        var affiliationId = AffiliationId.from(approved.resultingAffiliationId());
        activate.execute(new ActivateIdentityCommand(identityId));
        actor(identityId);
        jdbc.execute("create function fail_affiliation_delete() returns trigger language plpgsql as $$ begin raise exception 'simulated affiliation deletion failure'; end $$");
        jdbc.execute("create trigger fail_affiliation_delete before delete on ima_supplier_affiliations for each row execute function fail_affiliation_delete()");
        try {
            assertThrows(RuntimeException.class, () -> delete.execute(new SelfDeleteIdentityCommand(null)));
        } finally {
            jdbc.execute("drop trigger fail_affiliation_delete on ima_supplier_affiliations");
            jdbc.execute("drop function fail_affiliation_delete()");
        }
        assertTrue(identities.findById(identityId).isPresent());
        assertTrue(affiliations.findById(affiliationId).isPresent());
        assertEquals("ROLLBACK", jdbc.queryForObject("select supplier_code from ima_supplier_affiliations where id = ?", String.class, affiliationId.value()));
        assertEquals(0, history.count());
        assertEquals(0, affiliationHistory.count());
        assertEquals(0, revocations.count());
    }

    @Test
    void deletionArchivesAllTenantsAndInactiveLinksWithoutTouchingAnotherIdentity() {
        var otherTenant = br.com.imarui.ima.identity.core.domain.model.tenant.Tenant.create(
                br.com.imarui.ima.identity.core.domain.model.tenant.TenantId.generate(),
                br.com.imarui.ima.identity.core.domain.model.tenant.TenantCode.from("OTHER"),
                br.com.imarui.ima.identity.core.domain.model.tenant.TenantName.from("Other tenant"), earlier);
        otherTenant.activate(earlier);
        otherTenant = tenants.save(otherTenant);
        var first = affiliations.save(br.com.imarui.ima.identity.core.domain.model.affiliation.ExternalAffiliation.create(
                AffiliationId.generate(), tenant.getId(), reviewer.getId(), earlier));
        var inactive = br.com.imarui.ima.identity.core.domain.model.affiliation.ExternalAffiliation.create(
                AffiliationId.generate(), otherTenant.getId(), reviewer.getId(), earlier);
        inactive.deactivate(earlier.plusSeconds(1));
        inactive = (br.com.imarui.ima.identity.core.domain.model.affiliation.ExternalAffiliation) affiliations.save(inactive);
        var third = affiliations.save(br.com.imarui.ima.identity.core.domain.model.affiliation.PartnerAffiliation.create(
                AffiliationId.generate(), otherTenant.getId(), reviewer.getId(), earlier));
        var unrelated = service("unrelated@example.test");
        unrelated.activate(earlier);
        unrelated = identities.save(unrelated);
        var untouched = affiliations.save(br.com.imarui.ima.identity.core.domain.model.affiliation.ExternalAffiliation.create(
                AffiliationId.generate(), tenant.getId(), unrelated.getId(), earlier));
        delete.execute(new SelfDeleteIdentityCommand("Closed account"));
        assertTrue(identities.findById(reviewer.getId()).isEmpty());
        assertTrue(affiliations.findById(first.getId()).isEmpty());
        assertTrue(affiliations.findById(inactive.getId()).isEmpty());
        assertTrue(affiliations.findById(third.getId()).isEmpty());
        assertTrue(affiliations.findById(untouched.getId()).isPresent());
        assertTrue(identities.findById(unrelated.getId()).isPresent());
        assertEquals(2, jdbc.queryForObject("select count(*) from ima_tenants", Integer.class));
        assertEquals(1, history.count());
        var snapshots = affiliationHistory.findByIdentityHistoryId(history.findAll().get(0).getId());
        assertEquals(3, snapshots.size());
        assertEquals(1, snapshots.stream().filter(row -> row.getPreviousStatus() == AffiliationStatus.INACTIVE).count());
        assertTrue(snapshots.stream().allMatch(row -> row.getIdentityId().equals(reviewer.getId().value())));
    }

    @Test
    void historicalKeysHaveNoOperationalForeignKeysOrNaturalKeyUniqueness() {
        for (var table : new String[]{"ima_identities__history", "ima_affiliations__history"}) {
            assertEquals(0, jdbc.queryForObject("select count(*) from pg_constraint where conrelid = cast(? as regclass) and contype in ('f', 'u')", Integer.class, table));
            assertEquals(1, jdbc.queryForObject("select count(*) from pg_index where indrelid = cast(? as regclass) and indisunique", Integer.class, table));
        }
    }
}
