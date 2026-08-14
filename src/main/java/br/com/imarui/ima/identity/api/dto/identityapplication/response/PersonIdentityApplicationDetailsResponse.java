package br.com.imarui.ima.identity.api.dto.identityapplication.response;

import br.com.imarui.ima.identity.core.application.result.identityapplication.PersonIdentityApplicationDetailsResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.Objects;

@Schema(name = "PersonIdentityApplicationDetailsResponse")
public record PersonIdentityApplicationDetailsResponse(
        String fullName,
        String cpf,
        LocalDate birthDate,
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
