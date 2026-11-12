package br.com.imarui.ima.authentication.feature;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.http.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
    "security.auth.require-https=true",
    "server.forward-headers-strategy=native",
    "server.tomcat.remoteip.protocol-header=x-forwarded-proto",
    "server.tomcat.remoteip.remote-ip-header=x-forwarded-for",
    "server.tomcat.remoteip.internal-proxies=127.0.0.1/32"
})
class TrustedProxyHttpsIntegrationTest extends BrowserSecurityIntegrationSupport {
  @Test
  void trustedProxyMustIndicateHttps() throws Exception {
    assertEquals(202, request("https", true));
    assertEquals(400, request("http", true));
    assertEquals(400, request(null, true));
    assertEquals(400, request(null, false));
  }

  @Test
  void completeBrowserFlowRequiresTrustedHttpsAndCsrf() throws Exception {
    var csrfResponse = request("GET", "/authentication/csrf", null, null, null, "https", null);
    assertEquals(200, csrfResponse.statusCode());
    String csrfCookie = cookie(csrfResponse, "XSRF-TOKEN", "Lax");
    String csrf = json.readTree(csrfResponse.body()).path("token").asText();
    assertFalse(csrf.isBlank());
    assertEquals(403, request("POST", "/authentication/login", LOGIN, csrfCookie, null, "https", null).statusCode());
    assertEquals(403, request("POST", "/authentication/login", LOGIN, csrfCookie, "invalid", "https", null).statusCode());
    assertEquals(400, request("POST", "/authentication/login", LOGIN, csrfCookie, csrf, "http", null).statusCode());
    assertEquals(400, request("POST", "/authentication/login", LOGIN, csrfCookie, csrf, null, null).statusCode());
    var login = request("POST", "/authentication/login", LOGIN, csrfCookie, csrf, "https", null);
    accessTokenOnly(login);
    String refresh = cookie(login, "IIAM_REFRESH", "Lax");
    String cookies = csrfCookie + "; " + refresh;
    assertEquals(403, request("POST", "/authentication/refresh", "{}", cookies, null, "https", null).statusCode());
    assertEquals(403, request("POST", "/authentication/refresh", "{}", cookies, "invalid", "https", null).statusCode());
    assertEquals(403, request("POST", "/authentication/logout", "{}", cookies, "invalid", "https", null).statusCode());
    assertEquals(400, request("POST", "/authentication/refresh", "{}", cookies, csrf, "http", null).statusCode());
    assertEquals(400, request("POST", "/authentication/refresh", "{}", cookies, csrf, null, null).statusCode());
    assertEquals(400, request("POST", "/authentication/logout", "{}", cookies, csrf, "http", null).statusCode());
    assertEquals(400, request("POST", "/authentication/logout", "{}", cookies, csrf, null, null).statusCode());
    var rotated = request("POST", "/authentication/refresh", "{}", cookies, csrf, "https", null);
    accessTokenOnly(rotated);
    String access = json.readTree(rotated.body()).path("accessToken").asText();
    assertEquals(200, sessions(access));
    String replacement = cookie(rotated, "IIAM_REFRESH", "Lax");
    assertNotEquals(refresh, replacement);
    var logout = request("POST", "/authentication/logout", "{}", csrfCookie + "; " + replacement, csrf, "https", null);
    assertEquals(204, logout.statusCode());
    assertTrue(logout.headers().firstValue("Set-Cookie").orElseThrow().contains("Max-Age=0"));
    cookie(logout, "IIAM_REFRESH", "Lax");
    assertEquals(401, sessions(access));
    assertEquals(401, request("POST", "/authentication/refresh", "{}", csrfCookie + "; " + replacement, csrf, "https", null).statusCode());
    assertEquals(401, request("POST", "/authentication/refresh", "{}", cookies, csrf, "https", null).statusCode());
  }

  private int sessions(String access) throws Exception {
    var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/authentication/sessions"))
        .header("Authorization", "Bearer " + access).GET().build();
    return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString()).statusCode();
  }

  private int request(String proto, boolean forwarded) throws Exception {
    var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/authentication/activation/request"))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString("{\"identifier\":\"proxy-test@example.test\"}"));
    if (forwarded) builder.header("X-Forwarded-For", "198.51.100.10");
    if (proto != null) builder.header("X-Forwarded-Proto", proto);
    return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString()).statusCode();
  }
}
