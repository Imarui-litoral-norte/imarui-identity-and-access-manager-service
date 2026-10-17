package br.com.imarui.ima.identity.api.controller.tenant;

import br.com.imarui.ima.identity.api.dto.tenant.response.TenantResponse;
import br.com.imarui.ima.identity.core.application.result.tenant.TenantResult;
import br.com.imarui.ima.identity.core.application.usecase.tenant.GetTenantUseCase;
import br.com.imarui.ima.identity.core.application.usecase.tenant.RenameTenantUseCase;
import br.com.imarui.ima.identity.core.domain.model.tenant.TenantId;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@org.springframework.security.access.prepost.PreAuthorize("hasAuthority('TENANT_READ')")
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
@io.swagger.v3.oas.annotations.tags.Tag(name = "Identity", description = "Identidades, solicitações e contexto Tenant/Affiliation")
@br.com.imarui.ima.platform.openapi.security.RequiredPermission("TENANT_READ")
@RestController
@RequestMapping("/api/v1/tenants")
@RequiredArgsConstructor
public class TenantController {

    private final GetTenantUseCase getTenantUseCase;
    private final RenameTenantUseCase renameTenantUseCase;

    @GetMapping("/{tenantId}")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = TenantResponse.class)))
    @io.swagger.v3.oas.annotations.Operation(summary = "Consultar tenant", description = "Consulta Tenant por UUID em qualquer estado administrativo. code é estável; name pode ser alterado no fluxo administrativo. A resposta não inclui nem altera afiliações. Exige TENANT_READ.")
    @br.com.imarui.ima.platform.openapi.group.SwaggerOperationGroup(value = "Tenants · Consulta", order = 70)
    public ResponseEntity<TenantResponse> findById(
            @PathVariable String tenantId
    ) {
        TenantId id = TenantId.from(tenantId);

        TenantResult result = getTenantUseCase.execute(id);

        return ResponseEntity.ok(
                TenantResponse.from(result)
        );
    }


}