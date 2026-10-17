package br.com.imarui.ima.identity.core.domain.affiliation;

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

class AffiliationDomainTest {
    @ParameterizedTest @EnumSource(AffiliationType.class)
    void everyAffiliationSpecializationPreservesLifetimeAndRejectsInvalidTransitions(AffiliationType type) {
        var selection=new AffiliationSelection(type,type==AffiliationType.EMPLOYEE?"EMP-1":null,type==AffiliationType.CUSTOMER?"CUS-1":null,type==AffiliationType.SUPPLIER?"SUP-1":null);
        var affiliation=IdentityProvisioning.affiliation(selection,TenantId.generate(),IdentityId.generate(),NOW,mock(AffiliationRepository.class));
        assertEquals(type,affiliation.getType()); assertTrue(affiliation.isActive());
        assertThrows(br.com.imarui.ima.identity.core.domain.exception.affiliation.AffiliationAlreadyActiveException.class,()->affiliation.reactivate(NOW));
        affiliation.deactivate(NOW.plusSeconds(1));
        assertThrows(br.com.imarui.ima.identity.core.domain.exception.affiliation.AffiliationAlreadyInactiveException.class,()->affiliation.deactivate(NOW.plusSeconds(2)));
        affiliation.reactivate(NOW.plusSeconds(3)); assertEquals(NOW,affiliation.getStartedAt()); assertNull(affiliation.getEndedAt());
        assertThrows(IllegalArgumentException.class,()->affiliation.deactivate(NOW));
    }
    @Test void inactiveAffiliationRequiresAnEndTimestamp() {
        assertThrows(br.com.imarui.ima.identity.core.domain.exception.affiliation.InvalidAffiliationStateException.class,()->ExternalAffiliation.reconstitute(AffiliationId.generate(),TenantId.generate(),IdentityId.generate(),AffiliationStatus.INACTIVE,NOW,NOW,null));
    }
    @Test void affiliationIdentifiersAndSelectionRejectInvalidFields() {
        assertThrows(RuntimeException.class,()->EmployeeRegistration.from(" "));
        assertThrows(RuntimeException.class,()->CustomerCode.from("x".repeat(256)));
        assertThrows(RuntimeException.class,()->SupplierCode.from("x".repeat(256)));
        assertThrows(IllegalArgumentException.class,()->new AffiliationSelection(AffiliationType.EXTERNAL,"wrong",null,null));
        assertThrows(IllegalArgumentException.class,()->new AffiliationSelection(AffiliationType.EMPLOYEE,null,null,null));
    }
}
