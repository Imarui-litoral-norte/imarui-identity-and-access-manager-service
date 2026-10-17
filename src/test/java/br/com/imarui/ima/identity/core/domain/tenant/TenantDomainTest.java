package br.com.imarui.ima.identity.core.domain.tenant;

import br.com.imarui.ima.identity.core.domain.enums.identity.*;
import br.com.imarui.ima.identity.core.domain.enums.affiliation.*;
import br.com.imarui.ima.identity.core.domain.model.identity.*;
import br.com.imarui.ima.identity.core.domain.model.identity.person.*;
import br.com.imarui.ima.identity.core.domain.model.identity.LegalEntity.*;
import br.com.imarui.ima.identity.core.domain.model.identity.service.*;
import br.com.imarui.ima.identity.core.domain.model.identityapplication.*;
import br.com.imarui.ima.identity.core.domain.model.affiliation.*;
import br.com.imarui.ima.identity.core.domain.model.affiliation.employee.*;
import br.com.imarui.ima.identity.core.domain.model.affiliation.customer.*;
import br.com.imarui.ima.identity.core.domain.model.affiliation.supplier.*;
import br.com.imarui.ima.identity.core.domain.model.tenant.*;
import br.com.imarui.ima.identity.core.application.service.IdentityProvisioning;
import br.com.imarui.ima.identity.core.application.command.identityapplication.AffiliationSelection;
import br.com.imarui.ima.identity.core.repository.AffiliationRepository;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static br.com.imarui.ima.identity.support.IdentitySamples.*;

class TenantDomainTest {
    @Test void tenantLifecyclePreservesCodeAndOriginalActivation() {
        var tenant=Tenant.create(TenantId.generate(),TenantCode.from("CODE"),TenantName.from("Test tenant"),NOW);
        assertThrows(br.com.imarui.ima.identity.core.domain.exception.tenant.TenantDisableNotAllowedException.class,()->tenant.disable(NOW));
        tenant.activate(NOW.plusSeconds(1)); tenant.disable(NOW.plusSeconds(2));
        assertThrows(br.com.imarui.ima.identity.core.domain.exception.tenant.TenantActivationNotAllowedException.class,()->tenant.activate(NOW.plusSeconds(3)));
        assertThrows(br.com.imarui.ima.identity.core.domain.exception.tenant.TenantRenameNotAllowedException.class,()->tenant.rename(TenantName.from("New"),NOW.plusSeconds(3)));
        tenant.reactivate(NOW.plusSeconds(4)); tenant.rename(TenantName.from("Renamed"),NOW.plusSeconds(5));
        assertEquals("CODE",tenant.getCode().value()); assertEquals(NOW.plusSeconds(1),tenant.getActivatedAt());
        assertThrows(IllegalArgumentException.class,()->tenant.disable(NOW));
    }
}
