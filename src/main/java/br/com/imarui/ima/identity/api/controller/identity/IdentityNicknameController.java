package br.com.imarui.ima.identity.api.controller.identity;

import br.com.imarui.ima.identity.api.dto.identity.response.IdentityResponse;
import br.com.imarui.ima.identity.core.application.usecase.identity.ChangeNicknameUseCase;
import br.com.imarui.ima.identity.core.domain.model.identity.IdentityId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class IdentityNicknameController {
  private final ChangeNicknameUseCase useCase;

  public record Rename(@NotBlank @Size(min = 3, max = 64) String nickname) {}

  @PatchMapping("/api/v1/identities/{identityId}/nickname")
  @PreAuthorize("hasAuthority('IDENTITY_MANAGE') or authentication.name == #identityId")
  @io.swagger.v3.oas.annotations.Operation(
      summary = "Alterar nickname",
      description =
          "Titular ou IDENTITY_MANAGE; unicidade global case-insensitive, protegida no"
              + " PostgreSQL.")
  public IdentityResponse change(
      @PathVariable String identityId, @Valid @RequestBody Rename request) {
    return IdentityResponse.from(useCase.execute(IdentityId.from(identityId), request.nickname()));
  }
}
