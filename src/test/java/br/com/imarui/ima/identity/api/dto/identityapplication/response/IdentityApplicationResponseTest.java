package br.com.imarui.ima.identity.api.dto.identityapplication.response;

import br.com.imarui.ima.identity.core.application.result.identityapplication.IdentityApplicationResult;
import br.com.imarui.ima.identity.core.domain.enums.identity.IdentityKind;
import br.com.imarui.ima.identity.core.domain.model.identity.Email;
import br.com.imarui.ima.identity.core.domain.model.identity.IdentityFullName;
import br.com.imarui.ima.identity.core.domain.model.identity.LegalEntity.Cnpj;
import br.com.imarui.ima.identity.core.domain.model.identity.LegalEntity.LegalName;
import br.com.imarui.ima.identity.core.domain.model.identity.LegalEntity.TradeName;
import br.com.imarui.ima.identity.core.domain.model.identity.PhoneNumber;
import br.com.imarui.ima.identity.core.domain.model.identity.person.Cpf;
import br.com.imarui.ima.identity.core.domain.model.identity.service.ServiceDescription;
import br.com.imarui.ima.identity.core.domain.model.identity.service.ServiceName;
import br.com.imarui.ima.identity.core.domain.model.identityapplication.IdentityApplication;
import br.com.imarui.ima.identity.core.domain.model.identityapplication.IdentityApplicationId;
import br.com.imarui.ima.identity.core.domain.model.identityapplication.LegalEntityIdentityApplicationDetails;
import br.com.imarui.ima.identity.core.domain.model.identityapplication.PersonIdentityApplicationDetails;
import br.com.imarui.ima.identity.core.domain.model.identityapplication.ServiceIdentityApplicationDetails;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class IdentityApplicationResponseTest {

    private static final Instant REQUESTED_AT =
            Instant.parse("2026-08-05T12:00:00Z");

    @Test
    void mapsPersonDetails() {
        IdentityApplication application = IdentityApplication.create(
                IdentityApplicationId.generate(),
                Email.from("person@example.com"),
                new PersonIdentityApplicationDetails(
                        IdentityFullName.from("Maria da Silva"),
                        Cpf.from("529.982.247-25"),
                        LocalDate.of(1990, 5, 10),
                        PhoneNumber.from("48999999999")
                ),
                REQUESTED_AT
        );

        IdentityApplicationResponse response = IdentityApplicationResponse.from(
                IdentityApplicationResult.from(application)
        );

        assertEquals(IdentityKind.PERSON, response.type());
        assertEquals("person@example.com", response.email());
        PersonIdentityApplicationDetailsResponse details = assertInstanceOf(
                PersonIdentityApplicationDetailsResponse.class,
                response.details()
        );
        assertEquals("Maria da Silva", details.fullName());
        assertEquals("52998224725", details.cpf());
    }

    @Test
    void mapsLegalEntityDetails() {
        IdentityApplication application = IdentityApplication.create(
                IdentityApplicationId.generate(),
                Email.from("legal@example.com"),
                new LegalEntityIdentityApplicationDetails(
                        LegalName.from("Imarui Tecnologia Ltda"),
                        TradeName.from("Imarui Tecnologia"),
                        Cnpj.from("11.222.333/0001-81")
                ),
                REQUESTED_AT
        );

        IdentityApplicationResponse response = IdentityApplicationResponse.from(
                IdentityApplicationResult.from(application)
        );

        assertEquals(IdentityKind.LEGAL_ENTITY, response.type());
        assertEquals("legal@example.com", response.email());
        LegalEntityIdentityApplicationDetailsResponse details = assertInstanceOf(
                LegalEntityIdentityApplicationDetailsResponse.class,
                response.details()
        );
        assertEquals("Imarui Tecnologia Ltda", details.legalName());
        assertEquals("11222333000181", details.cnpj());
    }

    @Test
    void mapsServiceDetails() {
        IdentityApplication application = IdentityApplication.create(
                IdentityApplicationId.generate(),
                Email.from("service@example.com"),
                new ServiceIdentityApplicationDetails(
                        ServiceName.from("Integration Worker"),
                        ServiceDescription.from("Executes identity integrations")
                ),
                REQUESTED_AT
        );

        IdentityApplicationResponse response = IdentityApplicationResponse.from(
                IdentityApplicationResult.from(application)
        );

        assertEquals(IdentityKind.SERVICE, response.type());
        assertEquals("service@example.com", response.email());
        ServiceIdentityApplicationDetailsResponse details = assertInstanceOf(
                ServiceIdentityApplicationDetailsResponse.class,
                response.details()
        );
        assertEquals("Integration Worker", details.name());
        assertEquals(
                "Executes identity integrations",
                details.description()
        );
    }
}
