package br.com.imarui.ima.identity.core.application.port;

import br.com.imarui.ima.identity.core.domain.model.identity.Identity;
import br.com.imarui.ima.identity.core.domain.model.identity.IdentityId;
import java.time.Instant;

public interface IdentityDeletion {
    void archiveAndDelete(Identity identity, IdentityId actor, String reason, Instant deletedAt);
}
