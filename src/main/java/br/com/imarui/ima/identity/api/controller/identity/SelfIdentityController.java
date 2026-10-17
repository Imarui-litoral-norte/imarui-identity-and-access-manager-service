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

@br.com.imarui.ima.identity.api.exception.IdentityApiErrors
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/identities/me")
@RequiredArgsConstructor
public class SelfIdentityController {
    private final SelfDeleteIdentityUseCase deletion;
    public record DeleteRequest(@Size(min = 1, max = 500) String reason) {}

    @DeleteMapping
    @Operation(summary = "Delete the authenticated Identity and preserve its historical snapshot")
    @ApiResponse(responseCode = "204", description = "Identity deleted")
    public ResponseEntity<Void> delete(@Valid @RequestBody(required = false) DeleteRequest request) {
        deletion.execute(new SelfDeleteIdentityCommand(request == null ? null : request.reason()));
        return ResponseEntity.noContent().build();
    }
}
