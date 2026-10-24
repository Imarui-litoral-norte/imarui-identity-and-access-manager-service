package br.com.imarui.ima.identity.core.repository;

import br.com.imarui.ima.identity.core.domain.model.identity.Identity;
import br.com.imarui.ima.identity.core.domain.model.identity.IdentityId;
import br.com.imarui.ima.identity.core.domain.model.identity.LegalEntity.Cnpj;
import br.com.imarui.ima.identity.core.domain.model.identity.person.Cpf;
import java.util.List;
import java.util.Optional;

public interface IdentityRepository {
  boolean existsNickname(String nickname);

  boolean existsEmail(String email);

  Identity save(Identity identity);

  List<Identity> findAllActive();

  Optional<Identity> findById(IdentityId identityId);

  Optional<Identity> findByIdForUpdate(IdentityId identityId);

  Optional<Identity> findPersonByCpf(Cpf cpf);

  Optional<Identity> findLegalEntityByCnpj(Cnpj cnpj);
}
