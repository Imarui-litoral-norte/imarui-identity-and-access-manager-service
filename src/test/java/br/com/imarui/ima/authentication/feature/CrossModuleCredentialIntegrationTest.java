package br.com.imarui.ima.authentication.feature;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import br.com.imarui.ima.authentication.core.application.exception.AuthenticationFailure;
import br.com.imarui.ima.authentication.core.domain.model.AuthenticationChallenge.Purpose;
import br.com.imarui.ima.authentication.core.port.IdentityAccess;
import br.com.imarui.ima.identity.core.application.command.identity.*;
import br.com.imarui.ima.identity.core.application.usecase.identity.ChangeNicknameUseCase;
import br.com.imarui.ima.identity.core.domain.enums.identity.IdentityKind;
import br.com.imarui.ima.identity.core.domain.model.identity.Identity;
import br.com.imarui.ima.identity.support.IdentityIntegrationSupport;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

class CrossModuleCredentialIntegrationTest extends IdentityIntegrationSupport {
  private static final String PASSWORD = "Fixture password 123!";
  private static final String NEXT = "Replacement password 456!";
  @MockitoSpyBean IdentityAccess identityAccess;
  @Autowired ChangeNicknameUseCase nickname;

  private Identity active(String name) {
    var identity = service(name + "@example.test");
    identity.assignNickname(name);
    identity = identities.save(identity);
    firstAccess(identity.getId());
    return identities.findById(identity.getId()).orElseThrow();
  }

  @ParameterizedTest
  @ValueSource(strings = {"disable", "rename"})
  void loginRechecksEligibilityAndIdentifierAfterDiscovery(String change) throws Exception {
    var target = active("login-race");
    var discovered = new CountDownLatch(1);
    var resume = new CountDownLatch(1);
    doAnswer(call -> {
      var result = call.callRealMethod();
      discovered.countDown();
      assertTrue(resume.await(10, TimeUnit.SECONDS));
      return result;
    }).when(identityAccess).resolve(eq(IdentityKind.SERVICE), eq("login-race"));
    var pool = Executors.newSingleThreadExecutor();
    try {
      var login = pool.submit(() -> {
        try {
          authentication.login(IdentityKind.SERVICE, "login-race", PASSWORD);
          return true;
        } catch (AuthenticationFailure denied) { return false; }
      });
      assertTrue(discovered.await(10, TimeUnit.SECONDS));
      if (change.equals("disable")) disable.execute(new DisableIdentityCommand(target.getId()));
      else nickname.execute(target.getId(), "new-login-name");
      resume.countDown();
      assertFalse(login.get(10, TimeUnit.SECONDS), "The discovered account changed before its lock");
      assertEquals(0, jdbc.queryForObject("select count(*) from authentication_sessions where identity_id=?", Integer.class, target.getId().value()));
    } finally {
      resume.countDown();
      pool.shutdownNow();
    }
  }

  @Test
  void resetFromBeforeDisableCannotChangeCredentialsAfterReactivationWithoutWorker() {
    var target = active("old-reset");
    var old = authentication.issueChallenge(target.getId().value(), Purpose.PASSWORD_RESET);
    disable.execute(new DisableIdentityCommand(target.getId()));
    reactivate.execute(new ReactivateIdentityCommand(target.getId()));
    assertThrows(AuthenticationFailure.class, () -> authentication.complete(old, NEXT, Purpose.PASSWORD_RESET));
    assertNotNull(authentication.login(IdentityKind.SERVICE, "old-reset", PASSWORD));
    var fresh = authentication.issueChallenge(target.getId().value(), Purpose.PASSWORD_RESET);
    authentication.complete(fresh, NEXT, Purpose.PASSWORD_RESET);
    assertNotNull(authentication.login(IdentityKind.SERVICE, "old-reset", NEXT));
  }

  @Test
  void failedCurrentPasswordAttemptsPersistAndUseCredentialLockout() {
    var target = active("change-lockout");
    actor(target.getId());
    for (int attempt = 0; attempt < 5; attempt++)
      assertThrows(AuthenticationFailure.class, () -> authentication.changePassword(target.getId().value(), "Wrong password!", NEXT));
    assertEquals(5, jdbc.queryForObject("select failures from authentication_credentials where identity_id=?", Integer.class, target.getId().value()));
    assertEquals(5, jdbc.queryForObject("select count(*) from authentication_audit where identity_id=? and event='PASSWORD_CHANGE_FAILED'", Integer.class, target.getId().value()));
    assertThrows(AuthenticationFailure.class, () -> authentication.changePassword(target.getId().value(), PASSWORD, NEXT));
    assertThrows(AuthenticationFailure.class, () -> authentication.login(IdentityKind.SERVICE, "change-lockout", PASSWORD));
  }
}
