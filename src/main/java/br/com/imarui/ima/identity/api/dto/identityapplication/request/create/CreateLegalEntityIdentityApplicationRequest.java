package br.com.imarui.ima.identity.api.dto.identityapplication.request.create;

import br.com.imarui.ima.identity.core.domain.enums.identity.IdentityKind;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(name = "CreateLegalEntityIdentityApplicationRequest", description = "Solicitação LEGAL_ENTITY com razão social, CNPJ e e-mail obrigatórios; nome fantasia opcional.")
public record CreateLegalEntityIdentityApplicationRequest(

        @NotBlank(message = "Legal name is required.")
        @Size(
                max = 150,
                message = "Legal name must not exceed 150 characters."
        )
        String legalName,

        @Pattern(
                regexp = ".*\\S.*",
                message = "Trade name must not be blank when informed."
        )
        @Size(
                max = 150,
                message = "Trade name must not exceed 150 characters."
        )
        String tradeName,

        @NotBlank(message = "CNPJ is required.")
        @Pattern(
                regexp = "^[0-9./\\-\\s]+$",
                message = "CNPJ has an invalid format."
        )
        String cnpj,

        @NotBlank(message = "Email is required.")
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
        return IdentityKind.LEGAL_ENTITY;
    }
}
