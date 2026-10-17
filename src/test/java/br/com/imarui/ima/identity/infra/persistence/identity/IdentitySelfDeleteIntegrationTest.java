package br.com.imarui.ima.identity.infra.persistence.identity;

import br.com.imarui.ima.identity.core.application.command.identity.*;
import br.com.imarui.ima.identity.core.application.command.identityapplication.*;
import br.com.imarui.ima.identity.core.application.command.tenant.*;
import br.com.imarui.ima.identity.core.application.usecase.identity.*;
import br.com.imarui.ima.identity.core.application.usecase.identityapplication.*;
import br.com.imarui.ima.identity.core.application.usecase.tenant.*;
import br.com.imarui.ima.identity.core.application.exception.*;
import br.com.imarui.ima.identity.core.domain.enums.identity.*;
import br.com.imarui.ima.identity.core.domain.enums.affiliation.*;
import br.com.imarui.ima.identity.core.domain.model.identity.*;
import br.com.imarui.ima.identity.core.domain.model.identity.person.*;
import br.com.imarui.ima.identity.core.domain.model.identity.LegalEntity.*;
import br.com.imarui.ima.identity.core.domain.model.identity.service.*;
import br.com.imarui.ima.identity.core.domain.model.identityapplication.*;
import br.com.imarui.ima.identity.core.domain.model.affiliation.*;
import br.com.imarui.ima.identity.core.domain.model.tenant.*;
import br.com.imarui.ima.identity.core.repository.*;
import br.com.imarui.ima.identity.infra.persistence.jpa.*;
import br.com.imarui.ima.platform.security.jwt.JwtSigningKeyProvider;
import io.jsonwebtoken.Jwts;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.net.URI;
import java.net.http.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import br.com.imarui.ima.identity.support.IdentityIntegrationSupport;

class IdentitySelfDeleteIntegrationTest extends IdentityIntegrationSupport {
    @ParameterizedTest @EnumSource(IdentityKind.class)
    void selfDeleteCommitsFullSnapshotRemovesJoinedRowsAndPermitsReregistration(IdentityKind kind) {
        var identity = sample(kind,"deleted@example.test"); identity.activate(earlier); identity = identities.save(identity);
        affiliations.save(ExternalAffiliation.create(AffiliationId.generate(),tenant.getId(),identity.getId(),earlier));
        actor(identity.getId()); delete.execute(new SelfDeleteIdentityCommand("Requested by owner"));
        assertTrue(identities.findById(identity.getId()).isEmpty());
        assertEquals(0, jdbc.queryForObject("select count(*) from ima_affiliations where identity_id = ?",Integer.class,identity.getId().value()));
        var archived = history.findAll().get(0);
        assertEquals(identity.getId().value(),archived.getOriginalIdentityId()); assertEquals(kind.name(),archived.getKind());
        assertEquals("ACTIVE",archived.getPreviousStatus()); assertEquals(identity.getId().value(),archived.getActorId());
        assertEquals("deleted@example.test",json.readTree(archived.getSnapshot()).path("primaryEmail").path("email").asText());
        String specific = switch(kind) { case PERSON -> "cpf"; case LEGAL_ENTITY -> "cnpj"; case SERVICE -> "name"; };
        assertFalse(json.readTree(archived.getSnapshot()).path(specific).isMissingNode());
        assertEquals(1,revocations.count());
        String table = switch(kind) { case PERSON -> "ima_person_identities"; case LEGAL_ENTITY -> "ima_legal_entity_identities"; case SERVICE -> "ima_service_identities"; };
        assertEquals(0,jdbc.queryForObject("select count(*) from "+table+" where id = ?",Integer.class,identity.getId().value()));
        assertNotNull(identities.save(sample(kind,"deleted@example.test")));
    }

    @Test void failingHistoryInsertRollsBackDeletionAndRevocationRequest() {
        var id = reviewer.getId();
        jdbc.execute("create function fail_identity_history() returns trigger language plpgsql as $$ begin raise exception 'simulated history failure'; end $$");
        jdbc.execute("create trigger fail_identity_history before insert on ima_identities__history for each row execute function fail_identity_history()");
        try { assertThrows(RuntimeException.class, () -> delete.execute(new SelfDeleteIdentityCommand(null))); }
        finally { jdbc.execute("drop trigger fail_identity_history on ima_identities__history"); jdbc.execute("drop function fail_identity_history()"); }
        assertTrue(identities.findById(id).isPresent()); assertEquals(0,history.count()); assertEquals(0,revocations.count());
    }

    @Test void failingOperationalDeleteRollsBackAlreadyWrittenHistory() {
        var id = reviewer.getId();
        var affiliation = affiliations.save(ExternalAffiliation.create(AffiliationId.generate(),tenant.getId(),id,earlier));
        jdbc.execute("create function fail_identity_delete() returns trigger language plpgsql as $$ begin raise exception 'simulated delete failure'; end $$");
        jdbc.execute("create trigger fail_identity_delete before delete on ima_identities for each row execute function fail_identity_delete()");
        try { assertThrows(RuntimeException.class, () -> delete.execute(new SelfDeleteIdentityCommand(null))); }
        finally { jdbc.execute("drop trigger fail_identity_delete on ima_identities"); jdbc.execute("drop function fail_identity_delete()"); }
        assertTrue(affiliations.findById(affiliation.getId()).isPresent());
        assertTrue(identities.findById(id).isPresent()); assertEquals(0,history.count()); assertEquals(0,revocations.count());
    }
}
