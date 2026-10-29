package br.com.imarui.ima.identity.api.contract.tenant;

import br.com.imarui.ima.identity.support.IdentityIntegrationSupport;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TenantDirectoryHttpContractTest extends IdentityIntegrationSupport {
    private String createTenant(String code, String name, String admin, boolean active) throws Exception {
        var response = http("POST", "/api/v1/admin/tenants", json.writeValueAsString(java.util.Map.of("code", code, "name", name)), admin);
        assertEquals(201, response.statusCode(), response.body());
        String id = json.readTree(response.body()).path("id").asText();
        if (active) assertEquals(200, http("POST", "/api/v1/admin/tenants/" + id + "/activate", null, admin).statusCode());
        return id;
    }

    @Test void searchUsesNamesAndCodesActiveDefaultsAndStableServerPagination() throws Exception {
        String admin = token(reviewer.getId(), "TENANT_MANAGE", "TENANT_READ");
        createTenant("IMARUI", "Imarui Distribuidora", admin, true);
        createTenant("IMARUI_2", "Imarui Filial", admin, true);
        createTenant("IMARUI_PENDING", "Imarui Pendente", admin, false);
        var response = http("GET", "/api/v1/tenants?q=imarui&size=1", null, admin);
        assertEquals(200, response.statusCode(), response.body());
        var first = json.readTree(response.body());
        assertEquals(2, first.path("total").asInt());
        assertEquals(1, first.path("items").size());
        assertEquals("Imarui Distribuidora", first.path("items").get(0).path("name").asText());
        assertEquals("ACTIVE", first.path("items").get(0).path("status").asText());
        var second = json.readTree(http("GET", "/api/v1/tenants?q=IMARUI&page=1&size=1", null, admin).body());
        assertEquals("Imarui Filial", second.path("items").get(0).path("name").asText());
        var all = json.readTree(http("GET", "/api/v1/tenants?q=imarui&status=ALL", null, admin).body());
        assertEquals(3, all.path("total").asInt());
        var pending = json.readTree(http("GET", "/api/v1/tenants?status=PENDING", null, admin).body());
        assertEquals(1, pending.path("total").asInt());
        var byCode = json.readTree(http("GET", "/api/v1/tenants?q=imarui_2", null, admin).body());
        assertEquals(1, byCode.path("total").asInt());
        var literal = json.readTree(http("GET", "/api/v1/tenants?q=%25", null, admin).body());
        assertEquals(0, literal.path("total").asInt(), "SQL wildcard must be searched literally");
    }

    @Test void scopedReaderSeesOnlyAuthorizedTenantsAndCountsDoNotExposeOtherCompanies() throws Exception {
        String admin = token(reviewer.getId(), "TENANT_MANAGE", "TENANT_READ");
        createTenant("PRIVATE", "Private company", admin, true);
        var subject = identities.save(service("scoped-directory@example.test"));
        firstAccess(subject.getId());
        String scoped = token(subject.getId(), "TENANT_READ");
        var allowedScope = jdbc.queryForObject("select scope_id from authorization_tenant_scopes where tenant_id=?", java.util.UUID.class, tenant.getId().value());
        new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(tx -> {
            Long role = jdbc.queryForObject("select role_id from authorization_identity_roles where identity_id=?", Long.class, subject.getId().value());
            jdbc.update("delete from authorization_identity_roles where identity_id=?", subject.getId().value());
            jdbc.update("insert into authorization_identity_roles(identity_id,role_id,authorization_scope_id) values(?,?,?)", subject.getId().value(), role, allowedScope);
        });
        var response = http("GET", "/api/v1/tenants?status=ALL", null, scoped);
        assertEquals(200, response.statusCode(), response.body());
        var body = json.readTree(response.body());
        assertEquals(1, body.path("total").asInt());
        assertEquals(tenant.getId().toString(), body.path("items").get(0).path("id").asText());
        assertFalse(response.body().contains("Private company"));
        var hidden = json.readTree(http("GET", "/api/v1/tenants?q=Private&status=ALL", null, scoped).body());
        assertEquals(0, hidden.path("total").asInt());
        assertTrue(hidden.path("items").isEmpty());
        jdbc.update("delete from authorization_identity_roles where identity_id=?", subject.getId().value());
        assertEquals(403, http("GET", "/api/v1/tenants", null, scoped).statusCode(), "Existing JWT must not retain a removed directory grant");
    }

    @Test void directoryRejectsAnonymousUnprivilegedAndInvalidRequests() throws Exception {
        assertEquals(401, http("GET", "/api/v1/tenants", null, null).statusCode());
        String reader = token(reviewer.getId(), "TENANT_READ");
        for (String query : new String[]{"size=101", "size=0", "page=-1", "status=UNKNOWN", "q=" + "a".repeat(151)})
            assertEquals(400, http("GET", "/api/v1/tenants?" + query, null, reader).statusCode(), query);
        String unprivileged = token(reviewer.getId());
        assertEquals(403, http("GET", "/api/v1/tenants", null, unprivileged).statusCode());
    }

    @Test void explicitDenialExcludesCompanyEvenForInheritedReaders() throws Exception {
        String admin = token(reviewer.getId(), "TENANT_MANAGE", "TENANT_READ");
        String hiddenId = createTenant("HIDDEN", "Hidden by policy", admin, true);
        var hiddenScope = jdbc.queryForObject("select scope_id from authorization_tenant_scopes where tenant_id=?", java.util.UUID.class, java.util.UUID.fromString(hiddenId));
        jdbc.update("insert into authorization_denies(id,permission_code,scope_id,identity_id,active,reason,created_at,updated_at) values(?,'TENANT_READ',?,?,true,'Restricted company',current_timestamp,current_timestamp)", java.util.UUID.randomUUID(), hiddenScope, reviewer.getId().value());
        var response = http("GET", "/api/v1/tenants?status=ALL", null, admin);
        assertEquals(200, response.statusCode(), response.body());
        assertEquals(1, json.readTree(response.body()).path("total").asInt());
        assertFalse(response.body().contains(hiddenId));
        assertFalse(response.body().contains("Hidden by policy"));
    }
}
