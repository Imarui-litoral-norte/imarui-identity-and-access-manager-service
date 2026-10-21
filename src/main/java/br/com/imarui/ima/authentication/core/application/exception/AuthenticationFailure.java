package br.com.imarui.ima.authentication.core.application.exception;

public class AuthenticationFailure extends RuntimeException {
  public AuthenticationFailure() {
    super("Authentication failed.");
  }
}
