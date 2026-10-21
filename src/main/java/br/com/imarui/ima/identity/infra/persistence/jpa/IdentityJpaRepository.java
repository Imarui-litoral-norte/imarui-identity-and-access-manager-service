package br.com.imarui.ima.identity.infra.persistence.jpa;

import br.com.imarui.ima.identity.core.domain.enums.identity.IdentityStatus;
import br.com.imarui.ima.identity.infra.persistence.entity.identity.*;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface IdentityJpaRepository extends JpaRepository<IdentityEntity, UUID> {

  List<IdentityEntity> findAllByStatusOrderByCreatedAtDesc(IdentityStatus status);

  Optional<IdentityEntity> findByIdAndStatus(UUID identityId, IdentityStatus status);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      select identity
        from IdentityEntity identity
       where identity.id = :identityId
      """)
  Optional<IdentityEntity> findByIdForUpdate(@Param("identityId") UUID identityId);

  @Query(
      """
      select person
        from PersonIdentityEntity person
       where person.cpf = :cpf
      """)
  Optional<PersonIdentityEntity> findPersonByCpf(@Param("cpf") String cpf);

  @Query(
      """
      select legalEntity
        from LegalEntityIdentityEntity legalEntity
       where legalEntity.cnpj = :cnpj
      """)
  Optional<LegalEntityIdentityEntity> findLegalEntityByCnpj(@Param("cnpj") String cnpj);
}
