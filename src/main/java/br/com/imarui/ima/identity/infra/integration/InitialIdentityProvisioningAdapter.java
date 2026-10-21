package br.com.imarui.ima.identity.infra.integration;

import br.com.imarui.ima.identity.api.module.InitialIdentityProvisioning;
import br.com.imarui.ima.identity.core.domain.enums.identity.*;
import br.com.imarui.ima.identity.core.domain.model.identity.*;
import br.com.imarui.ima.identity.core.domain.model.identity.LegalEntity.*;
import br.com.imarui.ima.identity.core.domain.model.identity.person.*;
import br.com.imarui.ima.identity.core.domain.model.identity.service.*;
import br.com.imarui.ima.identity.core.repository.IdentityRepository;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

@Component
@RequiredArgsConstructor
public class InitialIdentityProvisioningAdapter implements InitialIdentityProvisioning {
  private final IdentityRepository identities;
  private final Clock clock;

  @Transactional(propagation = Propagation.MANDATORY)
  public UUID create(IdentityKind kind, String nickname, String contact, Map<String, String> d) {
    var now = clock.instant();
    var id = IdentityId.generate();
    var email =
        contact == null ? null : IdentityEmail.create(Email.from(contact), EmailType.PRIMARY, now);
    Identity identity =
        switch (kind) {
          case PERSON ->
              PersonIdentity.create(
                  id,
                  email,
                  IdentityFullName.from(d.get("name")),
                  Cpf.from(d.get("cpf")),
                  null,
                  null,
                  now);
          case LEGAL_ENTITY ->
              LegalEntityIdentity.create(
                  id, email, LegalName.from(d.get("name")), null, Cnpj.from(d.get("cnpj")), now);
          case SERVICE ->
              ServiceIdentity.create(id, email, ServiceName.from(d.get("name")), null, now);
        };
    identity.assignNickname(nickname);
    return identities.save(identity).getId().value();
  }
}
