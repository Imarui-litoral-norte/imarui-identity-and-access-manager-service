package br.com.imarui.ima.identity.infra.persistence.adapter;

import br.com.imarui.ima.identity.core.application.port.AccessRevocation;
import br.com.imarui.ima.identity.core.domain.model.identity.IdentityId;
import br.com.imarui.ima.identity.core.domain.model.tenant.TenantId;
import br.com.imarui.ima.identity.infra.persistence.entity.AccessRevocationRequestEntity;
import br.com.imarui.ima.identity.infra.persistence.jpa.AccessRevocationRequestJpaRepository;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class TransactionalAccessRevocation implements AccessRevocation {
    private final AccessRevocationRequestJpaRepository repository;
    private final Clock clock;
    public void revokeIdentity(IdentityId id) { save("IDENTITY", id.value()); }
    public void revokeTenant(TenantId id) { save("TENANT", id.value()); }
    private void save(String scope, java.util.UUID id) {
        repository.saveAndFlush(new AccessRevocationRequestEntity(scope, id, clock.instant()));
    }
}
