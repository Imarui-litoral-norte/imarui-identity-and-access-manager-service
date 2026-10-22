package br.com.imarui.ima.authorization.core.repository;

import java.time.Instant;
import java.util.*;

public interface SecurityAuditRepository {
  record Entry(UUID id,String event,UUID actorIdentityId,String service,String resourceType,String resourceId,UUID scopeId,
      Instant occurredAt,String reason,UUID correlationId,Object before,Object after) {}
  record Result(List<Entry> items,long total) {}
  Result query(Set<UUID> scopes,boolean includeGlobal,String event,UUID correlation,int offset,int size);
  Set<UUID> includeHistoricalScopes(Set<UUID> scopes);
}
