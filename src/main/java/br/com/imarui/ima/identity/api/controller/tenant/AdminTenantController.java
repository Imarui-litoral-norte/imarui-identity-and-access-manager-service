package br.com.imarui.ima.identity.api.controller.tenant;

import br.com.imarui.ima.identity.api.dto.tenant.request.CreateTenantRequest;
import br.com.imarui.ima.identity.api.dto.tenant.request.RenameTenantRequest;
import br.com.imarui.ima.identity.api.dto.tenant.response.TenantResponse;
import br.com.imarui.ima.identity.core.application.command.tenant.*;
import br.com.imarui.ima.identity.core.application.result.tenant.TenantResult;
import br.com.imarui.ima.identity.core.application.usecase.tenant.*;
import br.com.imarui.ima.identity.core.domain.model.tenant.TenantId;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

@org.springframework.security.access.prepost.PreAuthorize("hasAuthority('TENANT_MANAGE')")
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
@io.swagger.v3.oas.annotations.tags.Tag(name = "Identity", description = "Identidades, solicitações e contexto Tenant/Affiliation")
@br.com.imarui.ima.platform.openapi.security.RequiredPermission("TENANT_MANAGE")
@RestController
@RequestMapping("/api/v1/admin/tenants")
@RequiredArgsConstructor
public class AdminTenantController {

    private final RenameTenantUseCase renameTenantUseCase;
    private final ActivateTenantUseCase activateTenantUseCase;
    private final DisableTenantUseCase disableTenantUseCase;
    private final ReactivateTenantUseCase reactivateTenantUseCase;
    private final CreateTenantUseCase createTenantUseCase;

    @PostMapping
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = TenantResponse.class)))
    @io.swagger.v3.oas.annotations.Operation(summary = "Criar tenant pendente", description = "Cria Tenant PENDING com code único, normalizado para maiúsculas, e name aparado. O código deve iniciar por letra e conter letras, números ou underscore, com 3–50 caracteres. Não cria afiliações. Retorna 201 e Location.")
    @br.com.imarui.ima.platform.openapi.group.SwaggerOperationGroup(value = "Tenants · Administração", order = 80)
    public ResponseEntity<TenantResponse> create(
            @Valid @RequestBody CreateTenantRequest request,
            UriComponentsBuilder uriBuilder
    ) {
        CreateTenantCommand command = new CreateTenantCommand(
                request.code(),
                request.name()
        );

        TenantResult result = createTenantUseCase.execute(command);

        return ResponseEntity
                .created(
                        uriBuilder
                                .path("/api/v1/tenants/{tenantId}")
                                .buildAndExpand(result.id())
                                .toUri()
                )
                .body(TenantResponse.from(result));
    }

    @PostMapping("/{tenantId}/activate")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = TenantResponse.class)))
    @io.swagger.v3.oas.annotations.Operation(summary = "Ativar tenant pendente", description = "Executa PENDING → ACTIVE e permite provisionamento de identidades e validade contextual dos vínculos já ativos. Não altera o estado intrínseco de Identity ou Affiliation.")
    @br.com.imarui.ima.platform.openapi.group.SwaggerOperationGroup(value = "Tenants · Administração", order = 80)
    public ResponseEntity<TenantResponse> activate(
            @PathVariable String tenantId
    ) {
        ActivateTenantCommand command = new ActivateTenantCommand(
                TenantId.from(tenantId)
        );

        TenantResult result = activateTenantUseCase.execute(command);

        return ResponseEntity.ok(
                TenantResponse.from(result)
        );
    }

    @PostMapping("/{tenantId}/disable")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = TenantResponse.class)))
    @io.swagger.v3.oas.annotations.Operation(summary = "Desabilitar contexto do tenant", description = "Executa ACTIVE → DISABLED e registra revogação contextual na mesma transação. Vínculos tornam-se indisponíveis sem alterar os estados intrínsecos de Identity/Affiliation. Não exclui o Tenant.")
    @br.com.imarui.ima.platform.openapi.group.SwaggerOperationGroup(value = "Tenants · Administração", order = 80)
    public ResponseEntity<TenantResponse> disable(
            @PathVariable String tenantId
    ) {
        DisableTenantCommand command = new DisableTenantCommand(
                TenantId.from(tenantId)
        );

        TenantResult result = disableTenantUseCase.execute(command);

        return ResponseEntity.ok(
                TenantResponse.from(result)
        );
    }

    @PostMapping("/{tenantId}/reactivate")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = TenantResponse.class)))
    @io.swagger.v3.oas.annotations.Operation(summary = "Reativar tenant", description = "Executa DISABLED → ACTIVE. Apenas vínculos com Identity ACTIVE e Affiliation ACTIVE tornam-se efetivos novamente. Afiliações INACTIVE permanecem inativas e tokens revogados continuam inválidos.")
    @br.com.imarui.ima.platform.openapi.group.SwaggerOperationGroup(value = "Tenants · Administração", order = 80)
    public ResponseEntity<TenantResponse> reactivate(
            @PathVariable String tenantId
    ) {
        ReactivateTenantCommand command = new ReactivateTenantCommand(TenantId.from(tenantId));

        TenantResult result = reactivateTenantUseCase.execute(command);

        return ResponseEntity.ok(
                TenantResponse.from(result)
        );
    }

    @PatchMapping("/{tenantId}/name")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = TenantResponse.class)))
    @io.swagger.v3.oas.annotations.Operation(summary = "Renomear tenant", description = "Altera apenas name de Tenant PENDING ou ACTIVE, aparando o texto e atualizando updatedAt. Não altera code. Tenant DISABLED retorna conflito.")
    @br.com.imarui.ima.platform.openapi.group.SwaggerOperationGroup(value = "Tenants · Administração", order = 80)
    public ResponseEntity<TenantResponse> rename(
            @PathVariable String tenantId,
            @Valid @RequestBody RenameTenantRequest request
    ) {
        RenameTenantCommand command = new RenameTenantCommand(
                TenantId.from(tenantId),
                request.name()
        );

        TenantResult result = renameTenantUseCase.execute(command);

        return ResponseEntity.ok(
                TenantResponse.from(result)
        );
    }
}
