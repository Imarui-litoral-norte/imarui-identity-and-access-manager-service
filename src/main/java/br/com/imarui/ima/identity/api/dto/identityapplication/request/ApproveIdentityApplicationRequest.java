package br.com.imarui.ima.identity.api.dto.identityapplication.request;
import br.com.imarui.ima.identity.core.application.command.identityapplication.AffiliationSelection;
import br.com.imarui.ima.identity.core.domain.enums.affiliation.AffiliationType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Aprovação transacional: Tenant ACTIVE e seleção obrigatória do tipo de Affiliation.")
public record ApproveIdentityApplicationRequest(@NotNull UUID tenantId, @NotNull @Valid AffiliationRequest affiliation) {
    @Schema(description = "Tipo do vínculo. EMPLOYEE exige registration, CUSTOMER exige customerCode e SUPPLIER exige supplierCode; PARTNER/EXTERNAL não recebem esses campos. Campos de outro tipo são inválidos.")
    public record AffiliationRequest(@NotNull AffiliationType type,
        @Schema(description = "Matrícula obrigatória somente para EMPLOYEE, única por Tenant.") @Size(max = 255) String registration,
        @Schema(description = "Código obrigatório somente para CUSTOMER, único por Tenant.") @Size(max = 255) String customerCode,
        @Schema(description = "Código obrigatório somente para SUPPLIER, único por Tenant.") @Size(max = 255) String supplierCode) {
        public AffiliationSelection toSelection() { return new AffiliationSelection(type, registration, customerCode, supplierCode); }
    }
}
