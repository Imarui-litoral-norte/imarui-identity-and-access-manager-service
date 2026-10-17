package br.com.imarui.ima.identity.api.dto.identityapplication.request;
import br.com.imarui.ima.identity.core.application.command.identityapplication.AffiliationSelection;
import br.com.imarui.ima.identity.core.domain.enums.affiliation.AffiliationType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record ApproveIdentityApplicationRequest(@NotNull UUID tenantId, @NotNull @Valid AffiliationRequest affiliation) {
    public record AffiliationRequest(@NotNull AffiliationType type,
        @Schema(description = "Required only for EMPLOYEE") @Size(max = 255) String registration,
        @Schema(description = "Required only for CUSTOMER") @Size(max = 255) String customerCode,
        @Schema(description = "Required only for SUPPLIER") @Size(max = 255) String supplierCode) {
        public AffiliationSelection toSelection() { return new AffiliationSelection(type, registration, customerCode, supplierCode); }
    }
}
