package br.com.imarui.ima.identity.core.application.usecase.identity;

import br.com.imarui.ima.identity.core.application.result.identity.IdentityResult;
import br.com.imarui.ima.identity.core.domain.model.identity.*;
import br.com.imarui.ima.identity.core.repository.IdentityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChangeNicknameUseCase {
  private final IdentityRepository identities;
  private final java.time.Clock clock;

  @Transactional
  public IdentityResult execute(IdentityId id, String nickname) {
    var i = identities.findByIdForUpdate(id).orElseThrow();
    i.changeNickname(nickname, clock.instant());
    return IdentityResult.from(identities.save(i));
  }
}
