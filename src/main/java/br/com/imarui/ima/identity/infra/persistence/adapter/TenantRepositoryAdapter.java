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
import java.util.Set;
import java.util.UUID;
import java.util.Locale;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@Repository
@RequiredArgsConstructor
public class TenantRepositoryAdapter implements TenantRepository {

    private final TenantJpaRepository jpaRepository;
    private final TenantPersistenceMapper mapper;

    @Override
    public DirectoryPage search(Set<UUID> readableIds, TenantStatus status, String query, int page, int size) {
        if (readableIds.isEmpty()) return new DirectoryPage(List.of(), 0, page, size);
        String pattern = "%" + query.toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        var result = jpaRepository.findAll((root, criteria, cb) -> {
            var restrictions = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            restrictions.add(root.get("id").in(readableIds));
            if (status != null) restrictions.add(cb.equal(root.get("status"), status));
            if (!query.isEmpty()) restrictions.add(cb.or(
                    cb.like(cb.lower(root.get("name")), pattern, '\\'),
                    cb.like(cb.lower(root.get("code")), pattern, '\\')));
            return cb.and(restrictions.toArray(jakarta.persistence.criteria.Predicate[]::new));
        }, PageRequest.of(page, size, Sort.by(Sort.Order.asc("name").ignoreCase(), Sort.Order.asc("id"))));
        return new DirectoryPage(result.getContent().stream().map(mapper::toDomain).toList(), result.getTotalElements(), page, size);
    }

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
