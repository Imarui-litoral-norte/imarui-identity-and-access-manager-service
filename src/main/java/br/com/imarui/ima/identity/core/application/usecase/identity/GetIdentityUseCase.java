package br.com.imarui.ima.identity.core.application.usecase.identity;

import br.com.imarui.ima.identity.core.application.command.identity.GetIdentityUseCaseCommand;
import br.com.imarui.ima.identity.core.application.exception.identity.IdentityNotFoundException;
import br.com.imarui.ima.identity.core.application.result.identity.IdentityResult;
import br.com.imarui.ima.identity.core.domain.model.identity.Identity;
import br.com.imarui.ima.identity.core.domain.model.identity.IdentityId;
import br.com.imarui.ima.identity.core.repository.IdentityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
@RequiredArgsConstructor
public class GetIdentityUseCase {
    private final br.com.imarui.ima.identity.core.application.port.IdentityOperationAccess access;

    private final IdentityRepository identityRepository;

    @Transactional(readOnly = true)
    public IdentityResult execute(GetIdentityUseCaseCommand command) {
        IdentityId identityId = command.identityId();
        access.requireSelfOrPlatform("IDENTITY_READ", identityId.value());

        Identity identity = identityRepository.findById(identityId)
                .orElseThrow(() -> new IdentityNotFoundException(identityId));

        return IdentityResult.from(identity);
    }
}
