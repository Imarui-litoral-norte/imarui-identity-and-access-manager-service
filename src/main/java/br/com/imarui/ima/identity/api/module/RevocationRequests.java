package br.com.imarui.ima.identity.api.module;

import java.time.Instant;
import java.util.*;

public interface RevocationRequests {
  record Request(UUID id, String scope, UUID subject, Instant requestedAt) {}

  List<Request> pending();

  void processed(UUID id, Instant now);
}
