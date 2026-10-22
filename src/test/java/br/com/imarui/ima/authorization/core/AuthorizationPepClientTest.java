package br.com.imarui.ima.authorization.core;

import static org.junit.jupiter.api.Assertions.*;
import br.com.imarui.ima.authorization.api.pep.AuthorizationPepClient;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.RSAPrivateKey;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AuthorizationPepClientTest {
  private static RSAPrivateKey key;
  private HttpServer server;
  private ExecutorService executor;
  private volatile int status;
  private volatile String body;
  private volatile long delay;
  @BeforeAll static void key() throws Exception { var g=KeyPairGenerator.getInstance("RSA"); g.initialize(2048); key=(RSAPrivateKey)g.generateKeyPair().getPrivate(); }
  @BeforeEach void server() throws Exception {
    status=200; body="{}"; delay=0; executor=Executors.newFixedThreadPool(2);
    server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0); server.setExecutor(executor);
    server.createContext("/api/v1/authorization/service/decisions",exchange -> {
      try {
        exchange.getRequestBody().readAllBytes();
        if(delay>0) try { Thread.sleep(delay); } catch(InterruptedException e) { Thread.currentThread().interrupt(); return; }
        var bytes=body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type","application/json");
        exchange.getResponseHeaders().set("Location","http://127.0.0.1:1/never-forward-credentials");
        exchange.sendResponseHeaders(status,bytes.length); exchange.getResponseBody().write(bytes);
      } finally { exchange.close(); }
    }); server.start();
  }
  @AfterEach void close() { server.stop(0); executor.shutdownNow(); }
  private AuthorizationPepClient client(long timeout) {
    return new AuthorizationPepClient(URI.create("http://127.0.0.1:"+server.getAddress().getPort()),"test-service","test-audience:services",
        UUID.randomUUID(),key,id -> new AuthorizationPepClient.Resource("TENANT",UUID.randomUUID().toString(),null),Duration.ofMillis(timeout),Clock.systemUTC());
  }
  @ParameterizedTest @CsvSource({"401,UNAUTHENTICATED","403,FORBIDDEN","404,NOT_FOUND","429,RATE_LIMITED","503,UNAVAILABLE"})
  void upstreamFailuresNeverGrant(int httpStatus,AuthorizationPepClient.Failure failure) {
    status=httpStatus;
    var result=client(1000).check("opaque test actor token",UUID.randomUUID(),"test.read","order",UUID.randomUUID());
    assertFalse(result.allowed()); assertEquals(failure,result.failure());
  }
  @Test void aRealHttpTimeoutFailsClosedWithoutRetry() {
    delay=300;
    var result=client(50).check("opaque test actor token",UUID.randomUUID(),"test.read","order",UUID.randomUUID());
    assertFalse(result.allowed()); assertEquals(AuthorizationPepClient.Failure.UNAVAILABLE,result.failure());
  }
  @Test void malformedOrMismatchedAllowCannotBeTrusted() {
    var correlation=UUID.randomUUID(); body="{\"allowed\":true}";
    assertEquals(AuthorizationPepClient.Failure.INTEGRATION_ERROR,client(1000).check("test",UUID.randomUUID(),"test.read","order",correlation).failure());
    body="{\"allowed\":true,\"reasonCode\":\"ALLOW\",\"decisionId\":\""+UUID.randomUUID()+"\",\"correlationId\":\""+UUID.randomUUID()+"\",\"evaluatedAt\":\""+Instant.now()+"\"}";
    assertFalse(client(1000).check("test",UUID.randomUUID(),"test.read","order",correlation).allowed());
  }
  @Test void redirectsAreRejectedRatherThanForwardingTechnicalAndHumanCredentials() {
    status=302;
    var result=client(1000).check("test",UUID.randomUUID(),"test.read","order",UUID.randomUUID());
    assertFalse(result.allowed()); assertEquals(AuthorizationPepClient.Failure.INTEGRATION_ERROR,result.failure());
  }
  @Test void cleartextRemoteEndpointsAreRejectedBeforeNetworkAccess() {
    assertThrows(IllegalArgumentException.class,() -> new AuthorizationPepClient(URI.create("http://iam.example.test"),"test-service","aud",
        UUID.randomUUID(),key,id -> null,Duration.ofSeconds(1),Clock.systemUTC()));
  }
}
