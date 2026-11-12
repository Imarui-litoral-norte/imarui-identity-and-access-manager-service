package br.com.imarui.ima.authentication.feature;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "security.auth.cookie-same-site=None")
class CrossSiteCookieIntegrationTest extends BrowserSecurityIntegrationSupport {
  @Test
  void crossSiteLoginRotatesAndClearsCookiesWithExplicitCors() throws Exception {
    String origin = "https://frontend.example.test";
    var csrfResponse = request("GET", "/authentication/csrf", null, null, null, null, origin);
    assertEquals(200, csrfResponse.statusCode());
    assertEquals(origin, csrfResponse.headers().firstValue("Access-Control-Allow-Origin").orElseThrow());
    assertEquals("true", csrfResponse.headers().firstValue("Access-Control-Allow-Credentials").orElseThrow());
    String csrfCookie = cookie(csrfResponse, "XSRF-TOKEN", "None");
    String csrf = json.readTree(csrfResponse.body()).path("token").asText();
    assertFalse(csrf.isBlank());
    var rejected = request("GET", "/authentication/csrf", null, null, null, null, "https://untrusted.example.test");
    assertEquals(403, rejected.statusCode());
    assertTrue(rejected.headers().firstValue("Access-Control-Allow-Origin").isEmpty());
    var login = request("POST", "/authentication/login", LOGIN, csrfCookie, csrf, null, origin);
    accessTokenOnly(login);
    assertEquals(origin, login.headers().firstValue("Access-Control-Allow-Origin").orElseThrow());
    String refresh = cookie(login, "IIAM_REFRESH", "None");
    var rotated = request("POST", "/authentication/refresh", "{}", csrfCookie + "; " + refresh, csrf, null, origin);
    accessTokenOnly(rotated);
    String replacement = cookie(rotated, "IIAM_REFRESH", "None");
    assertNotEquals(refresh, replacement);
    var logout = request("POST", "/authentication/logout", "{}", csrfCookie + "; " + replacement, csrf, null, origin);
    assertEquals(204, logout.statusCode());
    assertTrue(logout.headers().firstValue("Set-Cookie").orElseThrow().contains("Max-Age=0"));
    cookie(logout, "IIAM_REFRESH", "None");
    assertEquals(401, request("POST", "/authentication/refresh", "{}", csrfCookie + "; " + refresh, csrf, null, origin).statusCode());
    assertEquals(401, request("POST", "/authentication/refresh", "{}", csrfCookie + "; " + replacement, csrf, null, origin).statusCode());
    // Replay can revoke the entire session; use a fresh session to test it before logout.
    var anotherLogin = request("POST", "/authentication/login", LOGIN, csrfCookie, csrf, null, origin);
    accessTokenOnly(anotherLogin);
    String old = cookie(anotherLogin, "IIAM_REFRESH", "None");
    var next = request("POST", "/authentication/refresh", "{}", csrfCookie + "; " + old, csrf, null, origin);
    accessTokenOnly(next);
    assertNotEquals(old, cookie(next, "IIAM_REFRESH", "None"));
    assertEquals(401, request("POST", "/authentication/refresh", "{}", csrfCookie + "; " + old, csrf, null, origin).statusCode());
  }
}
