package br.com.imarui.ima.authorization.infra;

import static org.junit.jupiter.api.Assertions.*;
import br.com.imarui.ima.authentication.api.module.TechnicalServiceAuthentication;
import br.com.imarui.ima.authorization.core.application.command.PolicyChangeReason;
import br.com.imarui.ima.authorization.core.application.usecase.*;
import br.com.imarui.ima.authorization.core.domain.model.*;
import br.com.imarui.ima.authorization.core.repository.ScopeRepository;
import br.com.imarui.ima.identity.core.domain.model.identity.Identity;
import br.com.imarui.ima.identity.support.IdentityIntegrationSupport;
import br.com.imarui.ima.platform.security.jwt.JwtProperties;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.*;
import java.net.URI;
import java.net.http.*;
import java.security.*;
import java.security.interfaces.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

class AuthorizationServiceIntegrationTest extends IdentityIntegrationSupport {
  @Autowired TechnicalClientAdministrationUseCases clients;
  @Autowired TechnicalServiceAuthentication technicalAuthentication;
  @Autowired CatalogOwnerAdministrationUseCases owners;
  @Autowired RoleAdministrationUseCases roles;
  @Autowired DenyDelegationUseCases denies;
  @Autowired ScopeRepository scopes;
  @Autowired ScopeAdministrationUseCases hierarchy;
  @Autowired JwtProperties jwtProperties;
  private static KeyPair pair;
  private String service;
  private TechnicalServiceAuthentication.Client client;
  private UUID keyId;
  private RSAPrivateKey signingKey;
  private static final UUID PLATFORM=AuthorizationScope.PLATFORM_ID;
  private static final PolicyChangeReason REASON=new PolicyChangeReason("Approved service security setup",UUID.fromString("00000000-0000-0000-0000-000000000088"));
  private static final String PDP="/api/v1/authorization/service/decisions";
  private static final String CATALOG="/api/v1/authorization/service/catalog";

