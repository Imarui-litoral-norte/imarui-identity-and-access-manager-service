package br.com.imarui.ima.identity.infra.persistence.jpa;
import br.com.imarui.ima.identity.infra.persistence.entity.identity.IdentityHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface IdentityHistoryJpaRepository extends JpaRepository<IdentityHistoryEntity, UUID> {}
