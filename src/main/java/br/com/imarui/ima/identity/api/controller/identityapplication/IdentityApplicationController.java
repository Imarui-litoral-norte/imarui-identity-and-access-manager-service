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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@io.swagger.v3.oas.annotations.tags.Tag(
    name = "Identity",
    description = "Identidades, solicitações e contexto Tenant/Affiliation")
@RestController
@RequestMapping(
    value = "/api/v1/identity-applications",
    produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Validated
public class IdentityApplicationController {

  private final CreateIdentityApplicationUseCase createIdentityApplicationUseCase;
  private final GetIdentityApplicationUseCase getIdentityApplicationUseCase;

  @PostMapping(value="/users", consumes=MediaType.APPLICATION_JSON_VALUE)
  @br.com.imarui.ima.platform.openapi.group.SwaggerOperationGroup(value="Solicitações · Cadastro", order=40)
  @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  @io.swagger.v3.oas.annotations.Operation(summary="Solicitar acesso com CPF ou CNPJ", description="Infere PERSON ou LEGAL_ENTITY pelo documento válido. Cria somente solicitação PENDING; apelido e e-mail são únicos globalmente.")
  public ResponseEntity<IdentityApplicationResponse> createUser(@Valid @RequestBody br.com.imarui.ima.identity.api.dto.identityapplication.request.create.CreateUserIdentityApplicationRequest request) {
    return created(createIdentityApplicationUseCase.execute(request.toCommand()));
  }
  @PostMapping(value="/services", consumes=MediaType.APPLICATION_JSON_VALUE)
  @br.com.imarui.ima.platform.openapi.group.SwaggerOperationGroup(value="Solicitações · Cadastro", order=40)
  @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  @io.swagger.v3.oas.annotations.Operation(summary="Solicitar acesso de serviço", description="Sem documento. Cria somente solicitação PENDING; SERVICE só é efetivado pela aprovação administrativa.")
  public ResponseEntity<IdentityApplicationResponse> createService(@Valid @RequestBody br.com.imarui.ima.identity.api.dto.identityapplication.request.create.CreateServiceApplicationRequest request) {
    return created(createIdentityApplicationUseCase.execute(request.toCommand()));
  }
  private ResponseEntity<IdentityApplicationResponse> created(IdentityApplicationResult result) {
    return ResponseEntity.created(java.net.URI.create("/api/v1/identity-applications/"+result.id())).body(IdentityApplicationResponse.from(result));
  }

  @org.springframework.security.access.prepost.PreAuthorize(
      "hasAuthority('IDENTITY_APPLICATION_READ')")
  @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
  @GetMapping("/{identityApplicationId}")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "200",
      content =
          @io.swagger.v3.oas.annotations.media.Content(
              schema =
                  @io.swagger.v3.oas.annotations.media.Schema(
                      implementation = IdentityApplicationResponse.class)))
  @io.swagger.v3.oas.annotations.Operation(
      summary = "Consultar solicitação de identidade",
      description =
          "Consulta solicitação por UUID com detalhes do tipo e metadados de revisão. O UUID não"
              + " concede acesso: exige IDENTITY_APPLICATION_READ. Antes da aprovação/rejeição os"
              + " campos de resolução permanecem nulos.")
  @br.com.imarui.ima.platform.openapi.group.SwaggerOperationGroup(
      value = "Solicitações · Consulta",
      order = 50)
  @br.com.imarui.ima.platform.openapi.security.RequiredPermission("IDENTITY_APPLICATION_READ")
  public ResponseEntity<IdentityApplicationResponse> findById(
      @PathVariable String identityApplicationId) {
    IdentityApplicationResult result =
        getIdentityApplicationUseCase.execute(IdentityApplicationId.from(identityApplicationId));

    return ResponseEntity.ok(IdentityApplicationResponse.from(result));
  }
}
