package br.com.imarui.ima.identity.api.documentation;
import java.util.*;
import tools.jackson.databind.json.JsonMapper;

/** Synthetic examples; never loaded from operational records. */
final class IdentityApiExamples {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    static final Map<String,String> APPLICATION_REQUESTS = Map.of("Pessoa", "{\"type\": \"PERSON\", \"nickname\": \"person-example\", \"nickname\": \"person-example\", \"fullName\": \"Pessoa Exemplo\", \"cpf\": \"529.982.247-25\", \"email\": \"pessoa@example.test\", \"birthDate\": \"1990-05-10\", \"phoneNumber\": \"48999999999\"}",
"Empresa", "{\"type\": \"LEGAL_ENTITY\", \"nickname\": \"company-example\", \"nickname\": \"company-example\", \"legalName\": \"Empresa Exemplo Ltda\", \"tradeName\": \"Empresa Exemplo\", \"cnpj\": \"11.222.333/0001-81\", \"email\": \"empresa@example.test\"}",
"Serviço", "{\"type\": \"SERVICE\", \"nickname\": \"service-example\", \"nickname\": \"service-example\", \"name\": \"Integração Exemplo\", \"description\": \"Identidade técnica da integração\", \"email\": \"servico@example.test\"}");
    static final Map<String,String> APPROVAL_REQUESTS = Map.of("EMPLOYEE", "{\"tenantId\": \"33333333-3333-4333-8333-333333333333\", \"affiliation\": {\"type\": \"EMPLOYEE\", \"registration\": \"EMP-100\"}}",
"CUSTOMER", "{\"tenantId\": \"33333333-3333-4333-8333-333333333333\", \"affiliation\": {\"type\": \"CUSTOMER\", \"customerCode\": \"CLI-100\"}}",
"SUPPLIER", "{\"tenantId\": \"33333333-3333-4333-8333-333333333333\", \"affiliation\": {\"type\": \"SUPPLIER\", \"supplierCode\": \"FOR-100\"}}",
"PARTNER", "{\"tenantId\": \"33333333-3333-4333-8333-333333333333\", \"affiliation\": {\"type\": \"PARTNER\"}}",
"EXTERNAL", "{\"tenantId\": \"33333333-3333-4333-8333-333333333333\", \"affiliation\": {\"type\": \"EXTERNAL\"}}");
    private static final Map<String,String> IDENTITIES = Map.of("Pessoa", "{\"id\": \"11111111-1111-4111-8111-111111111111\", \"kind\": \"PERSON\", \"primaryEmail\": \"pessoa@example.test\", \"status\": \"ACTIVE\", \"createdAt\": \"2026-10-17T12:00:00Z\", \"updatedAt\": \"2026-10-17T12:00:00Z\", \"activatedAt\": \"2026-10-17T12:00:00Z\", \"disabledAt\": null, \"details\": {\"fullName\": \"Pessoa Exemplo\", \"cpf\": \"52998224725\", \"birthDate\": \"1990-05-10\", \"phoneNumber\": \"48999999999\"}}",
"Empresa", "{\"id\": \"11111111-1111-4111-8111-111111111111\", \"kind\": \"LEGAL_ENTITY\", \"primaryEmail\": \"empresa@example.test\", \"status\": \"ACTIVE\", \"createdAt\": \"2026-10-17T12:00:00Z\", \"updatedAt\": \"2026-10-17T12:00:00Z\", \"activatedAt\": \"2026-10-17T12:00:00Z\", \"disabledAt\": null, \"details\": {\"legalName\": \"Empresa Exemplo Ltda\", \"tradeName\": \"Empresa Exemplo\", \"cnpj\": \"11222333000181\"}}",
"Serviço", "{\"id\": \"11111111-1111-4111-8111-111111111111\", \"kind\": \"SERVICE\", \"primaryEmail\": \"servico@example.test\", \"status\": \"ACTIVE\", \"createdAt\": \"2026-10-17T12:00:00Z\", \"updatedAt\": \"2026-10-17T12:00:00Z\", \"activatedAt\": \"2026-10-17T12:00:00Z\", \"disabledAt\": null, \"details\": {\"name\": \"Integração Exemplo\", \"description\": \"Identidade técnica da integração\"}}");
    private static final String TENANT = "{\"id\": \"33333333-3333-4333-8333-333333333333\", \"code\": \"LITORAL_NORTE\", \"name\": \"Unidade Litoral Norte\", \"status\": \"ACTIVE\", \"createdAt\": \"2026-10-17T12:00:00Z\", \"updatedAt\": \"2026-10-17T12:00:00Z\", \"activatedAt\": \"2026-10-17T12:00:00Z\", \"disabledAt\": null}";
    private static final String APPLICATION = "{\"id\": \"22222222-2222-4222-8222-222222222222\", \"type\": \"SERVICE\", \"nickname\": \"service-example\", \"nickname\": \"service-example\", \"email\": \"servico@example.test\", \"status\": \"PENDING\", \"details\": {\"name\": \"Integração Exemplo\", \"description\": \"Identidade técnica da integração\"}, \"requestedAt\": \"2026-10-17T12:00:00Z\", \"reviewedAt\": null, \"reviewedByIdentityId\": null, \"rejectionReason\": null, \"resolvedIdentityId\": null, \"assignedTenantId\": null, \"resultingAffiliationId\": null}";
    private IdentityApiExamples() {}
    @SuppressWarnings("unchecked")
    private static Map<String,Object> mutable(String json) { return new LinkedHashMap<>(JSON.readValue(json, Map.class)); }
    static Map<String,String> identities(String method) {
        var examples = new LinkedHashMap<String,String>();
        IDENTITIES.forEach((name, source) -> {
            var value=mutable(source);
            if (method.equals("disable")) { value.put("status","DISABLED"); value.put("disabledAt","2026-10-17T12:00:00Z"); }
            examples.put(name,JSON.writeValueAsString(value));
        });
        return examples;
    }
    static String tenant(String method) {
        var value=mutable(TENANT);
        if (method.equals("create")) { value.put("status","PENDING"); value.put("activatedAt",null); }
        if (method.equals("disable")) { value.put("status","DISABLED"); value.put("disabledAt","2026-10-17T12:00:00Z"); }
        return JSON.writeValueAsString(value);
    }
    static String application(String method) {
        var value=mutable(APPLICATION);
        if (method.equals("approve") || method.equals("reject")) {
            value.put("status",method.equals("approve")?"APPROVED":"REJECTED");
            value.put("reviewedAt","2026-10-17T12:00:00Z"); value.put("reviewedByIdentityId","11111111-1111-4111-8111-111111111111");
            if (method.equals("approve")) {
                value.put("resolvedIdentityId","11111111-1111-4111-8111-111111111111"); value.put("assignedTenantId","33333333-3333-4333-8333-333333333333"); value.put("resultingAffiliationId","44444444-4444-4444-8444-444444444444");
            } else value.put("rejectionReason","Dados insuficientes para o cadastro");
        }
        return JSON.writeValueAsString(value);
    }
}
