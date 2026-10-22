package br.com.imarui.ima.authorization.infra.security;
import br.com.imarui.ima.authorization.core.application.port.AuthorizationActor;
import br.com.imarui.ima.identity.core.application.port.CurrentActor;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
@Component
@RequiredArgsConstructor
public class AuthorizationActorAdapter implements AuthorizationActor {
  private final CurrentActor currentActor;
  public UUID requireIdentityId() { return currentActor.requireIdentityId().value(); }
}
