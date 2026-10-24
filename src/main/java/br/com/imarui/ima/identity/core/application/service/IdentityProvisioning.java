package br.com.imarui.ima.identity.core.application.service;

import br.com.imarui.ima.identity.core.application.command.identityapplication.AffiliationSelection;
import br.com.imarui.ima.identity.core.application.exception.IdentityConflictException;
import br.com.imarui.ima.identity.core.domain.enums.identity.EmailType;
import br.com.imarui.ima.identity.core.domain.model.affiliation.*;
import br.com.imarui.ima.identity.core.domain.model.affiliation.customer.*;
import br.com.imarui.ima.identity.core.domain.model.affiliation.employee.*;
import br.com.imarui.ima.identity.core.domain.model.affiliation.supplier.*;
import br.com.imarui.ima.identity.core.domain.model.identity.*;
import br.com.imarui.ima.identity.core.domain.model.identity.LegalEntity.*;
import br.com.imarui.ima.identity.core.domain.model.identity.person.*;
import br.com.imarui.ima.identity.core.domain.model.identity.service.*;
import br.com.imarui.ima.identity.core.domain.model.identityapplication.*;
import br.com.imarui.ima.identity.core.domain.model.tenant.TenantId;
import br.com.imarui.ima.identity.core.repository.AffiliationRepository;
import java.time.Instant;

public final class IdentityProvisioning {
  private IdentityProvisioning() {}

  public static Identity identity(IdentityApplication application, Instant now) {
    return identity(application, now, application.getKind());
  }

  public static Identity identity(IdentityApplication application, Instant now, br.com.imarui.ima.identity.core.domain.enums.identity.IdentityKind approvedKind) {
    var id = IdentityId.generate();
    var email =
        application.getEmail() == null
            ? null
            : IdentityEmail.create(application.getEmail(), EmailType.PRIMARY, now);
    if (approvedKind == br.com.imarui.ima.identity.core.domain.enums.identity.IdentityKind.SERVICE && application.getKind() != approvedKind) {
      String name = application.getDetails() instanceof PersonIdentityApplicationDetails p ? p.fullName().value() : ((LegalEntityIdentityApplicationDetails)application.getDetails()).legalName().value();
      return ServiceIdentity.create(id, email, ServiceName.from(name), null, now, application.getNickname());
    }
    if (application.getDetails() instanceof PersonIdentityApplicationDetails p)
      return PersonIdentity.create(
          id,
          email,
          p.fullName(),
          p.cpf(),
          p.birthDate(),
          p.phoneNumber() == null ? null : IdentityPhoneNumber.create(p.phoneNumber(), now),
          now,
          application.getNickname());
    if (application.getDetails() instanceof LegalEntityIdentityApplicationDetails p)
      return LegalEntityIdentity.create(
          id, email, p.legalName(), p.tradeName(), p.cnpj(), now, application.getNickname());
    if (application.getDetails() instanceof ServiceIdentityApplicationDetails p)
      return ServiceIdentity.create(
          id, email, p.name(), p.description(), now, application.getNickname());
    throw new IllegalArgumentException("Unsupported Identity kind.");
  }

  public static Affiliation affiliation(
      AffiliationSelection selection,
      TenantId tenant,
      IdentityId identity,
      Instant now,
      AffiliationRepository repository) {
    var id = AffiliationId.generate();
    return switch (selection.type()) {
      case EMPLOYEE -> {
        var code = EmployeeRegistration.from(selection.registration());
        if (repository.existsEmployeeRegistration(tenant, code))
          throw new IdentityConflictException();
        yield EmployeeAffiliation.create(id, tenant, identity, code, now);
      }
      case CUSTOMER -> {
        var code = CustomerCode.from(selection.customerCode());
        if (repository.existsCustomerCode(tenant, code)) throw new IdentityConflictException();
        yield CustomerAffiliation.create(id, tenant, identity, code, now);
      }
      case SUPPLIER -> {
        var code = SupplierCode.from(selection.supplierCode());
        if (repository.existsSupplierCode(tenant, code)) throw new IdentityConflictException();
        yield SupplierAffiliation.create(id, tenant, identity, code, now);
      }
      case PARTNER -> PartnerAffiliation.create(id, tenant, identity, now);
      case EXTERNAL -> ExternalAffiliation.create(id, tenant, identity, now);
    };
  }
}
