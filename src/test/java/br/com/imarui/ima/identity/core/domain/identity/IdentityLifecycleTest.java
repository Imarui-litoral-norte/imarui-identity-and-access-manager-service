package br.com.imarui.ima.identity.core.domain.identity;

import br.com.imarui.ima.identity.core.application.command.identity.*;
import br.com.imarui.ima.identity.core.application.usecase.identity.*;
import br.com.imarui.ima.identity.core.application.port.*;
import br.com.imarui.ima.identity.core.domain.model.identity.*;
import br.com.imarui.ima.identity.core.domain.model.identity.service.*;
import br.com.imarui.ima.identity.core.domain.model.tenant.*;
import br.com.imarui.ima.identity.core.domain.model.affiliation.*;
import br.com.imarui.ima.identity.core.domain.policy.AffiliationValidity;
import br.com.imarui.ima.identity.core.domain.enums.identity.*;
import br.com.imarui.ima.identity.core.domain.exception.identity.*;
import br.com.imarui.ima.identity.core.repository.IdentityRepository;
import java.time.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IdentityLifecycleTest {
    static final Instant NOW = Instant.parse("2026-10-17T12:00:00Z");
    static ServiceIdentity identity() {
        return ServiceIdentity.create(IdentityId.generate(), IdentityEmail.create(Email.from("service@example.test"), EmailType.PRIMARY, NOW), ServiceName.from("Test service"), null, NOW);
    }

    @Test void lifecyclePreservesFirstActivationAndRejectsInvalidTransitions() {
        var identity = identity();
        assertThrows(InvalidIdentityStateException.class, () -> identity.disable(NOW));
        identity.activate(NOW.plusSeconds(1));
        assertThrows(IdentityAlreadyActiveException.class, () -> identity.activate(NOW.plusSeconds(2)));
        identity.disable(NOW.plusSeconds(3));
        assertThrows(IdentityAlreadyDisabledException.class, () -> identity.disable(NOW.plusSeconds(4)));
        identity.reactivate(NOW.plusSeconds(5));
        assertEquals(NOW.plusSeconds(1), identity.getActivatedAt());
        assertNull(identity.getDisabledAt());
        assertThrows(IdentityNotDisabledException.class, () -> identity.reactivate(NOW.plusSeconds(6)));
        assertThrows(IllegalArgumentException.class, () -> identity.disable(NOW));
    }
}
