package br.com.imarui.ima.identity.infra.security;

import br.com.imarui.ima.identity.core.application.port.CurrentActor;
import br.com.imarui.ima.identity.core.application.exception.CurrentActorUnavailableException;
import br.com.imarui.ima.identity.core.domain.model.identity.IdentityId;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@lombok.RequiredArgsConstructor
public class SecurityCurrentActor implements CurrentActor {
    private final br.com.imarui.ima.identity.core.repository.IdentityRepository identities;
    public IdentityId requireIdentityId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new CurrentActorUnavailableException();
        }
        try {
            var id = IdentityId.from(authentication.getName());
            if (identities.findById(id).filter(br.com.imarui.ima.identity.core.domain.model.identity.Identity::isActive).isEmpty()) throw new CurrentActorUnavailableException();
            return id;
        }
        catch (IllegalArgumentException exception) { throw new CurrentActorUnavailableException(); }
    }
}
