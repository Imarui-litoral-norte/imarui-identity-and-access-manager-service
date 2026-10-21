package br.com.imarui.ima.identity.infra.persistence.identityapplication;

import static org.junit.jupiter.api.Assertions.*;

import br.com.imarui.ima.identity.core.application.command.identity.*;
import br.com.imarui.ima.identity.core.application.command.identityapplication.*;
import br.com.imarui.ima.identity.core.application.command.tenant.*;
import br.com.imarui.ima.identity.core.application.exception.*;
import br.com.imarui.ima.identity.core.application.usecase.identity.*;
import br.com.imarui.ima.identity.core.application.usecase.identityapplication.*;
import br.com.imarui.ima.identity.core.application.usecase.tenant.*;
import br.com.imarui.ima.identity.core.domain.enums.affiliation.*;
import br.com.imarui.ima.identity.core.domain.enums.identity.*;
import br.com.imarui.ima.identity.core.domain.model.affiliation.*;
import br.com.imarui.ima.identity.core.domain.model.identity.*;
import br.com.imarui.ima.identity.core.domain.model.identity.LegalEntity.*;
import br.com.imarui.ima.identity.core.domain.model.identity.person.*;
import br.com.imarui.ima.identity.core.domain.model.identity.service.*;
import br.com.imarui.ima.identity.core.domain.model.identityapplication.*;
import br.com.imarui.ima.identity.core.domain.model.tenant.*;
import br.com.imarui.ima.identity.core.repository.*;
import br.com.imarui.ima.identity.infra.persistence.jpa.*;
import br.com.imarui.ima.identity.support.IdentityIntegrationSupport;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.security.core.context.SecurityContextHolder;

class IdentityApplicationIntegrationTest extends IdentityIntegrationSupport {
  @ParameterizedTest
  @EnumSource(IdentityKind.class)
  void approvalPersistsCorrectKindAndReviewerAndCannotResolveTwice(IdentityKind kind) {
    var id = request(kind, "new@example.test");
    var result = approve.execute(approval(id, AffiliationType.EXTERNAL, null));
    assertNotNull(result.reviewedAt());
    assertEquals(reviewer.getId().value(), result.reviewedBy());
    assertEquals(tenant.getId().value(), result.assignedTenantId());
    var identity = identities.findById(IdentityId.from(result.resolvedIdentityId())).orElseThrow();
    assertEquals(kind, identity.getKind());
    assertTrue(identity.isPending());
    var affiliation =
        affiliations.findById(AffiliationId.from(result.resultingAffiliationId())).orElseThrow();
    assertEquals(identity.getId(), affiliation.getIdentityId());
    assertThrows(
        br.com.imarui.ima.identity.core.domain.exception.identityapplication
            .IdentityApplicationReviewNotAllowedException.class,
        () -> approve.execute(approval(id, AffiliationType.EXTERNAL, null)));
    assertThrows(
        br.com.imarui.ima.identity.core.domain.exception.identityapplication
            .IdentityApplicationReviewNotAllowedException.class,
        () -> reject.execute(new RejectIdentityApplicationCommand(id, "Rejected")));
  }

  @Test
  void rejectionIsAuditedAndDoesNotProvisionIdentity() {
    var id = request(IdentityKind.SERVICE, "rejected@example.test");
    assertThrows(
        br.com.imarui.ima.identity.core.domain.exception.identityapplication
            .InvalidIdentityApplicationStateException.class,
        () -> reject.execute(new RejectIdentityApplicationCommand(id, "  ")));
    var result = reject.execute(new RejectIdentityApplicationCommand(id, " Insufficient data "));
    assertEquals(reviewer.getId().value(), result.reviewedBy());
    assertEquals("Insufficient data", result.rejectionReason());
    assertNull(result.resolvedIdentityId());
    assertEquals(1, jdbc.queryForObject("select count(*) from ima_identities", Integer.class));
    assertEquals(1, list.execute().size());
    assertEquals(result, get.execute(id));
    assertThrows(
        br.com.imarui.ima.identity.core.domain.exception.identityapplication
            .IdentityApplicationReviewNotAllowedException.class,
        () -> reject.execute(new RejectIdentityApplicationCommand(id, "Again")));
  }

