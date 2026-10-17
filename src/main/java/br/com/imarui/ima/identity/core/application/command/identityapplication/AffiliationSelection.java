package br.com.imarui.ima.identity.core.application.command.identityapplication;
import br.com.imarui.ima.identity.core.domain.enums.affiliation.AffiliationType;
import java.util.Objects;

public record AffiliationSelection(AffiliationType type, String registration, String customerCode, String supplierCode) {
    public AffiliationSelection {
        Objects.requireNonNull(type, "Affiliation type is required.");
        if (type != AffiliationType.EMPLOYEE && registration != null
                || type != AffiliationType.CUSTOMER && customerCode != null
                || type != AffiliationType.SUPPLIER && supplierCode != null) {
            throw new IllegalArgumentException("Affiliation fields must match its type.");
        }
        if (type == AffiliationType.EMPLOYEE && (registration == null || registration.isBlank())
                || type == AffiliationType.CUSTOMER && (customerCode == null || customerCode.isBlank())
                || type == AffiliationType.SUPPLIER && (supplierCode == null || supplierCode.isBlank())) {
            throw new IllegalArgumentException("The selected affiliation requires its specific identifier.");
        }
    }
}
