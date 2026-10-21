package br.com.imarui.ima.platform.security.jwt;

import java.nio.file.*;
import java.security.*;
import java.security.interfaces.*;
import java.security.spec.*;
import java.util.*;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class JwtSigningKeyProvider {
  private final RSAPrivateKey privateKey;
  private final Map<String, RSAPublicKey> publicKeys;
  private final String kid;

  public JwtSigningKeyProvider(JwtProperties properties, Environment env) {
    try {
      RSAPublicKey pub;
      if (properties.getPrivateKeyFile() == null || properties.getPrivateKeyFile().isBlank()) {
        if (!env.acceptsProfiles(org.springframework.core.env.Profiles.of("test", "dev")))
          throw new IllegalStateException("Configure JWT_PRIVATE_KEY_FILE (RSA PKCS8).");
        var gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(3072);
        var pair = gen.generateKeyPair();
        privateKey = (RSAPrivateKey) pair.getPrivate();
        pub = (RSAPublicKey) pair.getPublic();
      } else {
        privateKey =
            (RSAPrivateKey)
                KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(pem(properties.getPrivateKeyFile())));
        if (!(privateKey instanceof RSAPrivateCrtKey crt))
          throw new IllegalStateException("RSA CRT private key required.");
        pub =
            (RSAPublicKey)
                KeyFactory.getInstance("RSA")
                    .generatePublic(
                        new RSAPublicKeySpec(crt.getModulus(), crt.getPublicExponent()));
      }
      if (pub.getModulus().bitLength() < 2048)
        throw new IllegalStateException("RSA key must have at least 2048 bits.");
      kid = id(pub);
      var keys = new LinkedHashMap<String, RSAPublicKey>();
      keys.put(kid, pub);
      for (String file : properties.getVerificationKeyFiles()) {
        var old =
            (RSAPublicKey)
                KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(pem(file)));
        if (old.getModulus().bitLength() < 2048)
          throw new IllegalStateException("Weak verification key.");
        keys.put(id(old), old);
      }
      publicKeys = Collections.unmodifiableMap(keys);
    } catch (java.io.IOException | GeneralSecurityException e) {
      throw new IllegalStateException("Cannot load configured RSA signing material.", e);
    }
  }

  private static byte[] pem(String file) throws java.io.IOException {
    return Base64.getMimeDecoder()
        .decode(
            Files.readString(Path.of(file))
                .replaceAll("-----[^-]+-----", "")
                .replaceAll("\\s", ""));
  }

  private static String id(RSAPublicKey key) throws GeneralSecurityException {
    return HexFormat.of()
        .formatHex(MessageDigest.getInstance("SHA-256").digest(key.getEncoded()))
        .substring(0, 24);
  }

  public RSAPrivateKey getKey() {
    return privateKey;
  }

  public String kid() {
    return kid;
  }

  public Map<String, RSAPublicKey> publicKeys() {
    return publicKeys;
  }

  public com.nimbusds.jose.jwk.JWKSet jwks() {
    return new com.nimbusds.jose.jwk.JWKSet(
        publicKeys.entrySet().stream()
            .map(
                e ->
                    (com.nimbusds.jose.jwk.JWK)
                        new com.nimbusds.jose.jwk.RSAKey.Builder(e.getValue())
                            .keyID(e.getKey())
                            .algorithm(com.nimbusds.jose.JWSAlgorithm.RS256)
                            .keyUse(com.nimbusds.jose.jwk.KeyUse.SIGNATURE)
                            .build())
            .toList());
  }
}
