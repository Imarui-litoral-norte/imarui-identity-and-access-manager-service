package br.com.imarui.ima.authentication.infra.security;

import br.com.imarui.ima.authentication.api.module.AdministrativeAuthentication;
import br.com.imarui.ima.authentication.core.application.exception.AuthenticationFailure;
import br.com.imarui.ima.authentication.core.port.*;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

@Component
@RequiredArgsConstructor
public class AdministrativeAuthenticationAdapter implements AdministrativeAuthentication {
  private final AuthenticationStore store;
  private final IdentityAccess identities;
  private final PasswordHasher passwords;
  private final Clock clock;
  private String dummy;
  @jakarta.annotation.PostConstruct void initialize() { dummy=passwords.hash(UUID.randomUUID().toString()); }

  @Transactional(propagation=Propagation.REQUIRES_NEW,noRollbackFor=AuthenticationFailure.class)
  public void reauthenticate(UUID identity,String password) {
    var account=identities.lock(identity).orElseThrow(AuthenticationFailure::new);
    var credential=store.credential(identity).orElse(null); var now=clock.instant();
    boolean matches=passwords.matches(password,credential==null?dummy:credential.passwordHash());
    if(!account.active() || credential==null || !credential.allowed(now) || !matches) {
      if(credential!=null && account.active() && credential.allowed(now) && !matches) store.save(credential.failed(now));
      store.audit(identity,"ADMINISTRATIVE_REAUTHENTICATION_FAILED",now);
      throw new AuthenticationFailure();
    }
    store.save(credential.successful(now));
    store.audit(identity,"ADMINISTRATIVE_REAUTHENTICATION_SUCCESS",now);
  }
  @Transactional(readOnly=true)
  public boolean sessionActive(UUID identity,UUID sid) {
    var session=store.session(sid,false).orElse(null);
    return session!=null && identity.equals(session.getIdentityId()) && session.isValid(clock.instant())
        && identities.valid(identity,session.getCreatedAt(),null);
  }
}
