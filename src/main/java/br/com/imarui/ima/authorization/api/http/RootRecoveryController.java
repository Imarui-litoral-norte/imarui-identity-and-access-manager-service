package br.com.imarui.ima.authorization.api.http;

import br.com.imarui.ima.authorization.core.application.command.PolicyChangeReason;
import br.com.imarui.ima.authorization.core.application.usecase.RootRecoveryUseCases;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/authorization/admin/root/transfers")
@RequiredArgsConstructor
public class RootRecoveryController {
  public record Initiate(@NotNull UUID recipientId,@NotNull UUID scopeId,
      @NotBlank @Size(max=72) @Schema(accessMode=Schema.AccessMode.WRITE_ONLY) String password,
      @NotBlank @Size(max=500) String reason,@NotNull UUID correlationId) {
    @Override public String toString() { return "RootTransferRequest[REDACTED]"; }
  }
  public record Confirm(@NotBlank @Size(max=72) @Schema(accessMode=Schema.AccessMode.WRITE_ONLY) String password) {
    @Override public String toString() { return "RootTransferConfirmation[REDACTED]"; }
  }
  private final RootRecoveryUseCases recovery;
  @PostMapping public RootRecoveryUseCases.Approval initiate(@Valid @RequestBody Initiate r) {
    return recovery.initiate(r.recipientId(),r.scopeId(),r.password(),new PolicyChangeReason(r.reason(),r.correlationId()));
  }
  @PostMapping("/{id}/confirm") public RootRecoveryUseCases.Approval confirm(@PathVariable UUID id,@Valid @RequestBody Confirm r) {
    return recovery.confirm(id,r.password());
  }
}
