package br.com.imarui.ima.identity.support;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import br.com.imarui.ima.identity.core.domain.enums.affiliation.*;
import br.com.imarui.ima.identity.core.domain.enums.identity.*;
import br.com.imarui.ima.identity.core.domain.model.affiliation.*;
import br.com.imarui.ima.identity.core.domain.model.affiliation.customer.*;
import br.com.imarui.ima.identity.core.domain.model.affiliation.employee.*;
import br.com.imarui.ima.identity.core.domain.model.affiliation.supplier.*;
import br.com.imarui.ima.identity.core.domain.model.identity.*;
import br.com.imarui.ima.identity.core.domain.model.identity.LegalEntity.*;
import br.com.imarui.ima.identity.core.domain.model.identity.person.*;
import br.com.imarui.ima.identity.core.domain.model.identity.service.*;
import br.com.imarui.ima.identity.core.domain.model.identityapplication.*;
import br.com.imarui.ima.identity.core.domain.model.tenant.*;
import java.time.*;

public final class IdentitySamples {
  private IdentitySamples() {}

  public static final Instant NOW = Instant.parse("2026-10-17T12:00:00Z");

  public static IdentityApplication application(IdentityKind kind) {
    IdentityApplicationDetails details =
        switch (kind) {
          case PERSON ->
              new PersonIdentityApplicationDetails(
                  IdentityFullName.from("Test Person"),
                  Cpf.from("52998224725"),
                  LocalDate.of(1990, 1, 1),
                  null);
          case LEGAL_ENTITY ->
              new LegalEntityIdentityApplicationDetails(
                  LegalName.from("Test Company"), null, Cnpj.from("11222333000181"));
          case SERVICE ->
              new ServiceIdentityApplicationDetails(ServiceName.from("Test service"), null);
        };
    var application =
        IdentityApplication.create(
            IdentityApplicationId.generate(),
            Email.from("domain@example.test"),
            details,
            NOW,
            "fixture-" + java.util.UUID.randomUUID());
    application.assignNickname("domain-fixture");
    return application;
  }
}
