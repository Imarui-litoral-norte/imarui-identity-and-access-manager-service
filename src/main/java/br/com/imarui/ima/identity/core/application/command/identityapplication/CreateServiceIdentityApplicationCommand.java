package br.com.imarui.ima.identity.core.application.command.identityapplication;

import br.com.imarui.ima.identity.core.domain.enums.identity.IdentityKind;

public record CreateServiceIdentityApplicationCommand(
    String name, String description, String email, String nickname)
    implements CreateIdentityApplicationCommand {

  @Override
  public IdentityKind kind() {
    return IdentityKind.SERVICE;
  }
}
