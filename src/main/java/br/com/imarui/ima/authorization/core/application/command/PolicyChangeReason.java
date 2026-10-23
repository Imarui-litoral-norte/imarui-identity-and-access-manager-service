package br.com.imarui.ima.authorization.core.application.command;
import java.util.UUID;
public record PolicyChangeReason(String reason, UUID correlationId) {
  public PolicyChangeReason {
    if (reason==null || reason.isBlank() || reason.length()>500) throw new IllegalArgumentException("Policy change reason required.");
    reason=reason.trim();
    if (correlationId==null) correlationId=UUID.randomUUID();
  }
}
