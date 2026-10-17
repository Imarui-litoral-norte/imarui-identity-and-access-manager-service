package br.com.imarui.ima.identity.infra.persistence.adapter;

import br.com.imarui.ima.identity.core.domain.enums.affiliation.AffiliationStatus;
import br.com.imarui.ima.identity.core.domain.model.affiliation.*;
import br.com.imarui.ima.identity.core.domain.model.affiliation.customer.CustomerCode;
import br.com.imarui.ima.identity.core.domain.model.affiliation.employee.EmployeeRegistration;
import br.com.imarui.ima.identity.core.domain.model.affiliation.supplier.SupplierCode;
import br.com.imarui.ima.identity.core.domain.model.identity.IdentityId;
import br.com.imarui.ima.identity.core.domain.model.tenant.TenantId;
import br.com.imarui.ima.identity.core.repository.AffiliationRepository;
import br.com.imarui.ima.identity.infra.persistence.entity.affiliation.AffiliationEntity;
import br.com.imarui.ima.identity.infra.persistence.jpa.AffiliationJpaRepository;
import br.com.imarui.ima.identity.infra.persistence.mapper.AffiliationPersistenceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class AffiliationRepositoryAdapter implements AffiliationRepository {

    private final AffiliationJpaRepository jpaRepository;
    private final AffiliationPersistenceMapper mapper;
    private final br.com.imarui.ima.identity.core.repository.IdentityRepository identities;
    private final br.com.imarui.ima.identity.core.repository.TenantRepository tenants;

    private boolean isEffective(Affiliation affiliation) {
        return br.com.imarui.ima.identity.core.domain.policy.AffiliationValidity.isEffective(
            affiliation, identities.findById(affiliation.getIdentityId()).orElse(null),
            tenants.findById(affiliation.getTenantId()).orElse(null));
    }

    @Override
    public Affiliation save(Affiliation affiliation) {
        AffiliationEntity entity = jpaRepository
                .findById(affiliation.getId().value())
                .map(existing -> {
                    mapper.updateEntity(affiliation, existing);
                    return existing;
                })
                .orElseGet(() -> mapper.toEntity(affiliation));

        return mapper.toDomain(
                jpaRepository.save(entity)
        );
    }

    @Override
    public List<Affiliation> findAllActive() {
        return jpaRepository
                .findAllByStatusOrderByStartedAtDesc(
                        AffiliationStatus.ACTIVE
                )
                .stream()
                .map(mapper::toDomain)
                .filter(this::isEffective)
                .toList();
    }

    @Override
    public Optional<Affiliation> findById(
            AffiliationId affiliationId
    ) {
        return jpaRepository
                .findById(affiliationId.value())
                .map(mapper::toDomain);
    }

    @Override
    public Optional<Affiliation> findByIdForUpdate(
            AffiliationId affiliationId
    ) {
        return jpaRepository
                .findByIdForUpdate(affiliationId.value())
                .map(mapper::toDomain);
    }

    @Override
    public List<Affiliation> findByTenantId(TenantId tenantId) {
        return jpaRepository
                .findByTenantIdAndStatusOrderByStartedAtDesc(
                        tenantId.value(),
                        AffiliationStatus.ACTIVE
                )
                .stream()
                .map(mapper::toDomain)
                .filter(this::isEffective)
                .toList();
    }

    @Override
    public List<Affiliation> findByIdentityId(
            IdentityId identityId
    ) {
        return jpaRepository
                .findByIdentityIdAndStatusOrderByStartedAtDesc(
                        identityId.value(),
                        AffiliationStatus.ACTIVE
                )
                .stream()
                .map(mapper::toDomain)
                .filter(this::isEffective)
                .toList();
    }

    @Override
    public boolean existsActiveByTenantAndIdentity(
            TenantId tenantId,
            IdentityId identityId
    ) {
        return findByTenantId(tenantId).stream()
                .anyMatch(affiliation -> affiliation.getIdentityId().equals(identityId));
    }

    @Override
    public boolean existsEmployeeRegistration(
            TenantId tenantId,
            EmployeeRegistration registration
    ) {
        return jpaRepository.existsEmployeeRegistration(
                tenantId.value(),
                registration.value()
        );
    }

    @Override
    public boolean existsCustomerCode(
            TenantId tenantId,
            CustomerCode customerCode
    ) {
        return jpaRepository.existsCustomerCode(
                tenantId.value(),
                customerCode.value()
        );
    }

    @Override
    public boolean existsSupplierCode(
            TenantId tenantId,
            SupplierCode supplierCode
    ) {
        return jpaRepository.existsSupplierCode(
                tenantId.value(),
                supplierCode.value()
        );
    }
}
