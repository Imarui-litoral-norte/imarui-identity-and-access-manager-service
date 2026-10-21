package br.com.imarui.ima.identity.infra.persistence.jpa;

import br.com.imarui.ima.identity.core.domain.enums.identityapplication.IdentityApplicationStatus;
import br.com.imarui.ima.identity.infra.persistence.entity.identityapplication.IdentityApplicationEntity;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IdentityApplicationJpaRepository
    extends JpaRepository<IdentityApplicationEntity, UUID> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      select application
        from IdentityApplicationEntity application
       where application.id = :identityApplicationId
      """)
  Optional<IdentityApplicationEntity> findByIdForUpdate(
      @Param("identityApplicationId") UUID identityApplicationId);

  List<IdentityApplicationEntity> findAllByOrderByRequestedAtDesc();

  List<IdentityApplicationEntity> findByStatusOrderByRequestedAtAsc(
      IdentityApplicationStatus status);

  boolean existsByCnpjAndStatus(String cnpj, IdentityApplicationStatus status);

  boolean existsByCpfAndStatus(String cpf, IdentityApplicationStatus status);
}
