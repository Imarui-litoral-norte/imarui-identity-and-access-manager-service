package br.com.imarui.ima.identity.infra.persistence.mapper;

import br.com.imarui.ima.identity.core.domain.enums.affiliation.AffiliationType;
import br.com.imarui.ima.identity.infra.persistence.entity.affiliation.*;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class AffiliationHistoryMapper {
    public AffiliationHistoryEntity toHistory(AffiliationEntity entity, UUID identityHistoryId,
            UUID actorId, String reason, Instant deletedAt) {
        if (entity instanceof EmployeeAffiliationEntity employee) {
            return snapshot(entity, AffiliationType.EMPLOYEE, employee.getRegistration(), null, null,
                    identityHistoryId, actorId, reason, deletedAt);
        }
        if (entity instanceof CustomerAffiliationEntity customer) {
            return snapshot(entity, AffiliationType.CUSTOMER, null, customer.getCustomerCode(), null,
                    identityHistoryId, actorId, reason, deletedAt);
        }
        if (entity instanceof SupplierAffiliationJpaEntity supplier) {
            return snapshot(entity, AffiliationType.SUPPLIER, null, null, supplier.getSupplierCode(),
                    identityHistoryId, actorId, reason, deletedAt);
        }
        if (entity instanceof PartnerAffiliationEntity) {
            return snapshot(entity, AffiliationType.PARTNER, null, null, null,
                    identityHistoryId, actorId, reason, deletedAt);
        }
        if (entity instanceof ExternalAffiliationEntity) {
            return snapshot(entity, AffiliationType.EXTERNAL, null, null, null,
                    identityHistoryId, actorId, reason, deletedAt);
        }
        throw new IllegalArgumentException("Unsupported affiliation history type: " + entity.getClass().getName());
    }

    private AffiliationHistoryEntity snapshot(AffiliationEntity entity, AffiliationType type,
            String registration, String customerCode, String supplierCode, UUID identityHistoryId,
            UUID actorId, String reason, Instant deletedAt) {
        return new AffiliationHistoryEntity(entity, type, registration, customerCode, supplierCode,
                identityHistoryId, actorId, reason, deletedAt);
    }
}
