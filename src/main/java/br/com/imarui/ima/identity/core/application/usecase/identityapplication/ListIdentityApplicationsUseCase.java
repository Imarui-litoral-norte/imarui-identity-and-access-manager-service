package br.com.imarui.ima.identity.core.application.usecase.identityapplication;
import br.com.imarui.ima.identity.core.application.result.identityapplication.IdentityApplicationResult;
import br.com.imarui.ima.identity.core.repository.IdentityApplicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
@RequiredArgsConstructor
public class ListIdentityApplicationsUseCase {
    private final br.com.imarui.ima.identity.core.application.port.IdentityOperationAccess access;
    private final IdentityApplicationRepository applications;
    @Transactional(readOnly = true)
    public java.util.List<IdentityApplicationResult> execute() { access.requirePlatform("IDENTITY_APPLICATION_REVIEW"); return applications.findAll().stream().map(IdentityApplicationResult::from).toList(); }
}
