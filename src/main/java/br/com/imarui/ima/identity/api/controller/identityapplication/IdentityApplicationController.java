package br.com.imarui.ima.identity.api.controller.identityapplication;

import br.com.imarui.ima.identity.api.dto.identityapplication.request.create.CreateIdentityApplicationRequest;
import br.com.imarui.ima.identity.api.dto.identityapplication.response.IdentityApplicationResponse;
import br.com.imarui.ima.identity.api.factory.IdentityApplicationCommandFactory;
import br.com.imarui.ima.identity.core.application.command.identityapplication.CreateIdentityApplicationCommand;
import br.com.imarui.ima.identity.core.application.result.identityapplication.IdentityApplicationResult;
import br.com.imarui.ima.identity.core.application.usecase.identityapplication.CreateIdentityApplicationUseCase;
import br.com.imarui.ima.identity.core.application.usecase.identityapplication.GetIdentityApplicationUseCase;
import br.com.imarui.ima.identity.core.domain.model.identityapplication.IdentityApplicationId;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@br.com.imarui.ima.identity.api.exception.IdentityApiErrors
@RestController
@RequestMapping(
        value = "/api/v1/identity-applications",
        produces = MediaType.APPLICATION_JSON_VALUE
)
@RequiredArgsConstructor
@Validated
public class IdentityApplicationController {

    private final CreateIdentityApplicationUseCase createIdentityApplicationUseCase;
    private final GetIdentityApplicationUseCase getIdentityApplicationUseCase;
    private final IdentityApplicationCommandFactory commandFactory;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = IdentityApplicationResponse.class)))
    public ResponseEntity<IdentityApplicationResponse> create(
            @Valid
            @RequestBody
            CreateIdentityApplicationRequest request

    ) {

        CreateIdentityApplicationCommand command = commandFactory.from(request);
        IdentityApplicationResult result = createIdentityApplicationUseCase.execute(command);

        return ResponseEntity
                .created(java.net.URI.create("/api/v1/identity-applications/" + result.id()))
                .body(IdentityApplicationResponse.from(result));
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('IDENTITY_APPLICATION_READ')")
    @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
    @GetMapping("/{identityApplicationId}")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = IdentityApplicationResponse.class)))
    public ResponseEntity<IdentityApplicationResponse> findById(
            @PathVariable String identityApplicationId
    ) {
        IdentityApplicationResult result = getIdentityApplicationUseCase.execute(
                IdentityApplicationId.from(identityApplicationId)
        );

        return ResponseEntity.ok(IdentityApplicationResponse.from(result));
    }
}
