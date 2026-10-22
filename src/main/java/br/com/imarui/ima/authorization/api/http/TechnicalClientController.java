package br.com.imarui.ima.authorization.api.http;

import br.com.imarui.ima.authentication.api.module.TechnicalServiceAuthentication;
import br.com.imarui.ima.authorization.core.application.command.PolicyChangeReason;
import br.com.imarui.ima.authorization.core.application.usecase.TechnicalClientAdministrationUseCases;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/authorization/admin/service-clients")
@RequiredArgsConstructor
public class TechnicalClientController {
  public record Enroll(@NotBlank @Pattern(regexp="[a-z][a-z0-9-]{1,49}") String service,
      @NotEmpty Set<TechnicalServiceAuthentication.Operation> operations,@NotEmpty Set<UUID> scopeRoots,
      @NotBlank @Size(max=4096) String publicKey,@NotNull Instant expiresAt,@NotBlank @Size(max=500) String reason,@NotNull UUID correlationId) {}
  public record Configure(boolean enabled,@NotEmpty Set<TechnicalServiceAuthentication.Operation> operations,
      @NotEmpty Set<UUID> scopeRoots,@NotNull @Min(0) Long version,@NotBlank @Size(max=500) String reason,@NotNull UUID correlationId) {}
  public record KeyRequest(@NotBlank @Size(max=4096) String publicKey,@NotNull Instant expiresAt,
      @NotBlank @Size(max=500) String reason,@NotNull UUID correlationId) {}
  public record Reason(@NotBlank @Size(max=500) String reason,@NotNull UUID correlationId) {}
  public record Resource(@NotBlank String type,@NotBlank @Size(max=150) String resourceId,@NotNull UUID scopeId,
      @NotBlank @Size(max=500) String reason,@NotNull UUID correlationId) {}
  private final TechnicalClientAdministrationUseCases clients;

  @PostMapping
  public TechnicalServiceAuthentication.Client enroll(@Valid @RequestBody Enroll r) {
    return clients.enroll(r.service(),r.operations(),r.scopeRoots(),r.publicKey(),r.expiresAt(),new PolicyChangeReason(r.reason(),r.correlationId()));
  }
  @PatchMapping("/{service}")
  public TechnicalServiceAuthentication.Client configure(@PathVariable String service,@Valid @RequestBody Configure r) {
    return clients.configure(service,r.enabled(),r.operations(),r.scopeRoots(),r.version(),new PolicyChangeReason(r.reason(),r.correlationId()));
  }
  @PostMapping("/{service}/keys")
  public TechnicalServiceAuthentication.Key rotate(@PathVariable String service,@Valid @RequestBody KeyRequest r) {
    return clients.rotate(service,r.publicKey(),r.expiresAt(),new PolicyChangeReason(r.reason(),r.correlationId()));
  }
  @PostMapping("/{service}/keys/{key}/revoke")
  public void revoke(@PathVariable String service,@PathVariable UUID key,@Valid @RequestBody Reason r) {
    clients.revoke(service,key,new PolicyChangeReason(r.reason(),r.correlationId()));
  }
  @PostMapping("/{service}/resources")
  public void bind(@PathVariable String service,@Valid @RequestBody Resource r) {
    clients.bind(service,r.type(),r.resourceId(),r.scopeId(),new PolicyChangeReason(r.reason(),r.correlationId()));
  }
}
