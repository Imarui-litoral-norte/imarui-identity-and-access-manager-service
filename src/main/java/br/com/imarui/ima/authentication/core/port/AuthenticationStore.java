package br.com.imarui.ima.authentication.core.port;

import br.com.imarui.ima.authentication.core.domain.model.*;
import java.time.*;
import java.util.*;

public interface AuthenticationStore {
  Optional<Credential> credential(UUID id);

  void save(Credential c);

  Optional<AuthenticationChallenge> challenge(String hash, boolean lock);

  void replaceChallenge(AuthenticationChallenge c);

  void consume(UUID challenge, Instant now);

  Session createSession(UUID id, Instant now);

  Optional<Session> session(UUID sid, boolean lock);

  void save(Session session);

  Optional<RefreshToken> token(String hash, boolean lock);

  RefreshToken save(RefreshToken token);

  void revoke(UUID identity, Instant cutoff);

  void revokeTokens(long session);

  List<Session> sessions(UUID owner);

  void audit(UUID id, String event, Instant now);
}
