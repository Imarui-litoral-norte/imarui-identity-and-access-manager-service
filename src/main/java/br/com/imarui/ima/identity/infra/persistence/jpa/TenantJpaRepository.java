package br.com.imarui.ima.identity.infra.persistence.jpa;

import br.com.imarui.ima.identity.core.domain.enums.tenant.TenantStatus;
import br.com.imarui.ima.identity.infra.persistence.entity.tenant.TenantEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantJpaRepository
        extends JpaRepository<TenantEntity, UUID>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<TenantEntity> {

    boolean existsByCodeIgnoreCase(String code);

    List<TenantEntity> findAllByStatusOrderByNameAsc(
            TenantStatus status
    );

    Optional<TenantEntity> findByIdAndStatus(
            UUID tenantId,
            TenantStatus status
    );

    Optional<TenantEntity> findByCodeIgnoreCaseAndStatus(
            String code,
            TenantStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select tenant
              from TenantEntity tenant
             where tenant.id = :tenantId
            """)
    Optional<TenantEntity> findByIdForUpdate(
            @Param("tenantId") UUID tenantId
    );
}
