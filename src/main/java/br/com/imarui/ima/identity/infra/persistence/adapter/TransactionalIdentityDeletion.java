package br.com.imarui.ima.identity.infra.persistence.adapter;

import br.com.imarui.ima.identity.core.application.port.IdentityDeletion;
import br.com.imarui.ima.identity.core.domain.model.identity.*;
import br.com.imarui.ima.identity.infra.persistence.entity.identity.IdentityHistoryEntity;
import br.com.imarui.ima.identity.infra.persistence.jpa.*;
import br.com.imarui.ima.identity.infra.persistence.mapper.IdentityPersistenceMapper;
import br.com.imarui.ima.identity.infra.persistence.mapper.AffiliationHistoryMapper;
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
    private final AffiliationHistoryJpaRepository affiliationHistory;
    private final AffiliationHistoryMapper affiliationHistoryMapper;
    private final IdentityPersistenceMapper mapper;
    private final JsonMapper json;

    public void archiveAndDelete(Identity identity, IdentityId actor, String reason, Instant deletedAt) {
        // Serialize a detached concrete snapshot, never a lazy Hibernate proxy.
        String snapshot = json.writeValueAsString(mapper.toEntity(identity));
        var related = affiliations.findByIdentityIdForUpdate(identity.getId().value());
        var archived = history.saveAndFlush(new IdentityHistoryEntity(identity, actor.value(), reason, deletedAt, snapshot));
        affiliationHistory.saveAllAndFlush(related.stream()
                .map(affiliation -> affiliationHistoryMapper.toHistory(affiliation, archived.getId(), actor.value(), reason, deletedAt))
                .toList());
        affiliations.deleteAll(related);
        affiliations.flush();
        identities.deleteById(identity.getId().value());
        identities.flush();
    }
}
