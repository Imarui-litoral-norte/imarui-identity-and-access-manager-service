package br.com.imarui.ima.authentication.infra.security.jwt;

import br.com.imarui.ima.authentication.core.port.AccessTokenIssuer;
import br.com.imarui.ima.platform.security.jwt.*;
import io.jsonwebtoken.Jwts;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RsaAccessTokenIssuer implements AccessTokenIssuer {
  private final JwtSigningKeyProvider keys;
  private final JwtProperties properties;

  public String issue(UUID id, UUID sid, Instant now) {
    return Jwts.builder()
        .header()
        .keyId(keys.kid())
        .type("at+jwt")
        .and()
        .issuer(properties.getIssuer())
        .subject(id.toString())
        .audience()
        .add(properties.getAudience())
        .and()
        .id(UUID.randomUUID().toString())
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plusSeconds(900)))
        .claim("sid", sid.toString())
        .signWith(keys.getKey(), Jwts.SIG.RS256)
        .compact();
  }
}
