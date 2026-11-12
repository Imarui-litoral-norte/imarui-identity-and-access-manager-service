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
    "server.tomcat.remoteip.internal-proxies=127.0.0.1/32"
})
class TrustedProxyHttpsIntegrationTest extends IdentityIntegrationSupport {
  @Test
  void trustedProxyMustIndicateHttps() throws Exception {
    assertEquals(202, request("https", true));
    assertEquals(400, request("http", true));
    assertEquals(400, request(null, true));
    assertEquals(400, request(null, false));
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
