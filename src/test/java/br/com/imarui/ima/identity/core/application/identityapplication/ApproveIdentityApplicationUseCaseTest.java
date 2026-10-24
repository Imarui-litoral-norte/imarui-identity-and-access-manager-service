package br.com.imarui.ima.identity.core.application.identityapplication;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import br.com.imarui.ima.identity.core.application.command.identityapplication.*;
import br.com.imarui.ima.identity.core.application.exception.*;
import br.com.imarui.ima.identity.core.application.port.*;
import br.com.imarui.ima.identity.core.application.service.IdentityApplicationConflicts;
import br.com.imarui.ima.identity.core.application.usecase.identityapplication.*;
import br.com.imarui.ima.identity.core.domain.enums.affiliation.*;
import br.com.imarui.ima.identity.core.domain.enums.identity.*;
import br.com.imarui.ima.identity.core.domain.model.identity.*;
import br.com.imarui.ima.identity.core.domain.model.tenant.*;
import br.com.imarui.ima.identity.core.repository.*;
import br.com.imarui.ima.identity.support.IdentitySamples;
import java.time.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ApproveIdentityApplicationUseCaseTest {
  final IdentityApplicationRepository applications = mock(IdentityApplicationRepository.class);
  final IdentityRepository identities = mock(IdentityRepository.class);
  final TenantRepository tenants = mock(TenantRepository.class);
  final AffiliationRepository affiliations = mock(AffiliationRepository.class);
  final CurrentActor actor = mock(CurrentActor.class);
  final IdentityRegistrationLock lock = mock(IdentityRegistrationLock.class);
  final IdentityApplicationConflicts conflicts =
      new IdentityApplicationConflicts(identities, applications);
  final Instant now = IdentitySamples.NOW;
  final ApproveIdentityApplicationUseCase useCase =
      new ApproveIdentityApplicationUseCase(
          applications,
          identities,
          tenants,
          affiliations,
          actor,
          lock,
          conflicts,
          Clock.fixed(now, ZoneOffset.UTC));

  @ParameterizedTest
  @EnumSource(IdentityKind.class)
  void approvalCreatesCorrectKindAndAuditsTrustedActorUsingOnlyPorts(IdentityKind kind) {
    var application = IdentitySamples.application(kind);
    var reviewer = IdentityId.generate();
    var tenant =
        Tenant.create(TenantId.generate(), TenantCode.from("CODE"), TenantName.from("Tenant"), now);
    tenant.activate(now);
    when(actor.requireIdentityId()).thenReturn(reviewer);
    when(applications.findByIdForUpdate(application.getId())).thenReturn(Optional.of(application));
    when(tenants.findByIdForUpdate(tenant.getId())).thenReturn(Optional.of(tenant));
    when(identities.save(any())).thenAnswer(i -> i.getArgument(0));
    when(affiliations.save(any())).thenAnswer(i -> i.getArgument(0));
    when(applications.save(any())).thenAnswer(i -> i.getArgument(0));
    var result =
        useCase.execute(
            new ApproveIdentityApplicationCommand(
                application.getId(),
                tenant.getId(),
                new AffiliationSelection(AffiliationType.EXTERNAL, null, null, null)));
    assertEquals(kind, result.kind());
    assertEquals(reviewer.value(), result.reviewedBy());
    assertNotNull(result.resultingAffiliationId());
    verify(lock).acquire(application);
    verify(identities).save(argThat(identity -> identity.getKind() == kind));
    verify(affiliations)
        .save(
            argThat(
                affiliation ->
                    affiliation.getIdentityId().value().equals(result.resolvedIdentityId())));
  }

  @Test
  void actorFailureStopsApprovalBeforeAnyWrite() {
    when(actor.requireIdentityId()).thenThrow(new CurrentActorUnavailableException());
    assertThrows(
        CurrentActorUnavailableException.class,
        () ->
            useCase.execute(
                new ApproveIdentityApplicationCommand(
                    br.com.imarui.ima.identity.core.domain.model.identityapplication
                        .IdentityApplicationId.generate(),
                    TenantId.generate(),
                    new AffiliationSelection(AffiliationType.PARTNER, null, null, null))));
    verifyNoInteractions(applications, identities, tenants, affiliations, lock);
  }

  @Test
  void approvalRechecksConflictsBeforeProvisioning() {
    var application = IdentitySamples.application(IdentityKind.SERVICE);
    when(actor.requireIdentityId()).thenReturn(IdentityId.generate());
    when(applications.findByIdForUpdate(application.getId())).thenReturn(Optional.of(application));
    when(identities.existsNickname(application.getNickname()))
        .thenReturn(true);
    assertThrows(
        IdentityConflictException.class,
        () ->
            useCase.execute(
                new ApproveIdentityApplicationCommand(
                    application.getId(),
                    TenantId.generate(),
                    new AffiliationSelection(AffiliationType.EXTERNAL, null, null, null))));
    verify(identities, never()).save(any());
    verifyNoInteractions(affiliations, tenants);
  }
}
