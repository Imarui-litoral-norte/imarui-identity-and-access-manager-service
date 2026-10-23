package br.com.imarui.ima.identity.core.application.usecase.tenant;

import br.com.imarui.ima.identity.core.application.exception.tenant.TenantNotFoundException;
import br.com.imarui.ima.identity.core.application.result.tenant.TenantResult;
import br.com.imarui.ima.identity.core.domain.model.tenant.Tenant;
import br.com.imarui.ima.identity.core.domain.model.tenant.TenantId;
import br.com.imarui.ima.identity.core.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetTenantUseCase {
    private final br.com.imarui.ima.identity.core.application.port.IdentityOperationAccess access;

    private final TenantRepository tenantRepository;

    @Transactional(readOnly = true)
    public TenantResult execute(TenantId tenantId) {
        Tenant tenant = tenantRepository
                .findById(tenantId)
                .orElseThrow(
                        () -> new TenantNotFoundException(tenantId)
                );

        access.requireTenant("TENANT_READ", tenantId.value());
        return TenantResult.from(tenant);
    }
}
