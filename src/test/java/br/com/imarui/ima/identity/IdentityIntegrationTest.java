package br.com.imarui.ima.identity;

import br.com.imarui.ima.identity.core.application.command.identity.*;
import br.com.imarui.ima.identity.core.application.command.identityapplication.*;
import br.com.imarui.ima.identity.core.application.command.tenant.*;
import br.com.imarui.ima.identity.core.application.usecase.identity.*;
import br.com.imarui.ima.identity.core.application.usecase.identityapplication.*;
import br.com.imarui.ima.identity.core.application.usecase.tenant.*;
import br.com.imarui.ima.identity.core.application.exception.*;
import br.com.imarui.ima.identity.core.domain.enums.identity.*;
import br.com.imarui.ima.identity.core.domain.enums.affiliation.*;
import br.com.imarui.ima.identity.core.domain.model.identity.*;
import br.com.imarui.ima.identity.core.domain.model.identity.person.*;
import br.com.imarui.ima.identity.core.domain.model.identity.LegalEntity.*;
import br.com.imarui.ima.identity.core.domain.model.identity.service.*;
import br.com.imarui.ima.identity.core.domain.model.identityapplication.*;
import br.com.imarui.ima.identity.core.domain.model.affiliation.*;
import br.com.imarui.ima.identity.core.domain.model.tenant.*;
import br.com.imarui.ima.identity.core.repository.*;
import br.com.imarui.ima.identity.infra.persistence.jpa.*;
import br.com.imarui.ima.platform.security.jwt.JwtSigningKeyProvider;
import io.jsonwebtoken.Jwts;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.net.URI;
import java.net.http.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class IdentityIntegrationTest {
    @Autowired IdentityRepository identities;
    @Autowired TenantRepository tenants;
    @Autowired AffiliationRepository affiliations;
    @Autowired IdentityApplicationRepository applications;
    @Autowired IdentityHistoryJpaRepository history;
    @Autowired AccessRevocationRequestJpaRepository revocations;
    @Autowired CreateIdentityApplicationUseCase create;
    @Autowired ApproveIdentityApplicationUseCase approve;
    @Autowired RejectIdentityApplicationUseCase reject;
    @Autowired GetIdentityApplicationUseCase get;
    @Autowired ListIdentityApplicationsUseCase list;
    @Autowired SelfDeleteIdentityUseCase delete;
    @Autowired ActivateIdentityUseCase activate;
    @Autowired DisableIdentityUseCase disable;
    @Autowired ReactivateIdentityUseCase reactivate;
    @Autowired DisableTenantUseCase disableTenant;
    @Autowired ReactivateTenantUseCase reactivateTenant;
    @Autowired JdbcTemplate jdbc;
    @Autowired JsonMapper json;
    @Autowired JwtSigningKeyProvider keys;
    @Autowired PlatformTransactionManager transactions;
    @LocalServerPort int port;
    Identity reviewer;
    Tenant tenant;
    final Instant earlier = Instant.now().minusSeconds(60);

    @BeforeEach void prepare() {
        jdbc.execute("truncate ima_identities, ima_tenants, ima_identity_applications, ima_identities__history, ima_access_revocation_requests cascade");
        reviewer = service("reviewer@example.test"); reviewer.activate(earlier);
        reviewer = identities.save(reviewer);
        tenant = Tenant.create(TenantId.generate(), TenantCode.from("TEST"), TenantName.from("Test tenant"), earlier);
        tenant.activate(earlier); tenant = tenants.save(tenant);
        actor(reviewer.getId());
    }
    @AfterEach void clearActor() { SecurityContextHolder.clearContext(); }
    void actor(IdentityId id) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(id.toString(), null, "IDENTITY_MANAGE"));
    }
    Identity service(String email) {
        return ServiceIdentity.create(IdentityId.generate(), IdentityEmail.create(Email.from(email), EmailType.PRIMARY, earlier), ServiceName.from("Test service"), ServiceDescription.from("Service details"), earlier);
    }
    Identity sample(IdentityKind kind, String email) {
        var primary = IdentityEmail.create(Email.from(email), EmailType.PRIMARY, earlier);
        return switch (kind) {
            case PERSON -> PersonIdentity.create(IdentityId.generate(), primary, IdentityFullName.from("Test Person"), Cpf.from("52998224725"), LocalDate.of(1990, 5, 10), IdentityPhoneNumber.create(PhoneNumber.from("48999999999"), earlier), earlier);
            case LEGAL_ENTITY -> LegalEntityIdentity.create(IdentityId.generate(), primary, LegalName.from("Test Company Ltda"), TradeName.from("Test Company"), Cnpj.from("11222333000181"), earlier);
            case SERVICE -> service(email);
        };
    }
    IdentityApplicationId request(IdentityKind kind, String email) {
        CreateIdentityApplicationCommand command = switch (kind) {
            case PERSON -> new CreatePersonIdentityApplicationCommand("Test Person", "52998224725", email, LocalDate.of(1990,5,10), "48999999999");
            case LEGAL_ENTITY -> new CreateLegalEntityIdentityApplicationCommand("Test Company Ltda", "Test Company", "11222333000181", email);
            case SERVICE -> new CreateServiceIdentityApplicationCommand("Test service", "Service details", email);
        };
        return IdentityApplicationId.from(create.execute(command).id());
    }
    ApproveIdentityApplicationCommand approval(IdentityApplicationId id, AffiliationType type, String code) {
        return new ApproveIdentityApplicationCommand(id, tenant.getId(), new AffiliationSelection(type,
            type == AffiliationType.EMPLOYEE ? code : null,
            type == AffiliationType.CUSTOMER ? code : null,
            type == AffiliationType.SUPPLIER ? code : null));
    }
    @ParameterizedTest @EnumSource(IdentityKind.class)
    void approvalPersistsCorrectKindAndReviewerAndCannotResolveTwice(IdentityKind kind) {
        var id = request(kind, "new@example.test");
        var result = approve.execute(approval(id, AffiliationType.EXTERNAL, null));
        assertNotNull(result.reviewedAt()); assertEquals(reviewer.getId().value(), result.reviewedBy());
        assertEquals(tenant.getId().value(), result.assignedTenantId());
        var identity = identities.findById(IdentityId.from(result.resolvedIdentityId())).orElseThrow();
        assertEquals(kind, identity.getKind()); assertTrue(identity.isPending());
        var affiliation = affiliations.findById(AffiliationId.from(result.resultingAffiliationId())).orElseThrow();
        assertEquals(identity.getId(), affiliation.getIdentityId());
        assertThrows(br.com.imarui.ima.identity.core.domain.exception.identityapplication.IdentityApplicationReviewNotAllowedException.class, () -> approve.execute(approval(id, AffiliationType.EXTERNAL, null)));
        assertThrows(br.com.imarui.ima.identity.core.domain.exception.identityapplication.IdentityApplicationReviewNotAllowedException.class, () -> reject.execute(new RejectIdentityApplicationCommand(id, "Rejected")));
    }
    @ParameterizedTest @EnumSource(AffiliationType.class)
    void approvalSupportsEachAffiliationSpecialization(AffiliationType type) {
        var id = request(IdentityKind.SERVICE, "affiliation@example.test");
        var result = approve.execute(approval(id, type, "CODE-1"));
        var affiliation = affiliations.findById(AffiliationId.from(result.resultingAffiliationId())).orElseThrow();
        assertEquals(type, affiliation.getType());
        var reconstructed = affiliations.save(affiliation);
        assertEquals(type, reconstructed.getType());
        if (type == AffiliationType.EMPLOYEE) assertEquals("CODE-1", ((br.com.imarui.ima.identity.core.domain.model.affiliation.employee.EmployeeAffiliation) reconstructed).getRegistration().value());
        if (type == AffiliationType.CUSTOMER) assertEquals("CODE-1", ((br.com.imarui.ima.identity.core.domain.model.affiliation.customer.CustomerAffiliation) reconstructed).getCustomerCode().value());
        if (type == AffiliationType.SUPPLIER) assertEquals("CODE-1", ((br.com.imarui.ima.identity.core.domain.model.affiliation.supplier.SupplierAffiliation) reconstructed).getSupplierCode().value());
    }
    @Test void rejectionIsAuditedAndDoesNotProvisionIdentity() {
        var id = request(IdentityKind.SERVICE, "rejected@example.test");
        assertThrows(br.com.imarui.ima.identity.core.domain.exception.identityapplication.InvalidIdentityApplicationStateException.class,
            () -> reject.execute(new RejectIdentityApplicationCommand(id, "  ")));
        var result = reject.execute(new RejectIdentityApplicationCommand(id, " Insufficient data "));
        assertEquals(reviewer.getId().value(), result.reviewedBy()); assertEquals("Insufficient data", result.rejectionReason());
        assertNull(result.resolvedIdentityId()); assertEquals(1, jdbc.queryForObject("select count(*) from ima_identities", Integer.class));
        assertEquals(1, list.execute().size()); assertEquals(result, get.execute(id));
        assertThrows(br.com.imarui.ima.identity.core.domain.exception.identityapplication.IdentityApplicationReviewNotAllowedException.class,
            () -> reject.execute(new RejectIdentityApplicationCommand(id, "Again")));
    }
    @Test void missingActorCannotResolveOrDelete() {
        var id = request(IdentityKind.SERVICE, "pending@example.test");
        SecurityContextHolder.clearContext();
        assertThrows(CurrentActorUnavailableException.class, () -> approve.execute(approval(id, AffiliationType.EXTERNAL, null)));
        assertThrows(CurrentActorUnavailableException.class, () -> reject.execute(new RejectIdentityApplicationCommand(id,"No")));
        assertThrows(CurrentActorUnavailableException.class, () -> delete.execute(new SelfDeleteIdentityCommand(null)));
        assertTrue(applications.findById(id).orElseThrow().isPending());
    }
    @ParameterizedTest @EnumSource(IdentityKind.class)
    void duplicatesAreBlockedBothBeforeAndAfterApplication(IdentityKind kind) {
        var id = request(kind,"duplicate@example.test");
        assertThrows(IdentityConflictException.class, () -> request(kind,"duplicate@example.test"));
        if (kind != IdentityKind.SERVICE) assertThrows(IdentityConflictException.class, () -> request(kind,"different-email@example.test"));
        var existing = sample(kind, "duplicate@example.test"); existing.activate(earlier); existing.disable(earlier);
        var identity = identities.save(existing);
        assertThrows(IdentityConflictException.class, () -> approve.execute(approval(id, AffiliationType.EXTERNAL,null)));
        assertTrue(applications.findById(id).orElseThrow().isPending());
        assertTrue(identities.findById(identity.getId()).isPresent());
    }
    @Test void sameEmailIsAllowedAcrossKindsAndRejectionReleasesPendingKey() {
        var service = request(IdentityKind.SERVICE, "shared@example.test");
        var company = request(IdentityKind.LEGAL_ENTITY, "shared@example.test");
        approve.execute(approval(service, AffiliationType.PARTNER,null));
        approve.execute(approval(company, AffiliationType.EXTERNAL,null));
        var rejected = request(IdentityKind.SERVICE,"reusable@example.test");
        reject.execute(new RejectIdentityApplicationCommand(rejected,"No"));
        assertNotNull(request(IdentityKind.SERVICE,"reusable@example.test"));
    }
    @Test void disabledTenantBlocksApprovalWithoutPartialIdentity() {
        var id = request(IdentityKind.SERVICE,"tenant@example.test");
        disableTenant.execute(new DisableTenantCommand(tenant.getId()));
        assertThrows(TenantUnavailableException.class, () -> approve.execute(approval(id, AffiliationType.EXTERNAL,null)));
        assertEquals(1, jdbc.queryForObject("select count(*) from ima_identities", Integer.class));
        assertTrue(applications.findById(id).orElseThrow().isPending());
        assertEquals("TENANT", revocations.findAll().get(0).getScope());
    }
    @Test void scopedCodesAreUniqueOnlyWithinTenantAndApprovalRollsBack() {
        var first = request(IdentityKind.SERVICE,"first@example.test");
        approve.execute(approval(first, AffiliationType.EMPLOYEE,"EMP-1"));
        var second = request(IdentityKind.SERVICE,"second@example.test");
        assertThrows(IdentityConflictException.class, () -> approve.execute(approval(second, AffiliationType.EMPLOYEE,"EMP-1")));
        assertTrue(applications.findById(second).orElseThrow().isPending());
        assertEquals(2, jdbc.queryForObject("select count(*) from ima_identities", Integer.class));
        var other = Tenant.create(TenantId.generate(),TenantCode.from("OTHER"),TenantName.from("Other tenant"),earlier); other.activate(earlier); other = tenants.save(other);
        var selection = approval(second,AffiliationType.EMPLOYEE,"EMP-1").affiliation();
        assertNotNull(approve.execute(new ApproveIdentityApplicationCommand(second,other.getId(),selection)).resultingAffiliationId());
    }
    @ParameterizedTest @EnumSource(IdentityKind.class)
    void selfDeleteCommitsFullSnapshotRemovesJoinedRowsAndPermitsReregistration(IdentityKind kind) {
        var identity = sample(kind,"deleted@example.test"); identity.activate(earlier); identity = identities.save(identity);
        affiliations.save(ExternalAffiliation.create(AffiliationId.generate(),tenant.getId(),identity.getId(),earlier));
        actor(identity.getId()); delete.execute(new SelfDeleteIdentityCommand("Requested by owner"));
        assertTrue(identities.findById(identity.getId()).isEmpty());
        assertEquals(0, jdbc.queryForObject("select count(*) from ima_affiliations where identity_id = ?",Integer.class,identity.getId().value()));
        var archived = history.findAll().get(0);
        assertEquals(identity.getId().value(),archived.getOriginalIdentityId()); assertEquals(kind.name(),archived.getKind());
        assertEquals("ACTIVE",archived.getPreviousStatus()); assertEquals(identity.getId().value(),archived.getActorId());
        assertEquals("deleted@example.test",json.readTree(archived.getSnapshot()).path("primaryEmail").path("email").asText());
        String specific = switch(kind) { case PERSON -> "cpf"; case LEGAL_ENTITY -> "cnpj"; case SERVICE -> "name"; };
        assertFalse(json.readTree(archived.getSnapshot()).path(specific).isMissingNode());
        assertEquals(1,revocations.count());
        String table = switch(kind) { case PERSON -> "ima_person_identities"; case LEGAL_ENTITY -> "ima_legal_entity_identities"; case SERVICE -> "ima_service_identities"; };
        assertEquals(0,jdbc.queryForObject("select count(*) from "+table+" where id = ?",Integer.class,identity.getId().value()));
        assertNotNull(identities.save(sample(kind,"deleted@example.test")));
    }
    @Test void failingHistoryInsertRollsBackDeletionAndRevocationRequest() {
        var id = reviewer.getId();
        jdbc.execute("create function fail_identity_history() returns trigger language plpgsql as $$ begin raise exception 'simulated history failure'; end $$");
        jdbc.execute("create trigger fail_identity_history before insert on ima_identities__history for each row execute function fail_identity_history()");
        try { assertThrows(RuntimeException.class, () -> delete.execute(new SelfDeleteIdentityCommand(null))); }
        finally { jdbc.execute("drop trigger fail_identity_history on ima_identities__history"); jdbc.execute("drop function fail_identity_history()"); }
        assertTrue(identities.findById(id).isPresent()); assertEquals(0,history.count()); assertEquals(0,revocations.count());
    }
    @Test void failingOperationalDeleteRollsBackAlreadyWrittenHistory() {
        var id = reviewer.getId();
        jdbc.execute("create function fail_identity_delete() returns trigger language plpgsql as $$ begin raise exception 'simulated delete failure'; end $$");
        jdbc.execute("create trigger fail_identity_delete before delete on ima_identities for each row execute function fail_identity_delete()");
        try { assertThrows(RuntimeException.class, () -> delete.execute(new SelfDeleteIdentityCommand(null))); }
        finally { jdbc.execute("drop trigger fail_identity_delete on ima_identities"); jdbc.execute("drop function fail_identity_delete()"); }
        assertTrue(identities.findById(id).isPresent()); assertEquals(0,history.count()); assertEquals(0,revocations.count());
    }
    @Test void failingRevocationRollsBackIdentityDisable() {
        jdbc.execute("create function fail_revocation() returns trigger language plpgsql as $$ begin raise exception 'simulated revocation failure'; end $$");
        jdbc.execute("create trigger fail_revocation before insert on ima_access_revocation_requests for each row execute function fail_revocation()");
        try { assertThrows(RuntimeException.class, () -> disable.execute(new DisableIdentityCommand(reviewer.getId()))); }
        finally { jdbc.execute("drop trigger fail_revocation on ima_access_revocation_requests"); jdbc.execute("drop function fail_revocation()"); }
        assertTrue(identities.findById(reviewer.getId()).orElseThrow().isActive()); assertEquals(0,revocations.count());
    }
    @Test void lifecycleQueriesHonorTenantAndIdentityWithoutMutatingAffiliation() {
        var affiliation = affiliations.save(ExternalAffiliation.create(AffiliationId.generate(),tenant.getId(),reviewer.getId(),earlier));
        assertEquals(1,affiliations.findAllActive().size());
        disableTenant.execute(new DisableTenantCommand(tenant.getId()));
        assertTrue(affiliations.findAllActive().isEmpty()); assertTrue(affiliations.findById(affiliation.getId()).orElseThrow().isActive());
        reactivateTenant.execute(new ReactivateTenantCommand(tenant.getId()));
        assertEquals(1,affiliations.findByTenantId(tenant.getId()).size());
        affiliation.deactivate(Instant.now()); affiliations.save(affiliation);
        disableTenant.execute(new DisableTenantCommand(tenant.getId())); reactivateTenant.execute(new ReactivateTenantCommand(tenant.getId()));
        assertTrue(affiliations.findByIdentityId(reviewer.getId()).isEmpty());
        assertTrue(affiliations.findById(affiliation.getId()).orElseThrow().isInactive());
        disable.execute(new DisableIdentityCommand(reviewer.getId())); reactivate.execute(new ReactivateIdentityCommand(reviewer.getId()));
        assertEquals(3,revocations.count());
    }
    @Test void concurrentDuplicateSubmissionsHaveOneWinner() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        Callable<Boolean> submit = () -> { start.await(); try { request(IdentityKind.SERVICE,"race@example.test"); return true; } catch (IdentityConflictException exception) { return false; } };
        try {
            var first=pool.submit(submit); var second=pool.submit(submit); start.countDown();
            assertNotEquals(first.get(15,TimeUnit.SECONDS),second.get(15,TimeUnit.SECONDS));
            assertEquals(1,jdbc.queryForObject("select count(*) from ima_identity_applications",Integer.class));
        } finally { pool.shutdownNow(); }
    }
    @Test void concurrentApprovalCanResolveOnlyOnce() throws Exception {
        var id=request(IdentityKind.SERVICE,"approval-race@example.test"); var actorId=reviewer.getId();
        var pool=Executors.newFixedThreadPool(2); var start=new CountDownLatch(1);
        Callable<Boolean> task=() -> { start.await(); actor(actorId); try { approve.execute(approval(id,AffiliationType.EXTERNAL,null)); return true; }
            catch (br.com.imarui.ima.identity.core.domain.exception.identityapplication.IdentityApplicationReviewNotAllowedException exception) { return false; }
            finally { SecurityContextHolder.clearContext(); } };
        try { var first=pool.submit(task); var second=pool.submit(task); start.countDown(); assertNotEquals(first.get(15,TimeUnit.SECONDS),second.get(15,TimeUnit.SECONDS));
            assertEquals(2,jdbc.queryForObject("select count(*) from ima_identities",Integer.class));
        } finally { pool.shutdownNow(); }
    }

    @Test void oldIdentityTokenRemainsRevokedAfterReactivation() throws Exception {
        var old=token(reviewer.getId(),"IDENTITY_READ");
        disable.execute(new DisableIdentityCommand(reviewer.getId()));
        reactivate.execute(new ReactivateIdentityCommand(reviewer.getId()));
        assertEquals(401,http("GET","/api/v1/identities/"+reviewer.getId(),null,old).statusCode());
    }
    @Test void scopedTokenRemainsRevokedAfterTenantReactivation() throws Exception {
        affiliations.save(ExternalAffiliation.create(AffiliationId.generate(),tenant.getId(),reviewer.getId(),earlier));
        var scoped=Jwts.builder().subject(reviewer.getId().toString()).issuer("identity-test").issuedAt(new Date())
            .expiration(Date.from(Instant.now().plusSeconds(120))).claim("tenant_id",tenant.getId().toString())
            .claim("permissions",List.of("IDENTITY_READ")).signWith(keys.getKey(),Jwts.SIG.HS256).compact();
        assertEquals(200,http("GET","/api/v1/identities/"+reviewer.getId(),null,scoped).statusCode());
        disableTenant.execute(new DisableTenantCommand(tenant.getId())); reactivateTenant.execute(new ReactivateTenantCommand(tenant.getId()));
        assertEquals(401,http("GET","/api/v1/identities/"+reviewer.getId(),null,scoped).statusCode());
    }
    @Test void documentedOpenApiContainsActualResponsesAndSecurity() throws Exception {
        var response=http("GET","/v3/api-docs",null,null); assertEquals(200,response.statusCode(),response.body());
        var spec=json.readTree(response.body());
        assertTrue(spec.path("paths").path("/api/v1/identities/me").path("delete").path("responses").has("204"));
        assertTrue(spec.path("paths").path("/api/v1/identity-applications").path("post").path("responses").has("201"));
        assertTrue(spec.path("components").path("schemas").has("PersonIdentityDetailsResponse"));
        assertTrue(spec.path("components").path("securitySchemes").has("bearerAuth"));
    }
    @Test void httpValidationConflictAndAdministrativePermissionAreEnforced() throws Exception {
        var body="{\"type\":\"SERVICE\",\"name\":\"HTTP service\",\"email\":\"duplicate-http@example.test\"}";
        assertEquals(201,http("POST","/api/v1/identity-applications",body,null).statusCode());
        var conflict=http("POST","/api/v1/identity-applications",body,null);
        assertEquals(409,conflict.statusCode(),conflict.body());
        assertEquals("IDENTITY_CONFLICT",json.readTree(conflict.body()).path("code").asText());
        var invalid=http("POST","/api/v1/identity-applications","{\"type\":\"SERVICE\",\"name\":\"\",\"email\":\"bad\"}",null);
        assertEquals(400,invalid.statusCode(),invalid.body());
        assertTrue(invalid.headers().firstValue("Content-Type").orElse("").startsWith("application/problem+json"));
        var denied=http("POST","/api/v1/admin/identities/"+reviewer.getId()+"/disable",null,token(reviewer.getId()));
        assertEquals(403,denied.statusCode(),denied.body()); assertTrue(identities.findById(reviewer.getId()).orElseThrow().isActive());
    }


    @Test void unknownTenantDuringApprovalReturnsNotFoundAndNoPartialWrites() throws Exception {
        var id=request(IdentityKind.SERVICE,"unknown-tenant@example.test");
        var response=http("POST","/api/v1/admin/identity-applications/"+id.value()+"/approve",
            "{\"tenantId\":\""+UUID.randomUUID()+"\",\"affiliation\":{\"type\":\"EXTERNAL\"}}",token(reviewer.getId(),"IDENTITY_APPLICATION_REVIEW"));
        assertEquals(404,response.statusCode(),response.body());
        assertEquals("TENANT_NOT_FOUND",json.readTree(response.body()).path("code").asText());
        assertTrue(applications.findById(id).orElseThrow().isPending());
        assertEquals(1,jdbc.queryForObject("select count(*) from ima_identities",Integer.class));
    }

    String token(IdentityId identity, String... permissions) {
        return Jwts.builder().subject(identity.toString()).issuer("identity-test")
            .issuedAt(new Date()).expiration(Date.from(Instant.now().plusSeconds(120)))
            .claim("permissions", List.of(permissions)).signWith(keys.getKey(),Jwts.SIG.HS256).compact();
    }
    HttpResponse<String> http(String method, String path, String body, String token) throws Exception {
        var builder=HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).header("Content-Type","application/json");
        if(token!=null) builder.header("Authorization","Bearer "+token);
        return HttpClient.newHttpClient().send(builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
    @ParameterizedTest @EnumSource(IdentityKind.class)
    void httpResponseDiscriminatesKindsAndProtectsOtherIdentities(IdentityKind kind) throws Exception {
        var identity=sample(kind,"http@example.test"); identity.activate(earlier); identity=identities.save(identity);
        var response=http("GET","/api/v1/identities/"+identity.getId(),null,token(reviewer.getId(),"IDENTITY_READ"));
        assertEquals(200,response.statusCode(),response.body());
        assertEquals(kind.name(),json.readTree(response.body()).path("kind").asText());
        assertTrue(json.readTree(response.body()).path("details").isObject());
        assertEquals(403,http("GET","/api/v1/identities/"+identity.getId(),null,token(reviewer.getId())).statusCode());
    }
    @Test void httpOnboardingLifecycleErrorsAndSelfDeleteAreFunctional() throws Exception {
        var admin=token(reviewer.getId(),"IDENTITY_APPLICATION_REVIEW","IDENTITY_MANAGE","IDENTITY_READ");
        var created=http("POST","/api/v1/identity-applications","{\"type\":\"SERVICE\",\"name\":\"HTTP service\",\"email\":\"http-created@example.test\"}",null);
        assertEquals(201,created.statusCode(),created.body()); assertTrue(created.headers().firstValue("Location").isPresent());
        var applicationId=json.readTree(created.body()).path("id").asText();
        var approved=http("POST","/api/v1/admin/identity-applications/"+applicationId+"/approve","{\"tenantId\":\""+tenant.getId()+"\",\"affiliation\":{\"type\":\"EXTERNAL\"}}",admin);
        assertEquals(200,approved.statusCode(),approved.body());
        var identityId=IdentityId.from(json.readTree(approved.body()).path("resolvedIdentityId").asText());
        var activation=http("POST","/api/v1/admin/identities/"+identityId+"/activate",null,admin);
        assertEquals(200,activation.statusCode(),activation.body());
        var conflict=http("POST","/api/v1/admin/identities/"+identityId+"/activate",null,admin);
        assertEquals(409,conflict.statusCode(),conflict.body()); assertTrue(conflict.headers().firstValue("Content-Type").orElse("").startsWith("application/problem+json"));
        assertEquals(404,http("GET","/api/v1/identities/"+UUID.randomUUID(),null,admin).statusCode());
        assertEquals(400,http("GET","/api/v1/identities/not-a-uuid",null,admin).statusCode());
        assertEquals(401,http("DELETE","/api/v1/identities/me",null,null).statusCode());
        assertEquals(204,http("DELETE","/api/v1/identities/me","{\"reason\":\"Owner request\"}",token(identityId)).statusCode());
        assertTrue(identities.findById(identityId).isEmpty()); assertEquals(1,history.count());
        assertEquals(401,http("GET","/api/v1/identities/"+identityId,null,token(identityId)).statusCode());
    }
}
