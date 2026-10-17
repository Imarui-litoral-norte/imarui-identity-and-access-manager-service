package br.com.imarui.ima.identity.infra.persistence.affiliation;

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

class AffiliationIntegrationTest extends IdentityIntegrationSupport {
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
}
