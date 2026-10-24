package br.com.imarui.ima.identity.core.application.service;

import br.com.imarui.ima.identity.core.application.exception.IdentityConflictException;
import br.com.imarui.ima.identity.core.domain.model.identityapplication.*;
import br.com.imarui.ima.identity.core.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class IdentityApplicationConflicts {
  private final IdentityRepository identities;
  private final IdentityApplicationRepository applications;

  public void check(IdentityApplication application, boolean includePending) {
    if (application.getNickname() != null
        && (identities.existsNickname(application.getNickname())
            || includePending
                && applications.existsPendingNickname(application.getNickname())))
      throw new IdentityConflictException();
    if (application.getEmail() != null && (identities.existsEmail(application.getEmail().value())
        || includePending && applications.existsPendingEmail(application.getEmail().value())))
      throw new IdentityConflictException();
    if (application.getDetails() instanceof PersonIdentityApplicationDetails person
        && (identities.findPersonByCpf(person.cpf()).isPresent()
            || includePending && applications.existsPendingByCpf(person.cpf())))
      throw new IdentityConflictException();
    if (application.getDetails() instanceof LegalEntityIdentityApplicationDetails legal
        && (identities.findLegalEntityByCnpj(legal.cnpj()).isPresent()
            || includePending && applications.existsPendingByCnpj(legal.cnpj())))
      throw new IdentityConflictException();
  }
}
