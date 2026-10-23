package br.com.imarui.ima.identity.core.application.identity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import br.com.imarui.ima.identity.core.application.command.identity.*;
import br.com.imarui.ima.identity.core.application.port.*;
import br.com.imarui.ima.identity.core.application.usecase.identity.*;
import br.com.imarui.ima.identity.core.domain.enums.identity.*;
import br.com.imarui.ima.identity.core.domain.exception.identity.*;
import br.com.imarui.ima.identity.core.domain.model.affiliation.*;
import br.com.imarui.ima.identity.core.domain.model.identity.*;
import br.com.imarui.ima.identity.core.domain.model.identity.service.*;
import br.com.imarui.ima.identity.core.domain.model.tenant.*;
import br.com.imarui.ima.identity.core.repository.IdentityRepository;
import java.time.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class IdentityLifecycleUseCaseTest {
  static final Instant NOW = Instant.parse("2026-10-17T12:00:00Z");

  static ServiceIdentity identity() {
    return ServiceIdentity.create(
        IdentityId.generate(),
        IdentityEmail.create(Email.from("service@example.test"), EmailType.PRIMARY, NOW),
        ServiceName.from("Test service"),
        null,
        NOW,
        "fixture-" + java.util.UUID.randomUUID());
  }

  @Test
  void useCasesLoadLockedPersistAndOnlyDisableRequestsRevocation() {
    var identity = identity();
    var repository = mock(IdentityRepository.class);
    var revocation = mock(AccessRevocation.class);
    var clock = Clock.fixed(NOW, ZoneOffset.UTC);
    when(repository.findByIdForUpdate(identity.getId())).thenReturn(Optional.of(identity));
    when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    var access = mock(IdentityOperationAccess.class);
    new ActivateIdentityUseCase(access, repository, clock, id -> true)
        .execute(new ActivateIdentityCommand(identity.getId()));
    new DisableIdentityUseCase(access, repository, clock, revocation)
        .execute(new DisableIdentityCommand(identity.getId()));
    new ReactivateIdentityUseCase(access, repository, clock)
        .execute(new ReactivateIdentityCommand(identity.getId()));
    verify(revocation).revokeIdentity(identity.getId());
    verifyNoMoreInteractions(revocation);
    verify(repository, times(3)).findByIdForUpdate(identity.getId());
    assertTrue(identity.isActive());
  }
}
