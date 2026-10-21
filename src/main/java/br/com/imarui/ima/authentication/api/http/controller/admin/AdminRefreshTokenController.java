package br.com.imarui.ima.authentication.api.http.controller.admin;

import br.com.imarui.ima.authentication.api.http.dto.admin.refresh.AdminRefreshTokenResponseDTO;
import br.com.imarui.ima.authentication.core.application.result.admin.refresh.AdminRefreshTokenResult;
import br.com.imarui.ima.authentication.core.application.service.refreshtoken.AdminRefreshTokenService;
import br.com.imarui.ima.platform.openapi.group.SwaggerOperationGroup;
import br.com.imarui.ima.platform.openapi.security.RequiredPermission;
import org.springframework.http.ProblemDetail;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(value = "/authentication/admin", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Validated
@Tag(name = "Authentication - Refresh Tokens", description = "Operações administrativas para consulta e gerenciamento de refresh tokens.")
public class AdminRefreshTokenController {

    private final AdminRefreshTokenService adminRefreshTokenService;

    @GetMapping("/refresh-tokens")
    @PreAuthorize("hasAuthority('AUTHENTICATION_REFRESH_TOKEN_READ')")
    @RequiredPermission("AUTHENTICATION_REFRESH_TOKEN_READ")
    @SwaggerOperationGroup(value = "Rotas administrativas", order = 30)
    @Operation(
            summary = "Listar refresh tokens",
            description = "Retorna metadados administrativos de refresh tokens cadastrados.",
            responses = @ApiResponse(
                    responseCode = "200",
                    description = "Refresh tokens retornados com sucesso.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = AdminRefreshTokenResponseDTO.class))
            )
    )
    public ResponseEntity<List<AdminRefreshTokenResponseDTO>> findAll(@org.springframework.web.bind.annotation.RequestParam(defaultValue="0") @jakarta.validation.constraints.Min(0) int page, @org.springframework.web.bind.annotation.RequestParam(defaultValue="100") @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(100) int size) {
        List<AdminRefreshTokenResult> results = adminRefreshTokenService.findPage(page,size);
        return ResponseEntity.ok(AdminRefreshTokenResponseDTO.from(results));
    }

    @GetMapping("/refresh-tokens/{refreshTokenId}")
    @PreAuthorize("hasAuthority('AUTHENTICATION_REFRESH_TOKEN_READ')")
    @RequiredPermission("AUTHENTICATION_REFRESH_TOKEN_READ")
    @SwaggerOperationGroup(value = "Rotas administrativas", order = 30)
    @Operation(
            summary = "Consultar refresh token",
            description = "Retorna metadados administrativos de um refresh token específico.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Refresh token retornado com sucesso.",
                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = AdminRefreshTokenResponseDTO.class))
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "Refresh token não encontrado.",
                            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))
                    )
            }
    )
    public ResponseEntity<AdminRefreshTokenResponseDTO> findById(@PathVariable @Positive Long refreshTokenId) {
        AdminRefreshTokenResult result = adminRefreshTokenService.findById(refreshTokenId);
        return ResponseEntity.ok(AdminRefreshTokenResponseDTO.from(result));
    }

    @PatchMapping("/refresh-tokens/{refreshTokenId}/revoke")
    @PreAuthorize("hasAuthority('AUTHENTICATION_REFRESH_TOKEN_REVOKE')")
    @RequiredPermission("AUTHENTICATION_REFRESH_TOKEN_REVOKE")
    @SwaggerOperationGroup(value = "Rotas administrativas", order = 30)
    @Operation(
            summary = "Revogar refresh token",
            description = "Revoga administrativamente um refresh token específico.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Refresh token revogado com sucesso.",
                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = AdminRefreshTokenResponseDTO.class))
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "Refresh token não encontrado.",
                            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))
                    ),
                    @ApiResponse(
                            responseCode = "409",
                            description = "Refresh token já está revogado ou não pode ser revogado no estado atual.",
                            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))
                    )
            }
    )
    public ResponseEntity<AdminRefreshTokenResponseDTO> revoke(@PathVariable @Positive Long refreshTokenId) {
        AdminRefreshTokenResult result = adminRefreshTokenService.revoke(refreshTokenId);
        return ResponseEntity.ok(AdminRefreshTokenResponseDTO.from(result));
    }
}
