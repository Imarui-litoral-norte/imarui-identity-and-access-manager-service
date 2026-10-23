package br.com.imarui.ima.authorization.infra.security;

import br.com.imarui.ima.authentication.api.module.TechnicalServiceAuthentication.Caller;
import br.com.imarui.ima.authorization.core.application.port.TechnicalCaller;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class TechnicalCallerAdapter implements TechnicalCaller {
  public Caller require() {
    var authentication=SecurityContextHolder.getContext().getAuthentication();
    if(authentication==null || !authentication.isAuthenticated() || !(authentication.getPrincipal() instanceof Caller caller))
      throw new SecurityException("Technical caller required.");
    return caller;
  }
}
