package br.com.imarui.ima.authentication.core.exception;

public class TechnicalAuthenticationException extends RuntimeException {
  public enum Reason { INVALID_PROOF, REPLAY }
  private final Reason reason;
  public TechnicalAuthenticationException() { this(Reason.INVALID_PROOF); }
  public TechnicalAuthenticationException(Reason reason) { super("Invalid technical authentication."); this.reason=java.util.Objects.requireNonNull(reason); }
  public Reason reason() { return reason; }
}
