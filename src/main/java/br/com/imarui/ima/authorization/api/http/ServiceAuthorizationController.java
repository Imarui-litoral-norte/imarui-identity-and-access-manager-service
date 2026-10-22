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
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name="serviceProof")
@io.swagger.v3.oas.annotations.tags.Tag(name="Authorization services")
public class ServiceAuthorizationController {
  public record DecisionRequest(@NotBlank @Size(max=16000) @io.swagger.v3.oas.annotations.media.Schema(accessMode=io.swagger.v3.oas.annotations.media.Schema.AccessMode.WRITE_ONLY) String actorToken,@NotNull UUID identityId,
      @NotBlank @Size(max=100) String permission,@NotBlank @Pattern(regexp="[A-Z][A-Z0-9_]{0,49}") String resourceType,
      @NotBlank @Size(max=150) String resourceId,UUID expectedScopeId,@NotNull UUID correlationId) {
    public PdpUseCases.Request command() { return new PdpUseCases.Request(actorToken,identityId,permission,resourceType,resourceId,expectedScopeId,correlationId); }
    @Override public String toString() { return "DecisionRequest[REDACTED]"; }
  }
  public record Batch(@NotNull UUID correlationId,@NotEmpty @Size(max=20) List<@NotNull @Valid DecisionRequest> decisions) {}
  public record PdpDecisionResponse(UUID decisionId,boolean allowed,String reasonCode,UUID correlationId,java.time.Instant evaluatedAt) {
    static PdpDecisionResponse from(PdpUseCases.Result r) { return new PdpDecisionResponse(r.decisionId(),r.allowed(),r.reasonCode(),r.correlationId(),r.evaluatedAt()); }
  }
  public record ManifestRequest(@NotBlank String owner,@NotBlank String namespace,@Min(1) long version,
      @NotNull @Size(max=10000) List<PermissionManifest.Entry> permissions) {}
  private final PdpUseCases pdp;
  private final PermissionCatalogUseCases catalogs;

  @PostMapping("/decisions")
  @Operation(summary="Evaluate a trusted resource using independent service proof and live human session")
  public ResponseEntity<PdpDecisionResponse> decide(@Valid @RequestBody DecisionRequest request) {
    var result=pdp.decide(request.command());
    return ResponseEntity.status(result.allowed()?HttpStatus.OK:HttpStatus.FORBIDDEN).cacheControl(CacheControl.noStore()).body(PdpDecisionResponse.from(result));
  }
  @PostMapping("/decisions/batch")
  @Operation(summary="Evaluate at most 20 independent decisions; caller and session checks apply to every item")
  public List<PdpDecisionResponse> batch(@Valid @RequestBody Batch request) {
    if(request.decisions().stream().anyMatch(r -> !request.correlationId().equals(r.correlationId())))
      throw new IllegalArgumentException("Batch correlation must match every decision.");
    return pdp.batch(request.decisions().stream().map(DecisionRequest::command).toList()).stream().map(PdpDecisionResponse::from).toList();
  }
  @PostMapping("/catalog")
  @Operation(summary="Synchronize only the authenticated service's owned versioned manifest")
  public Map<String,Boolean> catalog(@Valid @RequestBody ManifestRequest request) {
    return Map.of("changed",catalogs.synchronizeExternal(new PermissionManifest(request.owner(),request.namespace(),request.version(),request.permissions())));
  }
}
