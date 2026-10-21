package br.com.imarui.ima.identity.feature;

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

class IdentityEndToEndTest extends IdentityIntegrationSupport {
    @Test void httpOnboardingLifecycleErrorsAndSelfDeleteAreFunctional() throws Exception {
        var admin=token(reviewer.getId(),"IDENTITY_APPLICATION_REVIEW","IDENTITY_MANAGE","IDENTITY_READ");
        var created=http("POST","/api/v1/identity-applications","{\"type\":\"SERVICE\",\"nickname\":\"fixture-service\",\"name\":\"HTTP service\",\"email\":\"http-created@example.test\"}",null);
        assertEquals(201,created.statusCode(),created.body()); assertTrue(created.headers().firstValue("Location").isPresent());
        var applicationId=json.readTree(created.body()).path("id").asText();
        var approved=http("POST","/api/v1/admin/identity-applications/"+applicationId+"/approve","{\"tenantId\":\""+tenant.getId()+"\",\"affiliation\":{\"type\":\"EXTERNAL\"}}",admin);
        assertEquals(200,approved.statusCode(),approved.body());
        var identityId=IdentityId.from(json.readTree(approved.body()).path("resolvedIdentityId").asText());
        var activation=http("POST","/api/v1/admin/identities/"+identityId+"/activate",null,admin);
        assertEquals(409,activation.statusCode(),activation.body());
        firstAccess(identityId);
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
