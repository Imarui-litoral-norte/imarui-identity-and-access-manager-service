package br.com.imarui.ima.identity.api.controller.identityapplication;

import br.com.imarui.ima.identity.api.dto.identityapplication.request.ApproveIdentityApplicationRequest;
import br.com.imarui.ima.identity.api.dto.identityapplication.request.RejectIdentityApplicationRequest;
import br.com.imarui.ima.identity.api.dto.identityapplication.response.IdentityApplicationResponse;
import br.com.imarui.ima.identity.core.application.command.identityapplication.ApproveIdentityApplicationCommand;
import br.com.imarui.ima.identity.core.application.command.identityapplication.RejectIdentityApplicationCommand;
import br.com.imarui.ima.identity.core.application.result.identityapplication.IdentityApplicationResult;
import br.com.imarui.ima.identity.core.application.usecase.identityapplication.ApproveIdentityApplicationUseCase;
import br.com.imarui.ima.identity.core.application.usecase.identityapplication.GetIdentityApplicationUseCase;
import br.com.imarui.ima.identity.core.application.usecase.identityapplication.ListIdentityApplicationsUseCase;
import br.com.imarui.ima.identity.core.application.usecase.identityapplication.RejectIdentityApplicationUseCase;
import br.com.imarui.ima.identity.core.domain.model.identityapplication.IdentityApplicationId;
import br.com.imarui.ima.identity.core.domain.model.tenant.TenantId;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@org.springframework.security.access.prepost.PreAuthorize("hasAuthority('IDENTITY_APPLICATION_REVIEW')")
@br.com.imarui.ima.identity.api.exception.IdentityApiErrors
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping(
        value = "/api/v1/admin/identity-applications",
        produces = MediaType.APPLICATION_JSON_VALUE
)
@RequiredArgsConstructor
public class AdminIdentityApplicationController {

    private final ListIdentityApplicationsUseCase listIdentityApplicationsUseCase;
    private final GetIdentityApplicationUseCase getIdentityApplicationUseCase;
    private final ApproveIdentityApplicationUseCase approveIdentityApplicationUseCase;
    private final RejectIdentityApplicationUseCase rejectIdentityApplicationUseCase;

    @GetMapping
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", content = @io.swagger.v3.oas.annotations.media.Content(array = @io.swagger.v3.oas.annotations.media.ArraySchema(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = IdentityApplicationResponse.class))))
    public ResponseEntity<List<IdentityApplicationResponse>> findAll() {
        List<IdentityApplicationResponse> response = listIdentityApplicationsUseCase
                .execute()
                .stream()
                .map(IdentityApplicationResponse::from)
                .toList();

        return ResponseEntity.ok(response);
    }

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

    @PostMapping(
            value = "/{identityApplicationId}/approve",
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = IdentityApplicationResponse.class)))
    public ResponseEntity<IdentityApplicationResponse> approve(
            @PathVariable String identityApplicationId,
            @Valid @RequestBody ApproveIdentityApplicationRequest request
    ) {
        ApproveIdentityApplicationCommand command =
                new ApproveIdentityApplicationCommand(
                        IdentityApplicationId.from(identityApplicationId),
                        TenantId.from(request.tenantId()),
                        request.affiliation().toSelection()
                );
        IdentityApplicationResult result =
                approveIdentityApplicationUseCase.execute(command);

        return ResponseEntity.ok(IdentityApplicationResponse.from(result));
    }

    @PostMapping(
            value = "/{identityApplicationId}/reject",
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = IdentityApplicationResponse.class)))
    public ResponseEntity<IdentityApplicationResponse> reject(
            @PathVariable String identityApplicationId,
            @Valid @RequestBody RejectIdentityApplicationRequest request
    ) {
        RejectIdentityApplicationCommand command =
                new RejectIdentityApplicationCommand(
                        IdentityApplicationId.from(identityApplicationId),
                        request.reason()
                );
        IdentityApplicationResult result =
                rejectIdentityApplicationUseCase.execute(command);

        return ResponseEntity.ok(IdentityApplicationResponse.from(result));
    }
}
