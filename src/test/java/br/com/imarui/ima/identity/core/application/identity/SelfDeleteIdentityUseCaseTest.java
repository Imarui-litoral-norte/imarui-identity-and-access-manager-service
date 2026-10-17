package br.com.imarui.ima.identity.core.application.identity;

import br.com.imarui.ima.identity.core.application.command.identity.*;
import br.com.imarui.ima.identity.core.application.usecase.identity.*;
import br.com.imarui.ima.identity.core.application.port.*;
import br.com.imarui.ima.identity.core.domain.model.identity.*;
import br.com.imarui.ima.identity.core.domain.model.identity.service.*;
import br.com.imarui.ima.identity.core.domain.model.tenant.*;
import br.com.imarui.ima.identity.core.domain.model.affiliation.*;
import br.com.imarui.ima.identity.core.domain.policy.AffiliationValidity;
import br.com.imarui.ima.identity.core.domain.enums.identity.*;
import br.com.imarui.ima.identity.core.domain.exception.identity.*;
import br.com.imarui.ima.identity.core.repository.IdentityRepository;
import java.time.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SelfDeleteIdentityUseCaseTest {
    static final Instant NOW = Instant.parse("2026-10-17T12:00:00Z");
    static ServiceIdentity identity() {
        return ServiceIdentity.create(IdentityId.generate(), IdentityEmail.create(Email.from("service@example.test"), EmailType.PRIMARY, NOW), ServiceName.from("Test service"), null, NOW);
    }

    @Test void selfDeleteTakesItsTargetFromTrustedActorAndRejectsInvalidReasonBeforeWriting() {
        var identity = identity(); var repository = mock(IdentityRepository.class);
        var actor = mock(CurrentActor.class); var deletion = mock(IdentityDeletion.class); var revocation = mock(AccessRevocation.class);
        when(actor.requireIdentityId()).thenReturn(identity.getId());
        when(repository.findByIdForUpdate(identity.getId())).thenReturn(Optional.of(identity));
        var useCase = new SelfDeleteIdentityUseCase(repository, actor, deletion, revocation, Clock.fixed(NOW, ZoneOffset.UTC));
        assertThrows(IllegalArgumentException.class, () -> useCase.execute(new SelfDeleteIdentityCommand(" ")));
        verifyNoInteractions(deletion, revocation);
        useCase.execute(new SelfDeleteIdentityCommand(" request "));
        verify(deletion).archiveAndDelete(identity, identity.getId(), "request", NOW);
        verify(revocation).revokeIdentity(identity.getId());
    }
}
