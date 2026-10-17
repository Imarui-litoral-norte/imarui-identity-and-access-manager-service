package br.com.imarui.ima.identity.api.dto.identity.response;
import br.com.imarui.ima.identity.core.application.result.identity.PersonIdentityDetailsResult;
import io.swagger.v3.oas.annotations.media.Schema;
@Schema(name = "PersonIdentityDetailsResponse")
public record PersonIdentityDetailsResponse(
        @io.swagger.v3.oas.annotations.media.Schema(description = "Nome completo aparado da pessoa.", example = "Pessoa Exemplo", accessMode = io.swagger.v3.oas.annotations.media.Schema.AccessMode.READ_ONLY, requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED)
        String fullName,
        @io.swagger.v3.oas.annotations.media.Schema(description = "CPF canônico de 11 dígitos.", example = "52998224725", accessMode = io.swagger.v3.oas.annotations.media.Schema.AccessMode.READ_ONLY, requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED)
        String cpf,
        @io.swagger.v3.oas.annotations.media.Schema(description = "Data de nascimento ISO; ausente quando não informada.", example = "1990-05-10", accessMode = io.swagger.v3.oas.annotations.media.Schema.AccessMode.READ_ONLY)
        java.time.LocalDate birthDate,
        @io.swagger.v3.oas.annotations.media.Schema(description = "Telefone canônico somente com dígitos; opcional.", example = "48999999999", accessMode = io.swagger.v3.oas.annotations.media.Schema.AccessMode.READ_ONLY)
        String phoneNumber
) implements IdentityDetailsResponse {
    public static PersonIdentityDetailsResponse from(PersonIdentityDetailsResult result) {
        return new PersonIdentityDetailsResponse(result.fullName(), result.cpf(), result.birthDate(), result.phoneNumber());
    }
}
