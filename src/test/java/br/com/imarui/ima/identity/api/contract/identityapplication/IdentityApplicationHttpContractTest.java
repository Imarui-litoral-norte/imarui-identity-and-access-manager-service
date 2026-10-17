package br.com.imarui.ima.identity.api.contract.identityapplication;

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
import br.com.imarui.ima.identity.support.IdentityIntegrationSupport;

class IdentityApplicationHttpContractTest extends IdentityIntegrationSupport {
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

    @ParameterizedTest
    @EnumSource(AffiliationType.class)
    void httpApprovalAcceptsEachFeatureSpecificAffiliation(AffiliationType type) throws Exception {
        var id = request(IdentityKind.SERVICE, "http-affiliation@example.test");
        var affiliation = new LinkedHashMap<String,Object>(); affiliation.put("type",type.name());
        if(type == AffiliationType.EMPLOYEE) affiliation.put("registration","EMP-42");
        if(type == AffiliationType.CUSTOMER) affiliation.put("customerCode","CUS-42");
        if(type == AffiliationType.SUPPLIER) affiliation.put("supplierCode","SUP-42");
        String body = json.writeValueAsString(Map.of("tenantId",tenant.getId().value(),"affiliation",affiliation));
        var result = http("POST","/api/v1/admin/identity-applications/"+id.value()+"/approve",body,token(reviewer.getId(),"IDENTITY_APPLICATION_REVIEW"));
        assertEquals(200,result.statusCode(),result.body());
        var response = json.readTree(result.body());
        assertEquals("APPROVED",response.path("status").asText());
        assertEquals(reviewer.getId().toString(),response.path("reviewedByIdentityId").asText());
        var created = affiliations.findById(AffiliationId.from(response.path("resultingAffiliationId").asText())).orElseThrow();
        assertEquals(type,created.getType());
    }
    @Test void applicationReadListAndReviewKeepTheirDistinctPermissions() throws Exception {
        var id=request(IdentityKind.SERVICE,"read@example.test");
        String path="/api/v1/identity-applications/"+id.value();
        assertEquals(401,http("GET",path,null,null).statusCode());
        assertEquals(403,http("GET",path,null,token(reviewer.getId())).statusCode());
        assertEquals(200,http("GET",path,null,token(reviewer.getId(),"IDENTITY_APPLICATION_READ")).statusCode());
        assertEquals(403,http("GET","/api/v1/admin/identity-applications",null,token(reviewer.getId(),"IDENTITY_APPLICATION_READ")).statusCode());
        var list=http("GET","/api/v1/admin/identity-applications",null,token(reviewer.getId(),"IDENTITY_APPLICATION_REVIEW"));
        assertEquals(200,list.statusCode(),list.body()); assertTrue(json.readTree(list.body()).isArray()); assertEquals(1,json.readTree(list.body()).size());
    }
    @Test void rejectionValidatesReasonAndAuditsTheAuthenticatedReviewer() throws Exception {
        var id=request(IdentityKind.SERVICE,"reject-http@example.test");
        String path="/api/v1/admin/identity-applications/"+id.value()+"/reject";
        String admin=token(reviewer.getId(),"IDENTITY_APPLICATION_REVIEW");
        assertEquals(400,http("POST",path,"{\"reason\":\" \"}",admin).statusCode());
        var rejected=http("POST",path,"{\"reason\":\" Incomplete data \"}",admin);
        assertEquals(200,rejected.statusCode(),rejected.body());
        assertEquals("Incomplete data",json.readTree(rejected.body()).path("rejectionReason").asText());
        assertEquals(reviewer.getId().toString(),json.readTree(rejected.body()).path("reviewedByIdentityId").asText());
        assertEquals(409,http("POST",path,"{\"reason\":\"Again\"}",admin).statusCode());
        assertEquals(1,jdbc.queryForObject("select count(*) from ima_identities",Integer.class));
    }

}
