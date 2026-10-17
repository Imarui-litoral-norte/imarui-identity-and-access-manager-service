package br.com.imarui.ima.identity.api.contract.tenant;

import br.com.imarui.ima.identity.support.IdentityIntegrationSupport;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TenantHttpContractTest extends IdentityIntegrationSupport {
    @Test void tenantCreationRenameAndLifecycleMatchPublishedContract() throws Exception {
        String admin = token(reviewer.getId(), "TENANT_MANAGE", "TENANT_READ");
        var created = http("POST", "/api/v1/admin/tenants", "{\"code\":\"new_tenant\",\"name\":\" New Tenant \"}", admin);
        assertEquals(201, created.statusCode(), created.body());
        var data = json.readTree(created.body());
        assertEquals("NEW_TENANT", data.path("code").asText());
        assertEquals("New Tenant", data.path("name").asText());
        assertEquals("PENDING", data.path("status").asText());
        String id = data.path("id").asText();
        assertEquals("/api/v1/tenants/" + id, java.net.URI.create(created.headers().firstValue("Location").orElseThrow()).getPath());
        assertEquals(200, http("GET", "/api/v1/tenants/" + id, null, admin).statusCode());
        var active = http("POST", "/api/v1/admin/tenants/" + id + "/activate", null, admin);
        assertEquals(200, active.statusCode(), active.body());
        String firstActivation = json.readTree(active.body()).path("activatedAt").asText();
        var renamed = http("PATCH", "/api/v1/admin/tenants/" + id + "/name", "{\"name\":\"Renamed Tenant\"}", admin);
        assertEquals("Renamed Tenant", json.readTree(renamed.body()).path("name").asText());
        assertEquals(200, http("POST", "/api/v1/admin/tenants/" + id + "/disable", null, admin).statusCode());
        var forbiddenRename = http("PATCH", "/api/v1/admin/tenants/" + id + "/name", "{\"name\":\"Wrong state\"}", admin);
        assertEquals(409, forbiddenRename.statusCode(), forbiddenRename.body());
        assertEquals("TENANT_RENAME_NOT_ALLOWED", json.readTree(forbiddenRename.body()).path("code").asText());
        var restored = http("POST", "/api/v1/admin/tenants/" + id + "/reactivate", null, admin);
        assertEquals(200, restored.statusCode(), restored.body());
        assertEquals(firstActivation, json.readTree(restored.body()).path("activatedAt").asText());
        assertTrue(json.readTree(restored.body()).path("disabledAt").isNull());
    }
    @Test void tenantValidationDuplicateAndPermissionFailuresDoNotWrite() throws Exception {
        String admin = token(reviewer.getId(), "TENANT_MANAGE", "TENANT_READ");
        var duplicate = http("POST", "/api/v1/admin/tenants", "{\"code\":\"test\",\"name\":\"Duplicate\"}", admin);
        assertEquals(409, duplicate.statusCode(), duplicate.body());
        assertEquals("TENANT_CODE_ALREADY_EXISTS", json.readTree(duplicate.body()).path("code").asText());
        assertEquals(400, http("POST", "/api/v1/admin/tenants", "{\"code\":\"1wrong\",\"name\":\"Invalid\"}", admin).statusCode());
        assertEquals(400, http("GET", "/api/v1/tenants/not-a-uuid", null, admin).statusCode());
        assertEquals(403, http("POST", "/api/v1/admin/tenants/" + tenant.getId() + "/disable", null, token(reviewer.getId())).statusCode());
        assertTrue(tenants.findById(tenant.getId()).orElseThrow().isActive());
        assertEquals(0, revocations.count());
    }
}
