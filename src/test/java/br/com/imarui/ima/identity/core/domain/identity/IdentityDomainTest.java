package br.com.imarui.ima.identity.core.domain.identity;

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

class IdentityDomainTest {
    @ParameterizedTest @EnumSource(IdentityKind.class)
    void allKindsObeyLifecycleAndTimeInvariants(IdentityKind kind) {
        var identity=IdentityProvisioning.identity(application(kind),NOW);
        assertEquals(kind,identity.getKind()); assertTrue(identity.isPending());
        assertThrows(RuntimeException.class,()->identity.reactivate(NOW));
        identity.activate(NOW.plusSeconds(1)); identity.disable(NOW.plusSeconds(2)); identity.reactivate(NOW.plusSeconds(3));
        assertEquals(NOW.plusSeconds(1),identity.getActivatedAt()); assertNull(identity.getDisabledAt());
        assertEquals(NOW.plusSeconds(3),identity.getUpdatedAt());
        assertThrows(IllegalArgumentException.class,()->identity.disable(NOW.plusSeconds(2)));
    }

    @Test void invalidReconstitutedDomainStateIsRejected() {
        assertThrows(br.com.imarui.ima.identity.core.domain.exception.identity.InvalidIdentityStateException.class,()->ServiceIdentity.reconstitute(IdentityId.generate(),IdentityEmail.create(Email.from("invalid@example.test"),EmailType.PRIMARY,NOW),ServiceName.from("Service"),null,IdentityStatus.ACTIVE,NOW,NOW,null,null));
    }

    @Test void naturalKeysAreValidatedWithoutInfrastructure() {
        assertEquals("normalized@example.test",Email.from(" Normalized@Example.Test ").value());
        assertThrows(RuntimeException.class,()->Cpf.from("11111111111")); assertThrows(RuntimeException.class,()->Cnpj.from("11111111111111"));
        assertThrows(RuntimeException.class,()->Email.from("invalid"));

    }
}
