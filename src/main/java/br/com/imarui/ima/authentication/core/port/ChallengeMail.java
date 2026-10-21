package br.com.imarui.ima.authentication.core.port;

import br.com.imarui.ima.authentication.core.domain.model.AuthenticationChallenge.Purpose;

public interface ChallengeMail {
  void send(String contact, String token, Purpose purpose);
}
