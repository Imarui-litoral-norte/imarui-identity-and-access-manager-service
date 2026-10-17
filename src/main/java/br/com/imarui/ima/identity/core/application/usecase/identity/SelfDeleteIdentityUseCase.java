package br.com.imarui.ima.identity.core.application.usecase.identity;

import br.com.imarui.ima.identity.core.application.command.identity.SelfDeleteIdentityCommand;
import br.com.imarui.ima.identity.core.application.port.*;
import br.com.imarui.ima.identity.core.application.exception.identity.IdentityNotFoundException;
import br.com.imarui.ima.identity.core.repository.IdentityRepository;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SelfDeleteIdentityUseCase {
    private final IdentityRepository identities;
    private final CurrentActor currentActor;
    private final IdentityDeletion deletion;
    private final AccessRevocation revocation;
    private final Clock clock;

    @Transactional
    public void execute(SelfDeleteIdentityCommand command) {
        var actor = currentActor.requireIdentityId();
        var identity = identities.findByIdForUpdate(actor)
                .orElseThrow(() -> new IdentityNotFoundException(actor));
        String reason = command.reason() == null ? null : command.reason().trim();
        if (reason != null && (reason.isBlank() || reason.length() > 500)) {
            throw new IllegalArgumentException("Deletion reason must contain 1 to 500 characters.");
        }
        var now = clock.instant();
        if (now.isBefore(identity.getUpdatedAt())) {
            throw new IllegalArgumentException("Deletion time precedes last update.");
        }
        revocation.revokeIdentity(actor);
        deletion.archiveAndDelete(identity, actor, reason, now);
    }
}
