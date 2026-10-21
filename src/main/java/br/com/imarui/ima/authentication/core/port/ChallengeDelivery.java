package br.com.imarui.ima.authentication.core.port;

import br.com.imarui.ima.authentication.core.domain.model.AuthenticationChallenge.Purpose;
import java.util.UUID;

public interface ChallengeDelivery {
  void enqueue(UUID identity, Purpose purpose);
}
