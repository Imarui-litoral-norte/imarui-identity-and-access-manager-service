package br.com.imarui.ima.identity.api.controller.identity;

import br.com.imarui.ima.identity.core.application.command.identity.SelfDeleteIdentityCommand;
import br.com.imarui.ima.identity.core.application.usecase.identity.SelfDeleteIdentityUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
@io.swagger.v3.oas.annotations.tags.Tag(name = "Identity", description = "Identidades, solicitações e contexto Tenant/Affiliation")
@RestController
@RequestMapping("/api/v1/identities/me")
@RequiredArgsConstructor
public class SelfIdentityController {
    private final SelfDeleteIdentityUseCase deletion;
    public record DeleteRequest(@Size(min = 1, max = 500) String reason) {}

    @DeleteMapping
    @ApiResponse(responseCode = "204", description = "Identity deleted")
    @io.swagger.v3.oas.annotations.Operation(summary = "Excluir definitivamente a própria identidade", description = "Resolve o alvo exclusivamente pelo subject autenticado de uma Identity ACTIVE. Grava snapshot histórico e revogação e remove a identidade e suas afiliações operacionais na mesma transação. O motivo é opcional; quando informado, é aparado e exige 1–500 caracteres não brancos. Falha em qualquer etapa desfaz todos os efeitos. Sucesso retorna 204 sem corpo.")
    @br.com.imarui.ima.platform.openapi.group.SwaggerOperationGroup(value = "Identidades · Exclusão própria", order = 30)
    public ResponseEntity<Void> delete(@Valid @RequestBody(required = false) DeleteRequest request) {
        deletion.execute(new SelfDeleteIdentityCommand(request == null ? null : request.reason()));
        return ResponseEntity.noContent().build();
    }
}
