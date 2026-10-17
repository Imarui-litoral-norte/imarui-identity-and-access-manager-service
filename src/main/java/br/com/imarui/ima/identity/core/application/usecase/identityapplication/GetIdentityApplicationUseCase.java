package br.com.imarui.ima.identity.core.application.usecase.identityapplication;
import br.com.imarui.ima.identity.core.application.result.identityapplication.IdentityApplicationResult;
import br.com.imarui.ima.identity.core.repository.IdentityApplicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
@RequiredArgsConstructor
public class GetIdentityApplicationUseCase {
    private final IdentityApplicationRepository applications;
    @Transactional(readOnly = true)
    public IdentityApplicationResult execute(br.com.imarui.ima.identity.core.domain.model.identityapplication.IdentityApplicationId id) { return IdentityApplicationResult.from(applications.findById(id).orElseThrow(br.com.imarui.ima.identity.core.application.exception.IdentityApplicationNotFoundException::new)); }
}
