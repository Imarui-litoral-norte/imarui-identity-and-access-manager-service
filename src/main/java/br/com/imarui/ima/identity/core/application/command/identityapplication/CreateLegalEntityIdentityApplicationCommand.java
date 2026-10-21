package br.com.imarui.ima.identity.core.application.command.identityapplication;

import br.com.imarui.ima.identity.core.domain.enums.identity.IdentityKind;

public record CreateLegalEntityIdentityApplicationCommand(
    String legalName, String tradeName, String cnpj, String email, String nickname)
    implements CreateIdentityApplicationCommand {

  @Override
  public IdentityKind kind() {
    return IdentityKind.LEGAL_ENTITY;
  }
}
