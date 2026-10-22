package br.com.imarui.ima.authentication.api.module;

import java.time.Instant;
import java.util.*;

/** Public boundary: technical identity is independent of any human Identity/session. */
public interface TechnicalServiceAuthentication {
  enum Operation { PDP_DECIDE, CATALOG_SYNC }
  record Caller(UUID clientId,String serviceCode,UUID credentialId,Set<Operation> operations,Set<UUID> scopeRoots) {
    public Caller { operations=Set.copyOf(operations); scopeRoots=Set.copyOf(scopeRoots); }
  }
  record Key(UUID id,String fingerprint,boolean active,Instant expiresAt) {}
  record Client(UUID id,String serviceCode,boolean enabled,Set<Operation> operations,Set<UUID> scopeRoots,long version,List<Key> keys) {}
  Caller verify(String assertion,String method,String path,byte[] body);
  Client enroll(String service,Set<Operation> operations,Set<UUID> scopeRoots,String publicKey,Instant expiresAt);
  Key rotate(String service,String publicKey,Instant expiresAt);
  void revokeKey(String service,UUID key);
  Client configure(String service,boolean enabled,Set<Operation> operations,Set<UUID> scopeRoots,long expectedVersion);
  Client client(String service);
}
