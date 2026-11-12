package br.com.imarui.ima.authentication.feature;

import static org.junit.jupiter.api.Assertions.*;

import br.com.imarui.ima.identity.support.IdentityIntegrationSupport;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "security.auth.cookie-same-site=None")
class CrossSiteCookieIntegrationTest extends IdentityIntegrationSupport {
  @Test
  void csrfAndRefreshCookiesShareCrossSitePolicy() throws Exception {
    var csrf = http("GET", "/authentication/csrf", null, null);
    String csrfCookie = csrf.headers().firstValue("Set-Cookie").orElseThrow();
    assertTrue(csrfCookie.contains("SameSite=None"));
    assertTrue(csrfCookie.contains("Secure"));
    assertTrue(csrfCookie.contains("HttpOnly"));
    assertTrue(csrfCookie.contains("Path=/authentication"));
    String token = json.readTree(csrf.body()).path("token").asText();
    var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/authentication/logout"))
        .header("Cookie", csrfCookie.split(";", 2)[0])
        .header("X-XSRF-TOKEN", token)
        .POST(HttpRequest.BodyPublishers.noBody()).build();
    var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    assertEquals(204, response.statusCode());
    String refreshCookie = response.headers().firstValue("Set-Cookie").orElseThrow();
    assertTrue(refreshCookie.contains("SameSite=None"));
    assertTrue(refreshCookie.contains("Secure"));
    assertTrue(refreshCookie.contains("HttpOnly"));
    assertTrue(refreshCookie.contains("Path=/authentication"));
  }
}
