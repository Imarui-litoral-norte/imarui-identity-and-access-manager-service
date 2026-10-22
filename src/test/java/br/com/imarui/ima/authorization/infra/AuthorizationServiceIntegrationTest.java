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
  @Autowired PdpUseCases pdp;
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
        .claim("correlation_id",json.readTree(body).hasNonNull("correlationId")?json.readTree(body).path("correlationId").asText():UUID.randomUUID().toString())
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
    var changed=json.writeValueAsString(new PermissionManifest(service,service,1,List.of(new PermissionManifest.Entry(permission(),"Changed metadata","resources",false))));
    assertEquals(409,send(CATALOG,changed,null).statusCode());
  }
  @Test void catalogInspectionRequiresAuditAuthorityAndPreservesManifestRevisions() throws Exception {
    assertEquals(200,send(CATALOG,manifest(1),null).statusCode());
    String base="/api/v1/authorization/admin/catalog/owners/"+service;
    String rootToken=token(reviewer);
    var owner=http("GET",base,null,rootToken); assertEquals(200,owner.statusCode(),owner.body());
    assertEquals(service,json.readTree(owner.body()).path("serviceCode").asText());
    var revisions=http("GET",base+"/revisions",null,rootToken); assertEquals(200,revisions.statusCode(),revisions.body());
    assertEquals(1,json.readTree(revisions.body()).path("total").asInt());
    var revision=http("GET",base+"/revisions/1",null,rootToken); assertEquals(200,revision.statusCode(),revision.body());
    assertEquals(permission(),json.readTree(revision.body()).path("permissions").get(0).path("code").asText());
    assertEquals(403,http("GET",base,null,token(user())).statusCode());
    assertEquals(404,http("GET",base+"/revisions/999",null,rootToken).statusCode());
  }
  @Test void technicalJsonRequiresUtf8AndRejectsContentEncodingBeforeConsumingProof() throws Exception {
    var body=manifest(1); var assertion=proof(CATALOG,body);
    var response=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+CATALOG))
        .header("Content-Type","application/json;charset=ISO-8859-1").header("Authorization","Service "+assertion)
        .POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    assertEquals(415,response.statusCode(),response.body());
    assertEquals(200,send(CATALOG,body,assertion).statusCode());
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
  @Test void correlationAndTenConcurrentDecisionsRemainBoundAndDoNotExhaustTheConnectionPool() throws Exception {
    var user=granted(); var jwt=token(user); var body=request(user,jwt,scope()); var correlation=json.readTree(body).path("correlationId").asText();
    var pool=java.util.concurrent.Executors.newFixedThreadPool(10); var barrier=new java.util.concurrent.CyclicBarrier(10);
    try {
      var tasks=new ArrayList<java.util.concurrent.Future<Integer>>();
      for(int i=0;i<10;i++) tasks.add(pool.submit(() -> {
        barrier.await(10,java.util.concurrent.TimeUnit.SECONDS);
        var response=send(PDP,body,null);
        assertEquals(correlation,response.headers().firstValue("X-Correlation-ID").orElseThrow());
        assertEquals(correlation,json.readTree(response.body()).path("correlationId").asText());
        return response.statusCode();
      }));
      for(var task:tasks) assertEquals(200,task.get(25,java.util.concurrent.TimeUnit.SECONDS));
    } finally { pool.shutdownNow(); }
    assertEquals(20,jdbc.queryForObject("select count(*) from authorization_security_events where correlation_id=? and service_code=? and event in ('M2M_AUTHENTICATION','PDP_DECISION')",
        Integer.class,UUID.fromString(correlation),service));
  }
  @Test void simultaneousReplayHasOnlyOneAuthenticatedWinner() throws Exception {
    var body=manifest(1); var assertion=proof(CATALOG,body); var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
    var barrier=new java.util.concurrent.CyclicBarrier(2);
    try {
      java.util.concurrent.Callable<Integer> action=() -> { barrier.await(10,java.util.concurrent.TimeUnit.SECONDS); return send(CATALOG,body,assertion).statusCode(); };
      var a=pool.submit(action); var c=pool.submit(action);
      assertEquals(Set.of(200,401),Set.of(a.get(20,java.util.concurrent.TimeUnit.SECONDS),c.get(20,java.util.concurrent.TimeUnit.SECONDS)));
    } finally { pool.shutdownNow(); }
  }
  @Test void batchUsesOneActorAndOneCurrentPolicySnapshot() throws Exception {
    var user=granted(); var jwt=token(user); var correlation=UUID.randomUUID();
    var item=new br.com.imarui.ima.authorization.api.http.ServiceAuthorizationController.DecisionRequest(jwt,user.getId().value(),permission(),"TENANT",tenant.getId().value().toString(),scope(),correlation);
    var body=json.writeValueAsString(new br.com.imarui.ima.authorization.api.http.ServiceAuthorizationController.Batch(correlation,List.of(item,item)));
    var path=PDP+"/batch"; var response=send(path,body,null); assertEquals(200,response.statusCode(),response.body());
    assertEquals(2,json.readTree(response.body()).size());
    assertTrue(json.readTree(response.body()).get(0).path("allowed").asBoolean());
    var forged=new br.com.imarui.ima.authorization.api.http.ServiceAuthorizationController.DecisionRequest(jwt,reviewer.getId().value(),permission(),"TENANT",tenant.getId().value().toString(),scope(),correlation);
    var wrong=json.writeValueAsString(new br.com.imarui.ima.authorization.api.http.ServiceAuthorizationController.Batch(correlation,List.of(item,forged)));
    assertEquals(400,send(path,wrong,null).statusCode());
  }
  @Test void realConsumerResolvesItsResourceServerSideAndFailsClosedForTheSiblingTenant() throws Exception {
    var user=granted(); var jwt=token(user);
    var other=br.com.imarui.ima.identity.core.domain.model.tenant.Tenant.create(
        br.com.imarui.ima.identity.core.domain.model.tenant.TenantId.generate(),
        br.com.imarui.ima.identity.core.domain.model.tenant.TenantCode.from("PEP_"+UUID.randomUUID().toString().substring(0,8)),
        br.com.imarui.ima.identity.core.domain.model.tenant.TenantName.from("Other company"),fixtureClock.instant());
    other.activate(fixtureClock.instant()); var sibling=tenants.save(other);
    var pep=new br.com.imarui.ima.authorization.api.pep.AuthorizationPepClient(URI.create("http://localhost:"+port),service,
        jwtProperties.getAudience()+":services",keyId,signingKey,key -> switch(key) {
          case "owned-order" -> new br.com.imarui.ima.authorization.api.pep.AuthorizationPepClient.Resource("TENANT",tenant.getId().value().toString(),scope());
          case "sibling-order" -> new br.com.imarui.ima.authorization.api.pep.AuthorizationPepClient.Resource("TENANT",sibling.getId().value().toString(),null);
          default -> throw new NoSuchElementException();
        },Duration.ofSeconds(2),fixtureClock);
    assertTrue(pep.check(jwt,user.getId().value(),permission(),"owned-order",UUID.randomUUID()).allowed());
    assertFalse(pep.check(jwt,user.getId().value(),permission(),"sibling-order",UUID.randomUUID()).allowed());
    denies.deny(permission(),scope(),user.getId().value(),null,REASON);
    assertFalse(pep.check(jwt,user.getId().value(),permission(),"owned-order",UUID.randomUUID()).allowed());
  }
  @Test void roleCompositionRevocationArchiveAndHierarchyMovesAffectTheSameJwt() throws Exception {
    var user=granted(); var jwt=token(user); var body=request(user,jwt,scope());
    long role=jdbc.queryForObject("select role_id from authorization_identity_roles where identity_id=?",Long.class,user.getId().value());
    var name=jdbc.queryForObject("select name from authorization_roles where id=?",String.class,role);
    roles.revise(role,name,true,Set.of(),REASON); assertEquals(403,send(PDP,body,null).statusCode());
    roles.revise(role,name,true,Set.of(permission()),REASON); assertEquals(200,send(PDP,body,null).statusCode());
    var assignment=jdbc.queryForObject("select id from authorization_identity_roles where identity_id=?",UUID.class,user.getId().value());
    roles.revokeAssignment(assignment,REASON); roles.revise(role,name,Role.Status.ARCHIVED,Set.of(permission()),REASON);
    assertEquals(403,send(PDP,body,null).statusCode());
    var a=hierarchy.create("A_"+UUID.randomUUID().toString().replace("-","").toUpperCase(),"First region",PLATFORM,REASON);
    var c=hierarchy.create("C_"+UUID.randomUUID().toString().replace("-","").toUpperCase(),"Other region",PLATFORM,REASON);
    var resource=scopes.find(scope()).orElseThrow(); hierarchy.revise(resource.id(),resource.name(),resource.status(),a.id(),REASON);
    var regional=roles.create("Regional "+UUID.randomUUID(),Role.Ownership.SCOPE,a.id(),null,Set.of(permission()),REASON);
    roles.assign(user.getId().value(),regional.id(),a.id(),REASON); assertEquals(200,send(PDP,body,null).statusCode());
    hierarchy.revise(resource.id(),resource.name(),resource.status(),c.id(),REASON);
    assertEquals(403,send(PDP,body,null).statusCode());
  }
  @Test void authenticatedProofCannotUseAuthorityRevokedBeforeTheDecisionSnapshot() throws Exception {
    var user=granted(); var jwt=token(user); var body=request(user,jwt,scope());
    var proof=technicalAuthentication.verify(proof(PDP,body),"POST",PDP,body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    clients.revoke(service,keyId,REASON);
    var context=org.springframework.security.core.context.SecurityContextHolder.getContext();
    var previous=context.getAuthentication();
    context.setAuthentication(org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(proof,null,List.of()));
    try {
      var command=new br.com.imarui.ima.authorization.core.application.usecase.PdpUseCases.Request(jwt,user.getId().value(),permission(),"TENANT",tenant.getId().value().toString(),scope(),UUID.fromString(json.readTree(body).path("correlationId").asText()));
      assertThrows(br.com.imarui.ima.authentication.core.exception.TechnicalAuthenticationException.class,() -> pdp.decide(command));
    } finally { context.setAuthentication(previous); }
  }
  @Test void technicalMetadataIsProtectedAndNeverReturnsKeyMaterial() throws Exception {
    var response=http("GET","/api/v1/authorization/admin/service-clients/"+service,null,token(reviewer));
    assertEquals(200,response.statusCode(),response.body());
    assertFalse(response.body().contains(Base64.getEncoder().encodeToString(pair.getPublic().getEncoded())));
    assertTrue(response.body().contains(keyId.toString()));
    assertEquals(200,http("GET","/api/v1/authorization/admin/service-clients?size=2",null,token(reviewer)).statusCode());
    assertEquals(403,http("GET","/api/v1/authorization/admin/service-clients/"+service,null,token(user())).statusCode());
  }
}
