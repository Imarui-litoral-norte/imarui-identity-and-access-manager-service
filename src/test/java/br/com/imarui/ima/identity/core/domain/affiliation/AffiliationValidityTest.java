package br.com.imarui.ima.identity.core.domain.affiliation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import br.com.imarui.ima.identity.core.application.command.identity.*;
import br.com.imarui.ima.identity.core.application.port.*;
import br.com.imarui.ima.identity.core.application.usecase.identity.*;
import br.com.imarui.ima.identity.core.domain.enums.identity.*;
import br.com.imarui.ima.identity.core.domain.exception.identity.*;
import br.com.imarui.ima.identity.core.domain.model.affiliation.*;
import br.com.imarui.ima.identity.core.domain.model.identity.*;
import br.com.imarui.ima.identity.core.domain.model.identity.service.*;
import br.com.imarui.ima.identity.core.domain.model.tenant.*;
import br.com.imarui.ima.identity.core.domain.policy.AffiliationValidity;
import java.time.*;
import org.junit.jupiter.api.Test;

class AffiliationValidityTest {
  static final Instant NOW = Instant.parse("2026-10-17T12:00:00Z");

  static ServiceIdentity identity() {
    return ServiceIdentity.create(
        IdentityId.generate(),
        IdentityEmail.create(Email.from("service@example.test"), EmailType.PRIMARY, NOW),
        ServiceName.from("Test service"),
        null,
        NOW,
        "fixture-" + java.util.UUID.randomUUID());
  }

  @Test
  void tenantContextNeverMutatesIntrinsicAffiliationState() {
    var identity = identity();
    identity.activate(NOW);
    var tenant =
        Tenant.create(
            TenantId.generate(), TenantCode.from("test"), TenantName.from("Test tenant"), NOW);
    tenant.activate(NOW);
    var affiliation =
        ExternalAffiliation.create(AffiliationId.generate(), tenant.getId(), identity.getId(), NOW);
    assertTrue(AffiliationValidity.isEffective(affiliation, identity, tenant));
    tenant.disable(NOW.plusSeconds(1));
    assertFalse(AffiliationValidity.isEffective(affiliation, identity, tenant));
    assertTrue(affiliation.isActive());
    assertTrue(identity.isActive());
    tenant.reactivate(NOW.plusSeconds(2));
    assertTrue(AffiliationValidity.isEffective(affiliation, identity, tenant));
    affiliation.deactivate(NOW.plusSeconds(3));
    tenant.disable(NOW.plusSeconds(4));
    tenant.reactivate(NOW.plusSeconds(5));
    assertFalse(AffiliationValidity.isEffective(affiliation, identity, tenant));
    assertTrue(affiliation.isInactive());
    assertEquals(NOW.plusSeconds(3), affiliation.getEndedAt());
    affiliation.reactivate(NOW.plusSeconds(6));
    assertEquals(NOW, affiliation.getStartedAt());
    assertNull(affiliation.getEndedAt());
    identity.disable(NOW.plusSeconds(7));
    assertFalse(AffiliationValidity.isEffective(affiliation, identity, tenant));
  }
}
