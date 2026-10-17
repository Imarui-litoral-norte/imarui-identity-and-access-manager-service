package br.com.imarui.ima.identity.core.application.usecase.identity;

import br.com.imarui.ima.identity.core.application.command.identity.DisableIdentityCommand;
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
public class DisableIdentityUseCase {
    private final IdentityRepository identityRepository;
    private final Clock clock;
    private final AccessRevocation accessRevocation;

    @Transactional
    public IdentityResult execute(DisableIdentityCommand command) {
        var identity = identityRepository.findByIdForUpdate(command.identityId())
                .orElseThrow(() -> new IdentityNotFoundException(command.identityId()));
        identity.disable(clock.instant());
        var saved = identityRepository.save(identity);
        accessRevocation.revokeIdentity(identity.getId());
        return IdentityResult.from(saved);
    }
}
