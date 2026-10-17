package br.com.imarui.ima.identity.core.application.usecase.identityapplication;
import br.com.imarui.ima.identity.core.application.command.identityapplication.RejectIdentityApplicationCommand;
import br.com.imarui.ima.identity.core.application.result.identityapplication.IdentityApplicationResult;
import br.com.imarui.ima.identity.core.application.port.CurrentActor;
import br.com.imarui.ima.identity.core.application.exception.IdentityApplicationNotFoundException;
import br.com.imarui.ima.identity.core.repository.IdentityApplicationRepository;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RejectIdentityApplicationUseCase {
    private final IdentityApplicationRepository applications;
    private final CurrentActor currentActor;
    private final Clock clock;
    @Transactional
    public IdentityApplicationResult execute(RejectIdentityApplicationCommand command) {
        var actor = currentActor.requireIdentityId();
        var application = applications.findByIdForUpdate(command.identityApplicationId()).orElseThrow(IdentityApplicationNotFoundException::new);
        application.reject(actor, command.reason(), clock.instant());
        return IdentityApplicationResult.from(applications.save(application));
    }
}
