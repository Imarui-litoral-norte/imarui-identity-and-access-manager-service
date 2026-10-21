package br.com.imarui.ima.identity.core.application.usecase.identity;

import br.com.imarui.ima.identity.core.application.command.identity.ActivateIdentityCommand;
import br.com.imarui.ima.identity.core.application.exception.identity.IdentityNotFoundException;
import br.com.imarui.ima.identity.core.application.result.identity.IdentityResult;
import br.com.imarui.ima.identity.core.application.port.AccessRevocation;
import br.com.imarui.ima.identity.core.repository.IdentityRepository;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ActivateIdentityUseCase {
    private final IdentityRepository identityRepository;
    private final Clock clock;
    private final br.com.imarui.ima.identity.core.application.port.InitialAccessProof initialAccess;


    @Transactional
    public IdentityResult execute(ActivateIdentityCommand command) {
        var identity = identityRepository.findByIdForUpdate(command.identityId())
                .orElseThrow(() -> new IdentityNotFoundException(command.identityId()));
        if (!initialAccess.established(identity.getId().value())) throw new br.com.imarui.ima.identity.core.application.exception.IdentityConflictException();
        identity.activate(clock.instant());
        var saved = identityRepository.save(identity);

        return IdentityResult.from(saved);
    }
}
