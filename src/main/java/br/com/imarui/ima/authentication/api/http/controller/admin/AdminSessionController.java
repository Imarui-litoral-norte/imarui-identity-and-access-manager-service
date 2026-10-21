package br.com.imarui.ima.authentication.api.http.controller.admin;

import br.com.imarui.ima.authentication.api.http.dto.admin.session.AdminSessionResponseDTO;
import br.com.imarui.ima.authentication.core.application.result.admin.session.AdminSessionResult;
import br.com.imarui.ima.authentication.core.application.service.session.AdminSessionService;
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
@Tag(name = "Authentication - Sessions", description = "Operações administrativas para consulta e gerenciamento de sessões.")
public class AdminSessionController {

    private final AdminSessionService adminSessionService;

    @GetMapping("/sessions")
    @PreAuthorize("hasAuthority('AUTHENTICATION_SESSION_READ')")
    @RequiredPermission("AUTHENTICATION_SESSION_READ")
    @SwaggerOperationGroup(value = "Rotas administrativas", order = 30)
    @Operation(
            summary = "Listar sessões",
            description = "Retorna as sessões cadastradas no módulo de autenticação.",
            responses = @ApiResponse(
                    responseCode = "200",
                    description = "Sessões retornadas com sucesso.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = AdminSessionResponseDTO.class))
            )
    )
    public ResponseEntity<List<AdminSessionResponseDTO>> findAll(@org.springframework.web.bind.annotation.RequestParam(defaultValue="0") @jakarta.validation.constraints.Min(0) int page, @org.springframework.web.bind.annotation.RequestParam(defaultValue="100") @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(100) int size) {
        List<AdminSessionResult> results = adminSessionService.findPage(page,size);
        return ResponseEntity.ok(AdminSessionResponseDTO.from(results));
    }

    @GetMapping("/sessions/{sessionId}")
    @PreAuthorize("hasAuthority('AUTHENTICATION_SESSION_READ')")
    @RequiredPermission("AUTHENTICATION_SESSION_READ")
    @SwaggerOperationGroup(value = "Rotas administrativas", order = 30)
    @Operation(
            summary = "Consultar sessão",
            description = "Retorna os dados administrativos de uma sessão específica.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Sessão retornada com sucesso.",
                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = AdminSessionResponseDTO.class))
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "Sessão não encontrada.",
                            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))
                    )
            }
    )
    public ResponseEntity<AdminSessionResponseDTO> findById(@PathVariable @Positive Long sessionId) {
        AdminSessionResult result = adminSessionService.findById(sessionId);
        return ResponseEntity.ok(AdminSessionResponseDTO.from(result));
    }

    @PatchMapping("/sessions/{sessionId}/logout")
    @PreAuthorize("hasAuthority('AUTHENTICATION_SESSION_LOGOUT')")
    @RequiredPermission("AUTHENTICATION_SESSION_LOGOUT")
    @SwaggerOperationGroup(value = "Rotas administrativas", order = 30)
    @Operation(
            summary = "Encerrar sessão",
            description = "Encerra administrativamente uma sessão específica.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Sessão encerrada com sucesso.",
                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = AdminSessionResponseDTO.class))
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "Sessão não encontrada.",
                            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))
                    ),
                    @ApiResponse(
                            responseCode = "409",
                            description = "Sessão já está encerrada ou não pode ser encerrada no estado atual.",
                            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))
                    )
            }
    )
    public ResponseEntity<AdminSessionResponseDTO> logout(@PathVariable @Positive Long sessionId) {
        AdminSessionResult result = adminSessionService.logout(sessionId);
        return ResponseEntity.ok(AdminSessionResponseDTO.from(result));
    }
}
