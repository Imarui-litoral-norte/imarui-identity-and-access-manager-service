package br.com.imarui.ima.authentication.infra.integration;

import br.com.imarui.ima.authentication.core.port.*;
import br.com.imarui.ima.identity.api.module.RevocationRequests;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class RevocationProcessor {
  private final RevocationRequests requests;
  private final AuthenticationStore store;
  private final IdentityAccess identities;
  private final Clock clock;

  @Transactional
  public int process() {
    var pending = requests.pending();
    // Stable owner ordering across batches prevents cross-Identity deadlocks between workers.
    pending.stream()
        .filter(r -> r.scope().equals("IDENTITY"))
        .map(RevocationRequests.Request::subject)
        .distinct()
        .sorted()
        .forEach(
            id -> {
              identities.lock(id);
              store.credential(id);
            });
    for (var r : pending) {
      if (r.scope().equals("IDENTITY")) {
        identities.lock(r.subject());
        store.credential(r.subject());
        store.revoke(r.subject(), r.requestedAt());
      }
      // TENANT cutoff remains durable in Identity and is enforced by its validated-context query.
      store.audit(r.subject(), "OUTBOX_" + r.scope(), clock.instant());
      requests.processed(r.id(), clock.instant());
    }
    return pending.size();
  }
}
