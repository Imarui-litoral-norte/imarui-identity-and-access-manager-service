package br.com.imarui.ima.identity.api.dto.identityapplication.request.create;

import br.com.imarui.ima.identity.core.domain.enums.identity.IdentityKind;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(name = "CreateServiceIdentityApplicationRequest", description = "Solicitação SERVICE com nome e e-mail obrigatórios; descrição opcional.")
public record CreateServiceIdentityApplicationRequest(

        @NotBlank(message = "Service name is required.")
        @Size(
                max = 100,
                message = "Service name must not exceed 100 characters."
        )
        String name,

        @Pattern(
                regexp = ".*\\S.*",
                message = "Service description must not be blank when informed."
        )
        @Size(
                max = 500,
                message = "Service description must not exceed 500 characters."
        )
        String description,

        @Email(message = "Email has an invalid format.")
        @Size(
                max = 254,
                message = "Email must not exceed 254 characters."
        )
        String email

, @NotBlank @Size(min=3,max=64) String nickname
) implements CreateIdentityApplicationRequest {

    @Override
    public IdentityKind type() {
        return IdentityKind.SERVICE;
    }
}
