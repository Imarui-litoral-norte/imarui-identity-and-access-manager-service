package br.com.imarui.ima.identity;
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

class IdentityDomainTest {
    static final Instant NOW = Instant.parse("2026-10-17T12:00:00Z");
    static IdentityApplication application(IdentityKind kind) {
        IdentityApplicationDetails details = switch(kind) {
            case PERSON -> new PersonIdentityApplicationDetails(IdentityFullName.from("Test Person"),Cpf.from("52998224725"),LocalDate.of(1990,1,1),null);
            case LEGAL_ENTITY -> new LegalEntityIdentityApplicationDetails(LegalName.from("Test Company"),null,Cnpj.from("11222333000181"));
            case SERVICE -> new ServiceIdentityApplicationDetails(ServiceName.from("Test service"),null);
        };
        return IdentityApplication.create(IdentityApplicationId.generate(),Email.from("domain@example.test"),details,NOW);
    }
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
    @Test void invalidReconstitutedDomainStateIsRejected() {
        assertThrows(br.com.imarui.ima.identity.core.domain.exception.affiliation.InvalidAffiliationStateException.class,()->ExternalAffiliation.reconstitute(AffiliationId.generate(),TenantId.generate(),IdentityId.generate(),AffiliationStatus.INACTIVE,NOW,NOW,null));
        assertThrows(br.com.imarui.ima.identity.core.domain.exception.identity.InvalidIdentityStateException.class,()->ServiceIdentity.reconstitute(IdentityId.generate(),IdentityEmail.create(Email.from("invalid@example.test"),EmailType.PRIMARY,NOW),ServiceName.from("Service"),null,IdentityStatus.ACTIVE,NOW,NOW,null,null));
    }
    @Test void naturalKeysAndAffiliationIdentifiersAreValidatedWithoutInfrastructure() {
        assertEquals("normalized@example.test",Email.from(" Normalized@Example.Test ").value());
        assertThrows(RuntimeException.class,()->Cpf.from("11111111111")); assertThrows(RuntimeException.class,()->Cnpj.from("11111111111111"));
        assertThrows(RuntimeException.class,()->Email.from("invalid"));
        assertThrows(RuntimeException.class,()->EmployeeRegistration.from(" "));
        assertThrows(RuntimeException.class,()->CustomerCode.from("x".repeat(256)));
        assertThrows(RuntimeException.class,()->SupplierCode.from("x".repeat(256)));
        assertThrows(IllegalArgumentException.class,()->new AffiliationSelection(AffiliationType.EXTERNAL,"wrong",null,null));
        assertThrows(IllegalArgumentException.class,()->new AffiliationSelection(AffiliationType.EMPLOYEE,null,null,null));
    }
}
