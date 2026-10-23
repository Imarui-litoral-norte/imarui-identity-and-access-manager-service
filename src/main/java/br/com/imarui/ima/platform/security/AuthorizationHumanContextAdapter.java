package br.com.imarui.ima.platform.security;

import br.com.imarui.ima.authorization.core.application.port.AuthenticatedHumanContext;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthorizationHumanContextAdapter implements AuthenticatedHumanContext {
  private final JwtDecoder decoder;
  public Human verify(String accessToken,UUID expectedIdentity) {
    if(accessToken==null || accessToken.length()>16000) throw new org.springframework.security.authentication.BadCredentialsException("Invalid actor context.");
    var jwt=decoder.decode(accessToken);
    var identity=UUID.fromString(jwt.getSubject());
    if(!identity.equals(expectedIdentity)) throw new org.springframework.security.authentication.BadCredentialsException("Actor does not match the signed session.");
    return new Human(identity,UUID.fromString(jwt.getClaimAsString("sid")));
  }
}
