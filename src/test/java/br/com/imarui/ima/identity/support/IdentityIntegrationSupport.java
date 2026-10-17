package br.com.imarui.ima.identity.support;

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
public abstract class IdentityIntegrationSupport {
    @Autowired protected IdentityRepository identities;
    @Autowired protected TenantRepository tenants;
    @Autowired protected AffiliationRepository affiliations;
    @Autowired protected IdentityApplicationRepository applications;
    @Autowired protected IdentityHistoryJpaRepository history;
    @Autowired protected AffiliationHistoryJpaRepository affiliationHistory;
    @Autowired protected AccessRevocationRequestJpaRepository revocations;
    @Autowired protected CreateIdentityApplicationUseCase create;
    @Autowired protected ApproveIdentityApplicationUseCase approve;
    @Autowired protected RejectIdentityApplicationUseCase reject;
    @Autowired protected GetIdentityApplicationUseCase get;
    @Autowired protected ListIdentityApplicationsUseCase list;
    @Autowired protected SelfDeleteIdentityUseCase delete;
    @Autowired protected ActivateIdentityUseCase activate;
    @Autowired protected DisableIdentityUseCase disable;
    @Autowired protected ReactivateIdentityUseCase reactivate;
    @Autowired protected DisableTenantUseCase disableTenant;
    @Autowired protected ReactivateTenantUseCase reactivateTenant;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected JsonMapper json;
    @Autowired protected JwtSigningKeyProvider keys;
    @Autowired protected PlatformTransactionManager transactions;
    @LocalServerPort protected int port;
    protected Identity reviewer;
    protected Tenant tenant;
    protected final Instant earlier = Instant.now().minusSeconds(60);

    @BeforeEach protected void prepare() {
        jdbc.execute("truncate ima_identities, ima_tenants, ima_identity_applications, ima_identities__history, ima_affiliations__history, ima_access_revocation_requests cascade");
        reviewer = service("reviewer@example.test"); reviewer.activate(earlier);
        reviewer = identities.save(reviewer);
        tenant = Tenant.create(TenantId.generate(), TenantCode.from("TEST"), TenantName.from("Test tenant"), earlier);
        tenant.activate(earlier); tenant = tenants.save(tenant);
        actor(reviewer.getId());
    }
    @AfterEach protected void clearActor() { SecurityContextHolder.clearContext(); }
    protected void actor(IdentityId id) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(id.toString(), null, "IDENTITY_MANAGE"));
    }
    protected Identity service(String email) {
        return ServiceIdentity.create(IdentityId.generate(), IdentityEmail.create(Email.from(email), EmailType.PRIMARY, earlier), ServiceName.from("Test service"), ServiceDescription.from("Service details"), earlier);
    }
    protected Identity sample(IdentityKind kind, String email) {
        var primary = IdentityEmail.create(Email.from(email), EmailType.PRIMARY, earlier);
        return switch (kind) {
            case PERSON -> PersonIdentity.create(IdentityId.generate(), primary, IdentityFullName.from("Test Person"), Cpf.from("52998224725"), LocalDate.of(1990, 5, 10), IdentityPhoneNumber.create(PhoneNumber.from("48999999999"), earlier), earlier);
            case LEGAL_ENTITY -> LegalEntityIdentity.create(IdentityId.generate(), primary, LegalName.from("Test Company Ltda"), TradeName.from("Test Company"), Cnpj.from("11222333000181"), earlier);
            case SERVICE -> service(email);
        };
    }
    protected IdentityApplicationId request(IdentityKind kind, String email) {
        CreateIdentityApplicationCommand command = switch (kind) {
            case PERSON -> new CreatePersonIdentityApplicationCommand("Test Person", "52998224725", email, LocalDate.of(1990,5,10), "48999999999");
            case LEGAL_ENTITY -> new CreateLegalEntityIdentityApplicationCommand("Test Company Ltda", "Test Company", "11222333000181", email);
            case SERVICE -> new CreateServiceIdentityApplicationCommand("Test service", "Service details", email);
        };
        return IdentityApplicationId.from(create.execute(command).id());
    }
    protected ApproveIdentityApplicationCommand approval(IdentityApplicationId id, AffiliationType type, String code) {
        return new ApproveIdentityApplicationCommand(id, tenant.getId(), new AffiliationSelection(type,
            type == AffiliationType.EMPLOYEE ? code : null,
            type == AffiliationType.CUSTOMER ? code : null,
            type == AffiliationType.SUPPLIER ? code : null));
    }

    protected String token(IdentityId identity, String... permissions) {
        return Jwts.builder().subject(identity.toString()).issuer("identity-test")
            .issuedAt(new Date()).expiration(Date.from(Instant.now().plusSeconds(120)))
            .claim("permissions", List.of(permissions)).signWith(keys.getKey(),Jwts.SIG.HS256).compact();
    }
    protected HttpResponse<String> http(String method, String path, String body, String token) throws Exception {
        var builder=HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).header("Content-Type","application/json");
        if(token!=null) builder.header("Authorization","Bearer "+token);
        return HttpClient.newHttpClient().send(builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }

}
