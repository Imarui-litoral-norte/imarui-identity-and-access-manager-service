package br.com.imarui.ima.identity.api.dto.identityapplication.request.create;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import br.com.imarui.ima.identity.core.application.command.identityapplication.*;
public record CreateUserIdentityApplicationRequest(
  @NotBlank @Size(max=150) String name,
  @NotBlank @Size(max=24) String document,
  @NotBlank @Email @Size(max=254) String email,
  @Past LocalDate birthDate,
  @Pattern(regexp="^(?=(?:\\D*\\d){10,15}\\D*$)[0-9+()\\-\\s]+$") String phoneNumber,
  @Size(max=150) String tradeName,
  @NotBlank @Size(min=3,max=64) String nickname) {
  public CreateIdentityApplicationCommand toCommand() {
    if (!document.matches("[0-9. /-]+")) throw new IllegalArgumentException("Invalid document");
    String digits=document.replaceAll("[^0-9]", "");
    if (digits.length()==11) {
      if (tradeName != null) throw new IllegalArgumentException("Trade name requires a CNPJ");
      return new CreatePersonIdentityApplicationCommand(name,digits,email,birthDate,phoneNumber,nickname);
    }
    if (digits.length()==14) {
      if (birthDate != null || phoneNumber != null) throw new IllegalArgumentException("Person fields require a CPF");
      return new CreateLegalEntityIdentityApplicationCommand(name,tradeName,digits,email,nickname);
    }
    throw new IllegalArgumentException("Document must be CPF or CNPJ");
  }
}
