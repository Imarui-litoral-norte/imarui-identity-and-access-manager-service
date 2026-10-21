package br.com.imarui.ima.identity.infra.persistence.identity;

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

class IdentityRevocationIntegrationTest extends IdentityIntegrationSupport {
    @Test void failingRevocationRollsBackIdentityDisable() {
        jdbc.execute("create function fail_revocation() returns trigger language plpgsql as $$ begin raise exception 'simulated revocation failure'; end $$");
        jdbc.execute("create trigger fail_revocation before insert on ima_access_revocation_requests for each row execute function fail_revocation()");
        try { assertThrows(RuntimeException.class, () -> disable.execute(new DisableIdentityCommand(reviewer.getId()))); }
        finally { jdbc.execute("drop trigger fail_revocation on ima_access_revocation_requests"); jdbc.execute("drop function fail_revocation()"); }
        assertTrue(identities.findById(reviewer.getId()).orElseThrow().isActive()); assertEquals(0,revocations.count());
    }

    @Test void oldIdentityTokenRemainsRevokedAfterReactivation() throws Exception {
        var old=token(reviewer.getId(),"IDENTITY_READ");
        disable.execute(new DisableIdentityCommand(reviewer.getId()));
        reactivate.execute(new ReactivateIdentityCommand(reviewer.getId()));
        assertEquals(401,http("GET","/api/v1/identities/"+reviewer.getId(),null,old).statusCode());
    }

    @Test void scopedTokenRemainsRevokedAfterTenantReactivation() throws Exception {
        affiliations.save(ExternalAffiliation.create(AffiliationId.generate(),tenant.getId(),reviewer.getId(),earlier));
        var raw=token(reviewer.getId(),"IDENTITY_READ");
        var claims=Jwts.parser().verifyWith(keys.publicKeys().get(keys.kid())).build().parseSignedClaims(raw).getPayload();
        var scoped=Jwts.builder().claims(claims).header().keyId(keys.kid()).type("at+jwt").and()
            .claim("tenant_id",tenant.getId().toString()).signWith(keys.getKey(),Jwts.SIG.RS256).compact();
        assertEquals(200,http("GET","/api/v1/identities/"+reviewer.getId(),null,scoped).statusCode());
        disableTenant.execute(new DisableTenantCommand(tenant.getId())); reactivateTenant.execute(new ReactivateTenantCommand(tenant.getId()));
        assertEquals(401,http("GET","/api/v1/identities/"+reviewer.getId(),null,scoped).statusCode());
    }
}