  @BeforeAll static void signingKey() throws Exception { var gen=KeyPairGenerator.getInstance("RSA"); gen.initialize(2048); pair=gen.generateKeyPair(); }
  @BeforeEach void setupService() {
    new TransactionTemplate(transactions).executeWithoutResult(s -> {
      jdbc.update("delete from authentication_bootstrap where name='initial-root'");
      jdbc.update("insert into authentication_bootstrap values('initial-root',?,current_timestamp)",reviewer.getId().value());
      rootAuthorization.assign(reviewer.getId().value());
    });
    actor(reviewer.getId()); service="svc-"+UUID.randomUUID().toString().replace("-","");
    owners.enroll(service,service,REASON);
    client=clients.enroll(service,Set.of(TechnicalServiceAuthentication.Operation.PDP_DECIDE,TechnicalServiceAuthentication.Operation.CATALOG_SYNC),
        Set.of(PLATFORM),Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()),fixtureClock.instant().plusSeconds(3600),REASON);
    keyId=client.keys().get(0).id();
    signingKey=(RSAPrivateKey)pair.getPrivate();
  }
  @AfterEach void marker() { jdbc.update("delete from authentication_bootstrap where name='initial-root'"); }
  private UUID scope() { return scopes.tenantScope(tenant.getId().value()).orElseThrow(); }
  private String permission() { return service+".resource.read"; }
  private String manifest(long version) { return json.writeValueAsString(new PermissionManifest(service,service,version,
      List.of(new PermissionManifest.Entry(permission(),"Resource read","resources",false)))); }
  private String token(Identity identity) {
    return new TransactionTemplate(transactions).execute(s -> {
      var now=fixtureClock.instant(); var session=authenticationStore.createSession(identity.getId().value(),now);
      return tokenIssuer.issue(identity.getId().value(),session.getPublicId(),now);
    });
  }
  private Identity user() {
    var id=identities.save(service(UUID.randomUUID()+"@example.test")); firstAccess(id.getId());
    return identities.findById(id.getId()).orElseThrow();
  }
  private Identity granted() throws Exception {
    assertEquals(200,send(CATALOG,manifest(1),null).statusCode());
    denies.bootstrapCatalogAuthority(permission(),PLATFORM,REASON);
    var user=user();
    var role=roles.create("Service reader "+UUID.randomUUID(),Role.Ownership.GLOBAL,null,null,Set.of(permission()),REASON);
    roles.assign(user.getId().value(),role.id(),scope(),REASON); return user;
  }
  private String request(Identity actor,String token,UUID expectedScope) {
    return json.writeValueAsString(new br.com.imarui.ima.authorization.api.http.ServiceAuthorizationController.DecisionRequest(
        token,actor.getId().value(),permission(),"TENANT",tenant.getId().value().toString(),expectedScope,UUID.randomUUID()));
  }
  private String proof(String path,String body) throws Exception { return proof(path,body,service,jwtProperties.getAudience()+":services",fixtureClock.instant()); }
  private String proof(String path,String body,String issuer,String audience,Instant now) throws Exception {
    var claims=new JWTClaimsSet.Builder().issuer(issuer).subject(service).audience(audience)
        .issueTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(60))).jwtID(UUID.randomUUID().toString())
        .claim("htm","POST").claim("htu",path)
        .claim("body_sha256",br.com.imarui.ima.authentication.infra.security.TechnicalServiceAuthenticationAdapter.digest(body.getBytes(java.nio.charset.StandardCharsets.UTF_8))).build();
    var jwt=new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).type(new JOSEObjectType("iiam-service+jwt")).keyID(keyId.toString()).build(),claims);
    jwt.sign(new RSASSASigner(signingKey)); return jwt.serialize();
  }
  private HttpResponse<String> send(String path,String body,String assertion) throws Exception {
    return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+path))
        .timeout(Duration.ofSeconds(10)).header("Content-Type","application/json")
        .header("Authorization","Service "+(assertion==null?proof(path,body):assertion))
        .POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
  }

  @Test void secureCatalogIsIdempotentOwnedAndDoesNotGrantAccess() throws Exception {
    var body=manifest(1);
    assertEquals(200,send(CATALOG,body,null).statusCode());
    assertEquals("{\"changed\":false}",send(CATALOG,body,null).body());
    assertEquals(0,jdbc.queryForObject("select count(*) from authorization_role_permissions rp join authorization_permissions p on p.id=rp.permission_id where p.code=?",Integer.class,permission()));
    var wrong=json.writeValueAsString(new PermissionManifest("another-service","another-service",1,List.of()));
    assertEquals(403,send(CATALOG,wrong,null).statusCode());
    assertEquals(1,jdbc.queryForObject("select version from authorization_catalog_owners where service_code=?",Long.class,service));
  }
  @Test void replayAndBodyMethodPathTamperingAreRejected() throws Exception {
    var body=manifest(1); var assertion=proof(CATALOG,body);
    assertEquals(200,send(CATALOG,body,assertion).statusCode());
    assertEquals(401,send(CATALOG,body,assertion).statusCode());
    assertEquals(401,send(CATALOG,manifest(2),proof(CATALOG,body)).statusCode());
    assertEquals(401,send(CATALOG,body,proof(PDP,body)).statusCode());
  }
  @Test void invalidIssuerAudienceExpiredProofAndUnknownKeyFailClosed() throws Exception {
    var body=manifest(1); var now=fixtureClock.instant();
    assertEquals(401,send(CATALOG,body,proof(CATALOG,body,"spoofed",jwtProperties.getAudience()+":services",now)).statusCode());
    assertEquals(401,send(CATALOG,body,proof(CATALOG,body,service,"wrong-audience",now)).statusCode());
    assertEquals(401,send(CATALOG,body,proof(CATALOG,body,service,jwtProperties.getAudience()+":services",now.minusSeconds(120))).statusCode());
    keyId=UUID.randomUUID(); assertEquals(401,send(CATALOG,body,null).statusCode());
  }
  @Test void humanTokenAloneCannotAuthenticateATechnicalCaller() throws Exception {
    var response=http("POST",CATALOG,manifest(1),token(reviewer));
    assertEquals(401,response.statusCode());
    assertTrue(response.body().contains("INVALID_TECHNICAL_AUTHENTICATION"),response.body());
    assertEquals("Service",response.headers().firstValue("WWW-Authenticate").orElseThrow());
    assertEquals(401,http("POST",CATALOG,manifest(1),null).statusCode());
  }
  @Test void serviceOperationAndCredentialRevocationAreImmediate() throws Exception {
    var body=manifest(1);
    clients.configure(service,true,Set.of(TechnicalServiceAuthentication.Operation.PDP_DECIDE),Set.of(PLATFORM),client.version(),REASON);
    assertEquals(Set.of(TechnicalServiceAuthentication.Operation.PDP_DECIDE),technicalAuthentication.client(service).operations());
    var response=send(CATALOG,body,null);
    assertEquals(403,response.statusCode(),response.body());
    clients.revoke(service,keyId,REASON);
    assertEquals(401,send(CATALOG,body,null).statusCode());
  }
  @Test void rotationSupportsOverlapThenRevokesOnlyTheOldKey() throws Exception {
    var gen=KeyPairGenerator.getInstance("RSA"); gen.initialize(2048); var next=gen.generateKeyPair();
    var rotated=clients.rotate(service,Base64.getEncoder().encodeToString(next.getPublic().getEncoded()),fixtureClock.instant().plusSeconds(3600),REASON);
    assertEquals(200,send(CATALOG,manifest(1),null).statusCode());
    clients.revoke(service,keyId,REASON);
    assertTrue(rotated.active()); assertEquals(401,send(CATALOG,manifest(1),null).statusCode());
    keyId=rotated.id(); signingKey=(RSAPrivateKey)next.getPrivate();
    assertEquals(200,send(CATALOG,manifest(1),null).statusCode());
  }
  @Test void pdpUsesTheLiveHumanSessionAndHidesPolicyProvenance() throws Exception {
    var user=granted(); var jwt=token(user); var body=request(user,jwt,scope());
    var response=send(PDP,body,null); assertEquals(200,response.statusCode(),response.body());
    var result=json.readTree(response.body()); assertTrue(result.path("allowed").asBoolean()); assertEquals("ALLOW",result.path("reasonCode").asText());
    assertFalse(response.body().contains("roleId")); assertFalse(response.body().contains(jwt));
    var deny=denies.deny(permission(),scope(),user.getId().value(),null,REASON);
    assertEquals(403,send(PDP,body,null).statusCode());
    denies.revokeDeny(deny.id(),REASON); assertEquals(200,send(PDP,body,null).statusCode());
    jdbc.update("update authentication_sessions set session_status='REVOKED',revoked_at=current_timestamp where identity_id=?",user.getId().value());
    assertEquals(401,send(PDP,body,null).statusCode());
  }
  @Test void actorSpoofingAndScopeSpoofingAreRejected() throws Exception {
    var user=granted(); var other=user(); var jwt=token(user);
    assertEquals(401,send(PDP,request(other,jwt,scope()),null).statusCode());
    assertEquals(403,send(PDP,request(user,jwt,PLATFORM),null).statusCode());
  }
  @Test void technicalJurisdictionAndPermissionOwnerAreEnforced() throws Exception {
    var user=granted(); var jwt=token(user);
    var restricted=hierarchy.create("RESTRICTED_"+UUID.randomUUID().toString().replace("-","").toUpperCase(),"Other jurisdiction",PLATFORM,REASON);
    clients.configure(service,true,Set.of(TechnicalServiceAuthentication.Operation.PDP_DECIDE),Set.of(restricted.id()),client.version(),REASON);
    assertEquals(403,send(PDP,request(user,jwt,scope()),null).statusCode());
    clients.configure(service,true,Set.of(TechnicalServiceAuthentication.Operation.PDP_DECIDE),Set.of(PLATFORM),client.version()+1,REASON);
    var foreign=json.writeValueAsString(new br.com.imarui.ima.authorization.api.http.ServiceAuthorizationController.DecisionRequest(
        jwt,user.getId().value(),"IDENTITY_READ","TENANT",tenant.getId().value().toString(),scope(),UUID.randomUUID()));
    assertEquals(403,send(PDP,foreign,null).statusCode());
  }
  @Test void archivedRoleAndInactiveTenantAffectAnExistingJwt() throws Exception {
    var user=granted(); var jwt=token(user); var body=request(user,jwt,scope());
    assertEquals(200,send(PDP,body,null).statusCode());
    long role=jdbc.queryForObject("select role_id from authorization_identity_roles where identity_id=?",Long.class,user.getId().value());
    String name=jdbc.queryForObject("select name from authorization_roles where id=?",String.class,role);
    roles.revise(role,name,Role.Status.INACTIVE,Set.of(permission()),REASON); assertEquals(403,send(PDP,body,null).statusCode());
    roles.revise(role,name,Role.Status.ACTIVE,Set.of(permission()),REASON);
    disableTenant.execute(new br.com.imarui.ima.identity.core.application.command.tenant.DisableTenantCommand(tenant.getId()));
    assertEquals(403,send(PDP,body,null).statusCode());
  }
  @Test void auditFailureNeverReturnsAnAllowAndRecordsNoToken() throws Exception {
    var user=granted(); var jwt=token(user); var body=request(user,jwt,scope());
    jdbc.execute("create function test_pdp_audit_failure() returns trigger language plpgsql as $$ begin if new.event='PDP_DECISION' then raise exception 'fixture audit failure'; end if; return new; end $$");
    jdbc.execute("create trigger test_pdp_audit_failure before insert on authorization_security_events for each row execute function test_pdp_audit_failure()");
    try { assertEquals(503,send(PDP,body,null).statusCode()); }
    finally { jdbc.execute("drop trigger test_pdp_audit_failure on authorization_security_events"); jdbc.execute("drop function test_pdp_audit_failure()"); }
    assertEquals(200,send(PDP,body,null).statusCode());
    assertEquals(0,jdbc.queryForObject("select count(*) from authorization_security_events where details::text like ?",Integer.class,"%"+jwt+"%"));
  }
}
