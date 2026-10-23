package br.com.imarui.ima.authentication.core.exception;

public class TechnicalAuthenticationException extends RuntimeException {
  public TechnicalAuthenticationException() { super("Invalid technical authentication."); }
}
