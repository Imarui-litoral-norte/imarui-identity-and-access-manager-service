package br.com.imarui.ima.authentication.api.module;
import java.util.UUID;

public interface AdministrativeAuthentication {
  void reauthenticate(UUID identity,String password);
  boolean sessionActive(UUID identity,UUID session);
}
