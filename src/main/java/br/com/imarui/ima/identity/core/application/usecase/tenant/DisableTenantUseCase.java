package br.com.imarui.ima.identity.core.application.usecase.tenant;

import br.com.imarui.ima.identity.core.application.command.tenant.DisableTenantCommand;
import br.com.imarui.ima.identity.core.application.exception.tenant.TenantNotFoundException;
import br.com.imarui.ima.identity.core.application.result.tenant.TenantResult;
import br.com.imarui.ima.identity.core.domain.model.tenant.Tenant;
import br.com.imarui.ima.identity.core.domain.model.tenant.TenantId;
import br.com.imarui.ima.identity.core.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
@RequiredArgsConstructor
public class DisableTenantUseCase {
    private final br.com.imarui.ima.identity.core.application.port.IdentityOperationAccess access;

    private final TenantRepository tenantRepository;
    private final Clock clock;
    private final br.com.imarui.ima.identity.core.application.port.AccessRevocation accessRevocation;

    @Transactional
    public TenantResult execute(DisableTenantCommand command) {
        TenantId tenantId = command.tenantId();

        Tenant tenant = tenantRepository
                .findByIdForUpdate(tenantId)
                .orElseThrow(
                        () -> new TenantNotFoundException(
                                tenantId
                        )
                );

        access.requireTenant("TENANT_MANAGE", tenantId.value());
        tenant.disable(clock.instant());

        Tenant savedTenant = tenantRepository.save(tenant);

        accessRevocation.revokeTenant(tenantId);
        return TenantResult.from(savedTenant);
    }
}