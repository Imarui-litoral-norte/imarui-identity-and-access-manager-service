package br.com.imarui.ima.identity.core.application.port;

import br.com.imarui.ima.identity.core.domain.model.identity.IdentityId;

/** Trusted authenticated subject; never taken from request payloads. */
public interface CurrentActor {
    IdentityId requireIdentityId();
}
