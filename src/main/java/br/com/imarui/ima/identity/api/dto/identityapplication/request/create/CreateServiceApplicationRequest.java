package br.com.imarui.ima.identity.api.dto.identityapplication.request.create;
import jakarta.validation.constraints.*;
import br.com.imarui.ima.identity.core.application.command.identityapplication.CreateServiceIdentityApplicationCommand;
public record CreateServiceApplicationRequest(@NotBlank @Size(max=100) String name,
  @Pattern(regexp=".*\\S.*") @Size(max=500) String description,
  @NotBlank @Email @Size(max=254) String email,
  @NotBlank @Size(min=3,max=64) String nickname) {
  public CreateServiceIdentityApplicationCommand toCommand() { return new CreateServiceIdentityApplicationCommand(name,description,email,nickname); }
}
