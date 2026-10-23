package br.com.imarui.ima.authorization.api.pep;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.RSAPrivateKey;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.nio.ByteBuffer;
import tools.jackson.databind.json.JsonMapper;

/** Reference backend PEP. The resolver receives only a resource key, never a frontend Scope. */
public final class AuthorizationPepClient {
  public enum Failure { NONE, DENIED, UNAUTHENTICATED, FORBIDDEN, NOT_FOUND, UNAVAILABLE, RATE_LIMITED, INTEGRATION_ERROR }
  public record Resource(String type,String id,UUID expectedScopeId) {}
  @FunctionalInterface public interface TrustedResourceResolver { Resource resolve(String resourceId); }
  public record Decision(boolean allowed,Failure failure,UUID decisionId,UUID correlationId) {}
  private final URI endpoint;
  private final String service,audience;
  private final UUID credentialId;
  private final RSAPrivateKey key;
  private final TrustedResourceResolver resolver;
  private final Duration timeout;
  private final Clock clock;
  private final HttpClient http;
  private final JsonMapper json=new JsonMapper();

  public AuthorizationPepClient(URI origin,String service,String audience,UUID credentialId,RSAPrivateKey key,
      TrustedResourceResolver resolver,Duration timeout,Clock clock) {
    if(origin==null || origin.getHost()==null || origin.getUserInfo()!=null || origin.getQuery()!=null || origin.getFragment()!=null
        || !("https".equals(origin.getScheme()) || "http".equals(origin.getScheme())
          && Set.of("localhost","127.0.0.1","[::1]","::1").contains(origin.getHost())))
      throw new IllegalArgumentException("Use HTTPS; HTTP is allowed only for loopback integration tests.");
    if(service==null || !service.matches("[a-z][a-z0-9-]{1,49}") || audience==null || audience.isBlank()
        || key==null || key.getModulus().bitLength()<2048 || timeout==null
        || timeout.toMillis()<50 || timeout.compareTo(Duration.ofSeconds(5))>0) throw new IllegalArgumentException("Invalid PEP security configuration.");
    this.endpoint=origin.resolve("/api/v1/authorization/service/decisions");
    this.service=service; this.audience=audience; this.credentialId=Objects.requireNonNull(credentialId);
    this.key=key; this.resolver=Objects.requireNonNull(resolver); this.timeout=timeout; this.clock=Objects.requireNonNull(clock);
    this.http=HttpClient.newBuilder().connectTimeout(timeout).followRedirects(HttpClient.Redirect.NEVER).build();
  }

