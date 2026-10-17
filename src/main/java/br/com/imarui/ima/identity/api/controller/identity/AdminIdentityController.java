package br.com.imarui.ima.identity.api.controller.identity;

import br.com.imarui.ima.identity.api.dto.identity.response.IdentityResponse;
import br.com.imarui.ima.identity.core.application.command.identity.ActivateIdentityCommand;
import br.com.imarui.ima.identity.core.application.command.identity.DisableIdentityCommand;
import br.com.imarui.ima.identity.core.application.command.identity.ReactivateIdentityCommand;
import br.com.imarui.ima.identity.core.application.result.identity.IdentityResult;
import br.com.imarui.ima.identity.core.application.usecase.identity.ActivateIdentityUseCase;
import br.com.imarui.ima.identity.core.application.usecase.identity.DisableIdentityUseCase;
import br.com.imarui.ima.identity.core.application.usecase.identity.ReactivateIdentityUseCase;
import br.com.imarui.ima.identity.core.domain.model.identity.IdentityId;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@org.springframework.security.access.prepost.PreAuthorize("hasAuthority('IDENTITY_MANAGE')")
@br.com.imarui.ima.identity.api.exception.IdentityApiErrors
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping(
        value = "/api/v1/admin/identities",
        produces = MediaType.APPLICATION_JSON_VALUE
)
@RequiredArgsConstructor
public class AdminIdentityController {

    private final ActivateIdentityUseCase activateIdentityUseCase;
    private final DisableIdentityUseCase disableIdentityUseCase;
    private final ReactivateIdentityUseCase reactivateIdentityUseCase;

    @PostMapping("/{identityId}/activate")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = IdentityResponse.class)))
    public ResponseEntity<IdentityResponse> activate(
            @PathVariable String identityId
    ) {
        ActivateIdentityCommand command = new ActivateIdentityCommand(
                IdentityId.from(identityId)
        );
        IdentityResult result = activateIdentityUseCase.execute(command);

        return ResponseEntity.ok(IdentityResponse.from(result));
    }

    @PostMapping("/{identityId}/disable")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = IdentityResponse.class)))
    public ResponseEntity<IdentityResponse> disable(
            @PathVariable String identityId
    ) {
        DisableIdentityCommand command = new DisableIdentityCommand(
                IdentityId.from(identityId)
        );
        IdentityResult result = disableIdentityUseCase.execute(command);

        return ResponseEntity.ok(IdentityResponse.from(result));
    }

    @PostMapping("/{identityId}/reactivate")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = IdentityResponse.class)))
    public ResponseEntity<IdentityResponse> reactivate(
            @PathVariable String identityId
    ) {
        ReactivateIdentityCommand command = new ReactivateIdentityCommand(
                IdentityId.from(identityId)
        );
        IdentityResult result = reactivateIdentityUseCase.execute(command);

        return ResponseEntity.ok(IdentityResponse.from(result));
    }
}
