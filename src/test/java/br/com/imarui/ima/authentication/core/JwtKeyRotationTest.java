package br.com.imarui.ima.authentication.core;

import static org.junit.jupiter.api.Assertions.*;

import br.com.imarui.ima.authentication.infra.security.jwt.RsaAccessTokenIssuer;
import br.com.imarui.ima.platform.security.jwt.*;
import io.jsonwebtoken.Jwts;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

class JwtKeyRotationTest {
  @TempDir Path directory;

  @Test
  void loadsPkcs8AndKeepsPreviousPublicVerificationKey() throws Exception {
    var env = new MockEnvironment();
    env.setActiveProfiles("test");
    var properties = new JwtProperties();
    var old = new JwtSigningKeyProvider(properties, env);
    var replacement = new JwtSigningKeyProvider(properties, env);
    Path privateFile = directory.resolve("current.pem");
    Path publicFile = directory.resolve("old-public.pem");
    Files.writeString(
        privateFile,
        "-----BEGIN PRIVATE KEY-----\n"
            + Base64.getMimeEncoder().encodeToString(replacement.getKey().getEncoded())
            + "\n-----END PRIVATE KEY-----");
    Files.writeString(
        publicFile,
        "-----BEGIN PUBLIC KEY-----\n"
            + Base64.getMimeEncoder().encodeToString(old.publicKeys().get(old.kid()).getEncoded())
            + "\n-----END PUBLIC KEY-----");
    properties.setPrivateKeyFile(privateFile.toString());
    properties.setVerificationKeyFiles(List.of(publicFile.toString()));
    var rotated = new JwtSigningKeyProvider(properties, env);
    assertEquals(replacement.kid(), rotated.kid());
    assertEquals(2, rotated.jwks().getKeys().size());
    assertFalse(rotated.jwks().toString().contains("\"d\":"));
    String before =
        new RsaAccessTokenIssuer(old, properties)
            .issue(UUID.randomUUID(), UUID.randomUUID(), Instant.now());
    assertNotNull(
        Jwts.parser()
            .verifyWith(rotated.publicKeys().get(old.kid()))
            .build()
            .parseSignedClaims(before));
    String after =
        new RsaAccessTokenIssuer(rotated, properties)
            .issue(UUID.randomUUID(), UUID.randomUUID(), Instant.now());
    assertNotNull(
        Jwts.parser()
            .verifyWith(rotated.publicKeys().get(rotated.kid()))
            .build()
            .parseSignedClaims(after));
  }

  @Test
  void productionRefusesImplicitSigningKeys() {
    var env = new MockEnvironment();
    assertThrows(
        IllegalStateException.class, () -> new JwtSigningKeyProvider(new JwtProperties(), env));
  }
}
