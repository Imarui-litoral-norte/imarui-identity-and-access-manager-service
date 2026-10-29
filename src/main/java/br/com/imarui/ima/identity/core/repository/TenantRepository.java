package br.com.imarui.ima.identity.core.repository;

import br.com.imarui.ima.identity.core.domain.model.tenant.Tenant;
import br.com.imarui.ima.identity.core.domain.model.tenant.TenantCode;
import br.com.imarui.ima.identity.core.domain.model.tenant.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import br.com.imarui.ima.identity.core.domain.enums.tenant.TenantStatus;

public interface TenantRepository {

    record DirectoryPage(List<Tenant> items, long total, int page, int size) {}

    DirectoryPage search(Set<UUID> readableIds, TenantStatus status, String query, int page, int size);

    Tenant save(Tenant tenant);

    List<Tenant> findAllActive();

    Optional<Tenant> findById(TenantId tenantId);

    Optional<Tenant> findByIdForUpdate(TenantId tenantId);

    Optional<Tenant> findByCode(TenantCode code);

    boolean existsByCode(TenantCode code);
}
