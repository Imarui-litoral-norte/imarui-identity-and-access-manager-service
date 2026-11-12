package br.com.imarui.ima.authentication.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;

import br.com.imarui.ima.identity.support.IdentityIntegrationSupport;
import java.net.URI;
import java.net.http.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
    "security.auth.require-https=true",
    "server.forward-headers-strategy=native",
    "server.tomcat.remoteip.protocol-header=x-forwarded-proto",
    "server.tomcat.remoteip.remote-ip-header=x-forwarded-for",
    "server.tomcat.remoteip.internal-proxies=192.0.2.1/32"
})
class UntrustedProxyHttpsIntegrationTest extends IdentityIntegrationSupport {
  @Test
  void untrustedPeerCannotSpoofHttps() throws Exception {
    var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/authentication/activation/request"))
        .header("Content-Type", "application/json")
        .header("X-Forwarded-For", "198.51.100.10")
        .header("X-Forwarded-Proto", "https")
        .POST(HttpRequest.BodyPublishers.ofString("{\"identifier\":\"proxy-test@example.test\"}"))
        .build();
    assertEquals(400, HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString()).statusCode());
    for (boolean contradictory : new boolean[] {false, true}) {
      var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/authentication/activation/request"))
          .header("Content-Type", "application/json")
          .header("Forwarded", "for=198.51.100.10;proto=https")
          .POST(HttpRequest.BodyPublishers.ofString("{\"identifier\":\"proxy-test@example.test\"}"));
      if (contradictory) builder.header("X-Forwarded-For", "198.51.100.10").header("X-Forwarded-Proto", "http");
      assertEquals(400, HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString()).statusCode());
    }
    var reverse = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/authentication/activation/request"))
        .header("Content-Type", "application/json").header("Forwarded", "for=198.51.100.10;proto=http")
        .header("X-Forwarded-For", "198.51.100.10").header("X-Forwarded-Proto", "https")
        .POST(HttpRequest.BodyPublishers.ofString("{\"identifier\":\"proxy-test@example.test\"}")).build();
    assertEquals(400, HttpClient.newHttpClient().send(reverse, HttpResponse.BodyHandlers.ofString()).statusCode());
  }
}