  @Test
  void missingActorCannotResolveOrDelete() {
    var id = request(IdentityKind.SERVICE, "pending@example.test");
    SecurityContextHolder.clearContext();
    assertThrows(
        CurrentActorUnavailableException.class,
        () -> approve.execute(approval(id, AffiliationType.EXTERNAL, null)));
    assertThrows(
        CurrentActorUnavailableException.class,
        () -> reject.execute(new RejectIdentityApplicationCommand(id, "No")));
    assertThrows(
        CurrentActorUnavailableException.class,
        () -> delete.execute(new SelfDeleteIdentityCommand(null)));
    assertTrue(applications.findById(id).orElseThrow().isPending());
  }

  @ParameterizedTest
  @EnumSource(IdentityKind.class)
  void duplicatesAreBlockedBothBeforeAndAfterApplication(IdentityKind kind) {
    var id = request(kind, "duplicate@example.test");
    assertThrows(IdentityConflictException.class, () -> request(kind, "duplicate@example.test"));
    if (kind != IdentityKind.SERVICE)
      assertThrows(
          IdentityConflictException.class, () -> request(kind, "different-email@example.test"));
    var existing = sample(kind, "duplicate@example.test");
    existing.assignNickname(applications.findById(id).orElseThrow().getNickname());
    existing.activate(earlier);
    existing.disable(earlier);
    var identity = identities.save(existing);
    assertThrows(
        IdentityConflictException.class,
        () -> approve.execute(approval(id, AffiliationType.EXTERNAL, null)));
    assertTrue(applications.findById(id).orElseThrow().isPending());
    assertTrue(identities.findById(identity.getId()).isPresent());
  }

  @Test
  void sameEmailIsAllowedAcrossKindsAndRejectionReleasesPendingKey() {
    var service = request(IdentityKind.SERVICE, "shared@example.test");
    var company = request(IdentityKind.LEGAL_ENTITY, "shared@example.test");
    approve.execute(approval(service, AffiliationType.PARTNER, null));
    approve.execute(approval(company, AffiliationType.EXTERNAL, null));
    var rejected = request(IdentityKind.SERVICE, "reusable@example.test");
    reject.execute(new RejectIdentityApplicationCommand(rejected, "No"));
    assertNotNull(request(IdentityKind.SERVICE, "reusable@example.test"));
  }

  @Test
  void disabledTenantBlocksApprovalWithoutPartialIdentity() {
    var id = request(IdentityKind.SERVICE, "tenant@example.test");
    disableTenant.execute(new DisableTenantCommand(tenant.getId()));
    assertThrows(
        TenantUnavailableException.class,
        () -> approve.execute(approval(id, AffiliationType.EXTERNAL, null)));
    assertEquals(1, jdbc.queryForObject("select count(*) from ima_identities", Integer.class));
    assertTrue(applications.findById(id).orElseThrow().isPending());
    assertEquals("TENANT", revocations.findAll().get(0).getScope());
  }

  @Test
  void concurrentDuplicateSubmissionsHaveOneWinner() throws Exception {
    var pool = Executors.newFixedThreadPool(2);
    var start = new CountDownLatch(1);
    Callable<Boolean> submit =
        () -> {
          start.await();
          try {
            request(IdentityKind.SERVICE, "race@example.test");
            return true;
          } catch (IdentityConflictException exception) {
            return false;
          }
        };
    try {
      var first = pool.submit(submit);
      var second = pool.submit(submit);
      start.countDown();
      assertNotEquals(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
      assertEquals(
          1, jdbc.queryForObject("select count(*) from ima_identity_applications", Integer.class));
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void concurrentApprovalCanResolveOnlyOnce() throws Exception {
    var id = request(IdentityKind.SERVICE, "approval-race@example.test");
    var actorId = reviewer.getId();
    var pool = Executors.newFixedThreadPool(2);
    var start = new CountDownLatch(1);
    Callable<Boolean> task =
        () -> {
          start.await();
          actor(actorId);
          try {
            approve.execute(approval(id, AffiliationType.EXTERNAL, null));
            return true;
          } catch (
              br.com.imarui.ima.identity.core.domain.exception.identityapplication
                      .IdentityApplicationReviewNotAllowedException
                  exception) {
            return false;
          } finally {
            SecurityContextHolder.clearContext();
          }
        };
    try {
      var first = pool.submit(task);
      var second = pool.submit(task);
      start.countDown();
      assertNotEquals(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
      assertEquals(2, jdbc.queryForObject("select count(*) from ima_identities", Integer.class));
    } finally {
      pool.shutdownNow();
    }
  }
}
