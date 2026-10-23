package br.com.imarui.ima.authentication.core;

import static org.junit.jupiter.api.Assertions.*;

import br.com.imarui.ima.authentication.core.domain.model.*;
import br.com.imarui.ima.authorization.api.catalog.*;
import br.com.imarui.ima.authorization.core.domain.model.*;
import br.com.imarui.ima.identity.core.domain.model.identity.Nickname;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class AuthenticationDomainTest {
  @Test
  void nicknamesNormalizeAndCannotBeDocumentsOrEmail() {
    assertEquals("some.name", Nickname.from("  SOME.Name  ").value());
    for (String value :
        List.of("12345678901", "123.456.789-01", "person@example.test", "ab", "with space", "ábc"))
      assertThrows(IllegalArgumentException.class, () -> Nickname.from(value));
  }

  @Test
  void passwordPolicyProtectsBcryptByteLimit() {
    assertDoesNotThrow(() -> Credential.validatePassword("Strong password 123!"));
    for (String value : List.of("weak", "é".repeat(37), "x".repeat(73)))
      assertThrows(IllegalArgumentException.class, () -> Credential.validatePassword(value));
  }

  @Test
  void credentialLocksTemporarilyAndResetsAfterSuccess() {
    var now = Instant.parse("2026-01-01T00:00:00Z");
    var c = new Credential(UUID.randomUUID(), "hash", 0, null, true, now, now);
    for (int n = 0; n < 5; n++) c = c.failed(now);
    assertFalse(c.allowed(now));
    assertTrue(c.allowed(now.plusSeconds(900)));
    assertEquals(0, c.successful(now.plusSeconds(901)).failures());
    assertFalse(c.toString().contains("hash"));
  }

  @Test
  void challengePurposeExpiryAndConsumptionAreIndependentInvariants() {
    var now = Instant.parse("2026-01-01T00:00:00Z");
    var c =
        new AuthenticationChallenge(
            UUID.randomUUID(),
            UUID.randomUUID(),
            AuthenticationChallenge.Purpose.ACCOUNT_ACTIVATION,
            "secret-hash",
            now,
            now.plusSeconds(60),
            null);
    assertTrue(c.valid(AuthenticationChallenge.Purpose.ACCOUNT_ACTIVATION, now));
    assertFalse(c.valid(AuthenticationChallenge.Purpose.PASSWORD_RESET, now));
    assertFalse(c.valid(AuthenticationChallenge.Purpose.ACCOUNT_ACTIVATION, now.plusSeconds(60)));
    assertFalse(c.toString().contains("secret-hash"));
  }

  @Test
  void importedPermissionSynchronizationDoesNotChangeFunctionalCode() {
    var now = Instant.parse("2026-01-01T00:00:00Z");
    var d = PermissionDefinition.values()[0];
    var p = Permission.fromDefinition(d, now);
    assertFalse(p.synchronizeWith(d, now.plusSeconds(1)));
    var changed = new PermissionDefinition(d.code(), "Updated description", d.module());
    assertTrue(p.synchronizeWith(changed, now.plusSeconds(1)));
    assertEquals(d.code().name(), p.getCode());
    assertThrows(IllegalArgumentException.class, () -> RolePermission.create(-1L, 1L, now));
  }
}
