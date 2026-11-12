package br.com.imarui.ima.authentication.feature;

import static org.junit.jupiter.api.Assertions.*;

import br.com.imarui.ima.identity.support.IdentityIntegrationSupport;
import java.net.URI;
import java.net.http.*;

abstract class BrowserSecurityIntegrationSupport extends IdentityIntegrationSupport {
  static final String LOGIN = "{\"identifier\":\"reviewer\",\"password\":\"Fixture password 123!\"}";

  HttpResponse<String> request(String method, String path, String body, String cookies,
      String csrf, String proto, String origin) throws Exception {
    var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
    if (body != null) builder.header("Content-Type", "application/json");
    if (cookies != null) builder.header("Cookie", cookies);
    if (csrf != null) builder.header("X-XSRF-TOKEN", csrf);
    if (proto != null) {
      builder.header("X-Forwarded-For", "198.51.100.10");
      builder.header("X-Forwarded-Proto", proto);
    }
    if (origin != null) builder.header("Origin", origin);
    builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
    return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }

  String cookie(HttpResponse<String> response, String name, String sameSite) {
    String value = response.headers().allValues("Set-Cookie").stream()
        .filter(c -> c.startsWith(name + "=")).findFirst().orElseThrow();
    assertTrue(value.contains("Secure"));
    assertTrue(value.contains("HttpOnly"));
    assertTrue(value.contains("SameSite=" + sameSite));
    assertTrue(value.contains("Path=/authentication"));
    assertFalse(value.contains("Domain="));
    return value.split(";", 2)[0];
  }

  void accessTokenOnly(HttpResponse<String> response) {
    assertEquals(200, response.statusCode(), response.body());
    var body = json.readTree(response.body());
    String token = body.path("accessToken").asText();
    assertFalse(token.isBlank());
    var claims = io.jsonwebtoken.Jwts.parser().verifyWith(keys.publicKeys().get(keys.kid()))
        .build().parseSignedClaims(token).getPayload();
    assertEquals(reviewer.getId().toString(), claims.getSubject());
    assertFalse(body.has("refreshToken"));
  }
}
