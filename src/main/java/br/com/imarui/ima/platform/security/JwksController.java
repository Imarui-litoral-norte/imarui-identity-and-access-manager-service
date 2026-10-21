package br.com.imarui.ima.platform.security;

import br.com.imarui.ima.platform.security.jwt.JwtSigningKeyProvider;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class JwksController {
  private final JwtSigningKeyProvider keys;

  @GetMapping("/.well-known/jwks.json")
  @io.swagger.v3.oas.annotations.Operation(summary = "Chaves públicas de verificação RS256")
  public ResponseEntity<Map<String, Object>> keys() {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.maxAge(java.time.Duration.ofMinutes(5)))
        .body(keys.jwks().toJSONObject());
  }
}
