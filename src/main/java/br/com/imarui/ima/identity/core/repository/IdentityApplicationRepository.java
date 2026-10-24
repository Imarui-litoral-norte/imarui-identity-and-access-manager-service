package br.com.imarui.ima.identity.core.repository;

import br.com.imarui.ima.identity.core.domain.enums.identityapplication.IdentityApplicationStatus;
import br.com.imarui.ima.identity.core.domain.model.identity.person.Cpf;
import br.com.imarui.ima.identity.core.domain.model.identityapplication.IdentityApplication;
import br.com.imarui.ima.identity.core.domain.model.identityapplication.IdentityApplicationId;
import java.util.List;
import java.util.Optional;

public interface IdentityApplicationRepository {
  boolean existsPendingNickname(String nickname);

  boolean existsPendingEmail(String email);

  IdentityApplication save(IdentityApplication identityApplication);

  Optional<IdentityApplication> findById(IdentityApplicationId identityApplicationId);

  Optional<IdentityApplication> findByIdForUpdate(IdentityApplicationId identityApplicationId);

  List<IdentityApplication> findAll();

  List<IdentityApplication> findAllPending();

  List<IdentityApplication> findByStatus(IdentityApplicationStatus status);

  boolean existsPendingByCpf(Cpf cpf);

  boolean existsPendingByCnpj(
      br.com.imarui.ima.identity.core.domain.model.identity.LegalEntity.Cnpj cnpj);
}
