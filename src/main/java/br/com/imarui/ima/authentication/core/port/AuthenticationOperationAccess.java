package br.com.imarui.ima.authentication.core.port;

public interface AuthenticationOperationAccess {
  void requirePlatform(String permission);
  void requireIdentity(java.util.UUID identity);
}
