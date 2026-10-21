package br.com.imarui.ima.authentication.infra.mail;

import br.com.imarui.ima.authentication.core.domain.model.AuthenticationChallenge.Purpose;
import br.com.imarui.ima.authentication.core.port.*;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("test | dev")
public class LocalChallengeMail implements ChallengeMail, NotificationPort {
  public void send(String contact, String token, Purpose purpose) {
    /* Local/test delivery is intentionally silent. Tests capture this port explicitly. */
  }

  public void sendPasswordResetLink(String email, String link) {
    /* Never log secret links. */
  }
}
