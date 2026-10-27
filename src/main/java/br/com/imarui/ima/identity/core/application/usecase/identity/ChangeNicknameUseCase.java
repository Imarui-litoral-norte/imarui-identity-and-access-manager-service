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
  private final br.com.imarui.ima.identity.core.application.port.IdentityOperationAccess access;
  private final IdentityRepository identities;
  private final java.time.Clock clock;
  private final br.com.imarui.ima.identity.core.application.port.IdentityRegistrationLock registrationLock;
  private final br.com.imarui.ima.identity.core.repository.IdentityApplicationRepository applications;

  @Transactional
  public IdentityResult execute(IdentityId id, String nickname) {
    var i = identities.findByIdForUpdate(id).orElseThrow();
    access.requireSelfOrPlatform("IDENTITY_MANAGE", id.value());
    nickname = Nickname.from(nickname).value();
    registrationLock.acquireNickname(nickname);
    if (!nickname.equals(i.getNickname()) && (identities.existsNickname(nickname) || applications.existsPendingNickname(nickname)))
      throw new br.com.imarui.ima.identity.core.application.exception.IdentityConflictException();
    i.changeNickname(nickname, clock.instant());
    return IdentityResult.from(identities.save(i));
  }
}
