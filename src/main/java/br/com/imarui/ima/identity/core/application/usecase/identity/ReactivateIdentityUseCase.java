package br.com.imarui.ima.identity.core.application.usecase.identity;

import br.com.imarui.ima.identity.core.application.command.identity.ReactivateIdentityCommand;
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
public class ReactivateIdentityUseCase {
    private final br.com.imarui.ima.identity.core.application.port.IdentityOperationAccess access;
    private final IdentityRepository identityRepository;
    private final Clock clock;


    @Transactional
    public IdentityResult execute(ReactivateIdentityCommand command) {
        var identity = identityRepository.findByIdForUpdate(command.identityId())
                .orElseThrow(() -> new IdentityNotFoundException(command.identityId()));
        access.requirePlatform("IDENTITY_MANAGE");
        identity.reactivate(clock.instant());
        var saved = identityRepository.save(identity);

        return IdentityResult.from(saved);
    }
}
