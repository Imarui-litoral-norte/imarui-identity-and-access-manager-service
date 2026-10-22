package br.com.imarui.ima.authorization.api.http;

import br.com.imarui.ima.authorization.core.application.usecase.*;
import br.com.imarui.ima.authorization.core.domain.model.PermissionManifest;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/authorization/service")
@RequiredArgsConstructor
public class ServiceAuthorizationController {
  public record DecisionRequest(@NotBlank @Size(max=16000) String actorToken,@NotNull UUID identityId,
      @NotBlank @Size(max=100) String permission,@NotBlank @Pattern(regexp="[A-Z][A-Z0-9_]{0,49}") String resourceType,
      @NotBlank @Size(max=150) String resourceId,UUID expectedScopeId,@NotNull UUID correlationId) {
    public PdpUseCases.Request command() { return new PdpUseCases.Request(actorToken,identityId,permission,resourceType,resourceId,expectedScopeId,correlationId); }
    @Override public String toString() { return "DecisionRequest[REDACTED]"; }
  }
  public record Batch(@NotEmpty @Size(max=20) List<@Valid DecisionRequest> decisions) {}
  public record ManifestRequest(@NotBlank String owner,@NotBlank String namespace,@Min(1) long version,
      @NotNull @Size(max=10000) List<PermissionManifest.Entry> permissions) {}
  private final PdpUseCases pdp;
  private final PermissionCatalogUseCases catalogs;

  @PostMapping("/decisions")
  @Operation(summary="Evaluate a trusted resource using independent service proof and live human session")
  public ResponseEntity<PdpUseCases.Result> decide(@Valid @RequestBody DecisionRequest request) {
    var result=pdp.decide(request.command());
    return ResponseEntity.status(result.allowed()?HttpStatus.OK:HttpStatus.FORBIDDEN).cacheControl(CacheControl.noStore()).body(result);
  }
  @PostMapping("/decisions/batch")
  @Operation(summary="Evaluate at most 20 independent decisions; caller and session checks apply to every item")
  public List<PdpUseCases.Result> batch(@Valid @RequestBody Batch request) {
    return request.decisions().stream().map(r -> pdp.decide(r.command())).toList();
  }
  @PostMapping("/catalog")
  @Operation(summary="Synchronize only the authenticated service's owned versioned manifest")
  public Map<String,Boolean> catalog(@Valid @RequestBody ManifestRequest request) {
    return Map.of("changed",catalogs.synchronizeExternal(new PermissionManifest(request.owner(),request.namespace(),request.version(),request.permissions())));
  }
}