  public Decision check(String actorToken,UUID identity,String permission,String resourceId,UUID correlation) {
    Objects.requireNonNull(correlation);
    try {
      var resource=Objects.requireNonNull(resolver.resolve(resourceId));
      if(resource.type()==null || resource.id()==null || resource.id().isBlank()) throw new IllegalArgumentException("Invalid trusted resource.");
      var body=new LinkedHashMap<String,Object>();
      body.put("actorToken",actorToken); body.put("identityId",identity); body.put("permission",permission);
      body.put("resourceType",resource.type()); body.put("resourceId",resource.id()); body.put("expectedScopeId",resource.expectedScopeId());
      body.put("correlationId",correlation);
      byte[] bytes=json.writeValueAsString(body).getBytes(StandardCharsets.UTF_8);
      if(bytes.length>65536) return deny(Failure.INTEGRATION_ERROR,correlation);
      String assertion=proof(bytes,correlation);
      var pending=http.sendAsync(HttpRequest.newBuilder(endpoint).timeout(timeout).header("Content-Type","application/json")
          .header("Cache-Control","no-store")
          .header("Authorization","Service "+assertion).POST(HttpRequest.BodyPublishers.ofByteArray(bytes)).build(),info -> boundedBody());
      HttpResponse<byte[]> response;
      try { response=pending.get(timeout.toNanos(),TimeUnit.NANOSECONDS); }
      finally { if(!pending.isDone()) pending.cancel(true); }
      int status=response.statusCode();
      if(status==401) return deny(Failure.UNAUTHENTICATED,correlation);
      if(status==404) return deny(Failure.NOT_FOUND,correlation);
      if(status==429) return deny(Failure.RATE_LIMITED,correlation);
      if(status>=500) return deny(Failure.UNAVAILABLE,correlation);
      if(status!=200 && status!=403) return deny(Failure.INTEGRATION_ERROR,correlation);
      if(!response.headers().firstValue("Content-Type").orElse("").contains("json")) return deny(Failure.INTEGRATION_ERROR,correlation);
      var result=json.readTree(response.body());
      if(status==403 && !result.has("decisionId")) return deny(Failure.FORBIDDEN,correlation);
      if(!result.path("allowed").isBoolean() || !correlation.toString().equals(result.path("correlationId").asText()))
        return deny(Failure.INTEGRATION_ERROR,correlation);
      var id=UUID.fromString(result.path("decisionId").asText());
      Instant.parse(result.path("evaluatedAt").asText());
      boolean allowed=result.path("allowed").asBoolean();
      if(allowed && status==200 && "ALLOW".equals(result.path("reasonCode").asText())) return new Decision(true,Failure.NONE,id,correlation);
      if(!allowed && status==403 && "DENY".equals(result.path("reasonCode").asText())) return new Decision(false,Failure.DENIED,id,correlation);
      return deny(Failure.INTEGRATION_ERROR,correlation);
    } catch(NoSuchElementException unknown) { return deny(Failure.NOT_FOUND,correlation); }
      catch(TimeoutException unavailable) { return deny(Failure.UNAVAILABLE,correlation); }
      catch(ExecutionException unavailable) {
        var cause=unavailable.getCause();
        while(cause instanceof CompletionException && cause.getCause()!=null) cause=cause.getCause();
        return deny(cause instanceof OversizedResponse?Failure.INTEGRATION_ERROR:Failure.UNAVAILABLE,correlation);
      }
      catch(InterruptedException interrupted) { Thread.currentThread().interrupt(); return deny(Failure.UNAVAILABLE,correlation); }
      catch(Exception invalid) { return deny(Failure.INTEGRATION_ERROR,correlation); }
  }

  private static final class OversizedResponse extends java.io.IOException {}
  /** Cancel on the wire, before buffering an unbounded body. The future deadline covers all chunks. */
  private static HttpResponse.BodySubscriber<byte[]> boundedBody() {
    var delegate=HttpResponse.BodySubscribers.ofByteArray();
    return new HttpResponse.BodySubscriber<>() {
      private Flow.Subscription subscription;
      private long received;
      private boolean terminated;
      public CompletionStage<byte[]> getBody() { return delegate.getBody(); }
      public void onSubscribe(Flow.Subscription subscription) { this.subscription=subscription; delegate.onSubscribe(subscription); }
      public void onNext(List<ByteBuffer> chunks) {
        if(terminated) return;
        for(var chunk:chunks) received+=chunk.remaining();
        if(received>16384) { terminated=true; subscription.cancel(); delegate.onError(new OversizedResponse()); }
        else delegate.onNext(chunks);
      }
      public void onError(Throwable error) { if(!terminated) { terminated=true; delegate.onError(error); } }
      public void onComplete() { if(!terminated) { terminated=true; delegate.onComplete(); } }
    };
  }

  private String proof(byte[] body,UUID correlation) throws Exception {
    var now=clock.instant();
    String digest=Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(body));
    var claims=new JWTClaimsSet.Builder().issuer(service).subject(service).audience(audience)
        .issueTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(60))).jwtID(UUID.randomUUID().toString())
        .claim("htm","POST").claim("htu",endpoint.getPath()).claim("body_sha256",digest).claim("correlation_id",correlation.toString()).build();
    var jwt=new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).type(new JOSEObjectType("iiam-service+jwt")).keyID(credentialId.toString()).build(),claims);
    jwt.sign(new RSASSASigner(key)); return jwt.serialize();
  }
  private Decision deny(Failure failure,UUID correlation) { return new Decision(false,failure,null,correlation); }
}
