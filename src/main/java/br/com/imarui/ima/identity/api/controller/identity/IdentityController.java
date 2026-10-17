package br.com.imarui.ima.identity.api.controller.identity;

import br.com.imarui.ima.identity.api.dto.identity.response.IdentityResponse;
import br.com.imarui.ima.identity.core.application.command.identity.GetIdentityUseCaseCommand;
import br.com.imarui.ima.identity.core.application.result.identity.IdentityResult;
import br.com.imarui.ima.identity.core.application.usecase.identity.GetIdentityUseCase;
import br.com.imarui.ima.identity.core.domain.model.identity.IdentityId;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@br.com.imarui.ima.identity.api.exception.IdentityApiErrors
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping(
        value = "/api/v1/identities",
        produces = MediaType.APPLICATION_JSON_VALUE
)
@RequiredArgsConstructor
public class IdentityController {

    private final GetIdentityUseCase getIdentityUseCase;

    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('IDENTITY_READ') or authentication.name == #identityId")
    @GetMapping("/{identityId}")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = IdentityResponse.class)))
    public ResponseEntity<IdentityResponse> findById(
            @PathVariable String identityId
    ) {
        GetIdentityUseCaseCommand command =
                new GetIdentityUseCaseCommand(
                        IdentityId.from(identityId)
                );

        IdentityResult result = getIdentityUseCase.execute(command);

        return ResponseEntity.ok(
                IdentityResponse.from(result)
        );
    }
}
