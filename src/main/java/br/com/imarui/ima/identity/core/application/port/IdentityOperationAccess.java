package br.com.imarui.ima.identity.core.application.port;

import java.util.UUID;

/** Current authorization at the point of use, after any resource lock. */
public interface IdentityOperationAccess {
  void requirePlatform(String permission);
  void requireTenant(String permission, UUID tenant);
  void requireSelfOrPlatform(String permission, UUID identity);
}
