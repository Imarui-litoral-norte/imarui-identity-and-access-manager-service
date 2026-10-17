package br.com.imarui.ima.identity.api.contract.identity;

import br.com.imarui.ima.identity.support.IdentityIntegrationSupport;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IdentitySelfDeleteHttpContractTest extends IdentityIntegrationSupport {
    @Test void invalidReasonPreservesIdentityAndValidDeletionOnlyRemovesTheOwner() throws Exception {
        var other = service("other-owner@example.test");
        other.activate(earlier); identities.save(other);
        String ownerToken = token(reviewer.getId());
        var invalid = http("DELETE", "/api/v1/identities/me", "{\"reason\":\" \"}", ownerToken);
        assertEquals(400, invalid.statusCode(), invalid.body());
        assertTrue(identities.findById(reviewer.getId()).isPresent());
        assertEquals(0, history.count());
        assertEquals(0, revocations.count());
        var deleted = http("DELETE", "/api/v1/identities/me", null, ownerToken);
        assertEquals(204, deleted.statusCode(), deleted.body());
        assertEquals("", deleted.body());
        assertTrue(identities.findById(reviewer.getId()).isEmpty());
        assertTrue(identities.findById(other.getId()).isPresent());
        assertEquals(reviewer.getId().value(), history.findAll().get(0).getActorId());
        assertEquals(401, http("DELETE", "/api/v1/identities/me", null, ownerToken).statusCode());
    }
}
