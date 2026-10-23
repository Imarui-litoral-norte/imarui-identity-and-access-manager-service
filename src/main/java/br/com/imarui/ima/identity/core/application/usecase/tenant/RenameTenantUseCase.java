package br.com.imarui.ima.identity.core.application.usecase.tenant;

import br.com.imarui.ima.identity.core.application.command.tenant.RenameTenantCommand;
import br.com.imarui.ima.identity.core.application.exception.tenant.TenantNotFoundException;
import br.com.imarui.ima.identity.core.application.result.tenant.TenantResult;
import br.com.imarui.ima.identity.core.domain.model.tenant.Tenant;
import br.com.imarui.ima.identity.core.domain.model.tenant.TenantId;
import br.com.imarui.ima.identity.core.domain.model.tenant.TenantName;
import br.com.imarui.ima.identity.core.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;


@Service
@RequiredArgsConstructor
public class RenameTenantUseCase {
    private final br.com.imarui.ima.identity.core.application.port.IdentityOperationAccess access;

    private final TenantRepository tenantRepository;
    private final Clock clock;

    @Transactional
    public TenantResult execute(RenameTenantCommand command) {
        TenantName name = TenantName.from(command.name());
        TenantId id = command.tenantId();

        Tenant tenant = tenantRepository
                .findByIdForUpdate(id)
                .orElseThrow(
                        () -> new TenantNotFoundException(id)
                );

        access.requireTenant("TENANT_MANAGE", id.value());
        tenant.rename(name, clock.instant());

        return TenantResult.from(tenantRepository.save(tenant));
    }

}
