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
  private final br.com.imarui.ima.authorization.core.application.port.AuthenticatedHumanContext humans;
  public UUID requireIdentityId() {
    var identity=currentActor.requireIdentityId().value();
    var authentication=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
    if(authentication instanceof org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken jwt) {
      // Mutation guards invoke this after acquiring the policy lock; read paths
      // invoke it inside their policy snapshot. A filter's earlier SID check is insufficient.
      humans.verify(jwt.getToken().getTokenValue(),identity);
    }
    return identity;
  }
}
