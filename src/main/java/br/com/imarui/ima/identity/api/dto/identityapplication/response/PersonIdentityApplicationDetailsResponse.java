package br.com.imarui.ima.identity.api.dto.identityapplication.response;

import br.com.imarui.ima.identity.core.application.result.identityapplication.PersonIdentityApplicationDetailsResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.Objects;

@Schema(name = "PersonIdentityApplicationDetailsResponse")
public record PersonIdentityApplicationDetailsResponse(
        @io.swagger.v3.oas.annotations.media.Schema(description = "Nome completo aparado da pessoa.", example = "Pessoa Exemplo", accessMode = io.swagger.v3.oas.annotations.media.Schema.AccessMode.READ_ONLY, requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED)
        String fullName,
        @io.swagger.v3.oas.annotations.media.Schema(description = "CPF canônico de 11 dígitos.", example = "52998224725", accessMode = io.swagger.v3.oas.annotations.media.Schema.AccessMode.READ_ONLY, requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED)
        String cpf,
        @io.swagger.v3.oas.annotations.media.Schema(description = "Data de nascimento ISO; ausente quando não informada.", example = "1990-05-10", accessMode = io.swagger.v3.oas.annotations.media.Schema.AccessMode.READ_ONLY)
        LocalDate birthDate,
        @io.swagger.v3.oas.annotations.media.Schema(description = "Telefone canônico somente com dígitos; opcional.", example = "48999999999", accessMode = io.swagger.v3.oas.annotations.media.Schema.AccessMode.READ_ONLY)
        String phoneNumber
) implements IdentityApplicationDetailsResponse {

    public static PersonIdentityApplicationDetailsResponse from(
            PersonIdentityApplicationDetailsResult result
    ) {
        Objects.requireNonNull(result, "result cannot be null");

        return new PersonIdentityApplicationDetailsResponse(
                result.fullName(),
                result.cpf(),
                result.birthDate(),
                result.phoneNumber()
        );
    }
}
