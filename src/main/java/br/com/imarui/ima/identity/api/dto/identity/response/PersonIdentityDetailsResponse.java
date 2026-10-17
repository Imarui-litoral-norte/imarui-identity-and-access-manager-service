package br.com.imarui.ima.identity.api.dto.identity.response;
import br.com.imarui.ima.identity.core.application.result.identity.PersonIdentityDetailsResult;
import io.swagger.v3.oas.annotations.media.Schema;
@Schema(name = "PersonIdentityDetailsResponse")
public record PersonIdentityDetailsResponse(String fullName, String cpf, java.time.LocalDate birthDate, String phoneNumber) implements IdentityDetailsResponse {
    public static PersonIdentityDetailsResponse from(PersonIdentityDetailsResult result) {
        return new PersonIdentityDetailsResponse(result.fullName(), result.cpf(), result.birthDate(), result.phoneNumber());
    }
}
