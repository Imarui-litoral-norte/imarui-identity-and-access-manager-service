package br.com.imarui.ima.identity.infra.persistence.adapter;

import br.com.imarui.ima.identity.core.domain.enums.tenant.TenantStatus;
import br.com.imarui.ima.identity.core.domain.model.tenant.*;
import br.com.imarui.ima.identity.core.repository.TenantRepository;
import br.com.imarui.ima.identity.infra.persistence.entity.tenant.TenantEntity;
import br.com.imarui.ima.identity.infra.persistence.jpa.TenantJpaRepository;
import br.com.imarui.ima.identity.infra.persistence.mapper.TenantPersistenceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class TenantRepositoryAdapter implements TenantRepository {

    private final TenantJpaRepository jpaRepository;
    private final TenantPersistenceMapper mapper;

    @Override
    public Tenant save(Tenant tenant) {
        TenantEntity entity = jpaRepository
                .findById(tenant.getId().value())
                .map(existing -> {
                    mapper.updateEntity(tenant, existing);
                    return existing;
                })
                .orElseGet(() -> mapper.toEntity(tenant));

        return mapper.toDomain(jpaRepository.save(entity));
    }

    @Override
    public List<Tenant> findAllActive() {
        return jpaRepository
                .findAllByStatusOrderByNameAsc(TenantStatus.ACTIVE)
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public Optional<Tenant> findById(TenantId tenantId) {
        return jpaRepository
                .findById(tenantId.value())
                .map(mapper::toDomain);
    }

    @Override
    public Optional<Tenant> findByIdForUpdate(TenantId tenantId) {
        return jpaRepository
                .findByIdForUpdate(tenantId.value())
                .map(mapper::toDomain);
    }

    @Override
    public Optional<Tenant> findByCode(TenantCode code) {
        return jpaRepository
                .findByCodeIgnoreCaseAndStatus(
                        code.value(),
                        TenantStatus.ACTIVE
                )
                .map(mapper::toDomain);
    }

    @Override
    public boolean existsByCode(TenantCode code) {
        return jpaRepository.existsByCodeIgnoreCase(
                code.value()
        );
    }
}
