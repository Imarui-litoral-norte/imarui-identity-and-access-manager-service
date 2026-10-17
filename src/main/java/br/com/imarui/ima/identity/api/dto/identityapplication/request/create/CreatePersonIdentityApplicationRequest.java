package br.com.imarui.ima.identity.api.dto.identityapplication.request.create;

import br.com.imarui.ima.identity.core.domain.enums.identity.IdentityKind;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

@Schema(name = "CreatePersonIdentityApplicationRequest", description = "Solicitação PERSON com nome, CPF e e-mail obrigatórios. Nascimento e telefone são opcionais; CPF é normalizado para dígitos.")
public record CreatePersonIdentityApplicationRequest(

        @NotBlank(message = "Full name is required.")
        @Size(
                max = 150,
                message = "Full name must not exceed 150 characters."
        )
        String fullName,

        @NotBlank(message = "CPF is required.")
        @Pattern(
                regexp = "^[0-9.\\-\\s]+$",
                message = "CPF has an invalid format."
        )
        String cpf,

        @NotBlank(message = "Email is required.")
        @Email(message = "Email has an invalid format.")
        @Size(
                max = 254,
                message = "Email must not exceed 254 characters."
        )
        String email,

        @Past(message = "Birth date must be in the past.")
        LocalDate birthDate,

        @Pattern(
                regexp = "^(?=(?:\\D*\\d){10,15}\\D*$)[0-9+()\\-\\s]+$",
                message = "Phone number must contain between 10 and 15 digits."
        )
        String phoneNumber

) implements CreateIdentityApplicationRequest {

    @Override
    public IdentityKind type() {
        return IdentityKind.PERSON;
    }
}
