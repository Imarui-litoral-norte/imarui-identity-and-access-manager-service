package br.com.imarui.ima.authorization.api.module;

import java.util.UUID;

/** Sanitized append-only security events; never pass tokens, passwords or raw request bodies. */
public interface SecurityAuditEvents {
  void record(String event,UUID actor,String service,String resource,UUID scope,String reason,UUID correlation);
  void recordInTransaction(String event,UUID actor,String service,String resource,UUID scope,String reason,UUID correlation);
}
