package br.com.imarui.ima.authorization.infra.security;

import br.com.imarui.ima.authorization.core.application.port.CurrentHumanSession;
import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class CurrentHumanSessionAdapter implements CurrentHumanSession {
  public UUID requireSessionId() {
    if(!(SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken jwt))
      throw new SecurityException("A live human session is required.");
    return UUID.fromString(jwt.getToken().getClaimAsString("sid"));
  }
}
