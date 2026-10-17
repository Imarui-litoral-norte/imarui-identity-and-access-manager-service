package br.com.imarui.ima.identity.infra.persistence.jpa;
import br.com.imarui.ima.identity.infra.persistence.entity.AccessRevocationRequestEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccessRevocationRequestJpaRepository extends JpaRepository<AccessRevocationRequestEntity, UUID> {
    boolean existsByScopeAndSubjectIdAndRequestedAtGreaterThanEqual(String scope, UUID subjectId, java.time.Instant issuedAt);
}
