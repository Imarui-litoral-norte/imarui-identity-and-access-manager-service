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
@br.com.imarui.ima.identity.api.exception.IdentityApiErrors
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
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
