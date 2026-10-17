package br.com.imarui.ima.identity.infra.persistence.adapter;

import br.com.imarui.ima.identity.core.application.port.IdentityDeletion;
import br.com.imarui.ima.identity.core.domain.model.identity.*;
import br.com.imarui.ima.identity.infra.persistence.entity.identity.IdentityHistoryEntity;
import br.com.imarui.ima.identity.infra.persistence.jpa.*;
import br.com.imarui.ima.identity.infra.persistence.mapper.IdentityPersistenceMapper;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class TransactionalIdentityDeletion implements IdentityDeletion {
    private final IdentityHistoryJpaRepository history;
    private final IdentityJpaRepository identities;
    private final AffiliationJpaRepository affiliations;
    private final IdentityPersistenceMapper mapper;
    private final JsonMapper json;

    public void archiveAndDelete(Identity identity, IdentityId actor, String reason, Instant deletedAt) {
        // Serialize a detached concrete snapshot, never a lazy Hibernate proxy.
        String snapshot = json.writeValueAsString(mapper.toEntity(identity));
        history.saveAndFlush(new IdentityHistoryEntity(identity, actor.value(), reason, deletedAt, snapshot));
        affiliations.deleteAll(affiliations.findByIdentityId(identity.getId().value()));
        affiliations.flush();
        identities.deleteById(identity.getId().value());
        identities.flush();
    }
}
