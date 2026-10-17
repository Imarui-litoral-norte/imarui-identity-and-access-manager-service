package br.com.imarui.ima.identity.api.contract.openapi;

import br.com.imarui.ima.identity.support.IdentityIntegrationSupport;
import br.com.imarui.ima.identity.api.dto.identityapplication.request.*;
import br.com.imarui.ima.identity.api.dto.identityapplication.request.create.CreateIdentityApplicationRequest;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import jakarta.validation.Validator;
import tools.jackson.databind.JsonNode;
import static org.junit.jupiter.api.Assertions.*;

class IdentityOpenApiIntegrationTest extends IdentityIntegrationSupport {
    @Autowired Validator validator;

    static Stream<Arguments> identityOperations() {
        return Stream.of(
            Arguments.of("/api/v1/identities/{identityId}","get","200","OWNER_OR_PERMISSION",""),
            Arguments.of("/api/v1/identities/me","delete","204","AUTHENTICATED_OWNER",""),
            Arguments.of("/api/v1/admin/identities/{identityId}/activate","post","200","PERMISSION","IDENTITY_MANAGE"),
            Arguments.of("/api/v1/admin/identities/{identityId}/disable","post","200","PERMISSION","IDENTITY_MANAGE"),
            Arguments.of("/api/v1/admin/identities/{identityId}/reactivate","post","200","PERMISSION","IDENTITY_MANAGE"),
            Arguments.of("/api/v1/identity-applications","post","201","PUBLIC",""),
            Arguments.of("/api/v1/identity-applications/{identityApplicationId}","get","200","PERMISSION","IDENTITY_APPLICATION_READ"),
            Arguments.of("/api/v1/admin/identity-applications","get","200","PERMISSION","IDENTITY_APPLICATION_REVIEW"),
            Arguments.of("/api/v1/admin/identity-applications/{identityApplicationId}","get","200","PERMISSION","IDENTITY_APPLICATION_REVIEW"),
            Arguments.of("/api/v1/admin/identity-applications/{identityApplicationId}/approve","post","200","PERMISSION","IDENTITY_APPLICATION_REVIEW"),
            Arguments.of("/api/v1/admin/identity-applications/{identityApplicationId}/reject","post","200","PERMISSION","IDENTITY_APPLICATION_REVIEW"),
            Arguments.of("/api/v1/tenants/{tenantId}","get","200","PERMISSION","TENANT_READ"),
            Arguments.of("/api/v1/admin/tenants","post","201","PERMISSION","TENANT_MANAGE"),
            Arguments.of("/api/v1/admin/tenants/{tenantId}/activate","post","200","PERMISSION","TENANT_MANAGE"),
            Arguments.of("/api/v1/admin/tenants/{tenantId}/disable","post","200","PERMISSION","TENANT_MANAGE"),
            Arguments.of("/api/v1/admin/tenants/{tenantId}/reactivate","post","200","PERMISSION","TENANT_MANAGE"),
            Arguments.of("/api/v1/admin/tenants/{tenantId}/name","patch","200","PERMISSION","TENANT_MANAGE")
        );
    }

    JsonNode spec() throws Exception {
        var response = http("GET", "/v3/api-docs", null, null);
        assertEquals(200, response.statusCode(), response.body());
        return json.readTree(response.body());
    }

    @ParameterizedTest(name="{1} {0}")
    @MethodSource("identityOperations")
    void everyEndpointDocumentsItsContractAndAccess(String path, String method, String success, String access, String permission) throws Exception {
        var operation = spec().path("paths").path(path).path(method);
        assertFalse(operation.isMissingNode(), path);
        assertTrue(operation.path("summary").asText().length() > 8);
        assertTrue(operation.path("description").asText().length() > 80);
        assertEquals("Identity", operation.path("tags").get(0).asText());
        assertFalse(operation.path("x-operation-group").asText().isBlank());
        assertTrue(operation.path("x-operation-group-order").asInt() > 0);
        assertEquals(access, operation.path("x-access-mode").asText());
        assertTrue(operation.path("responses").has(success));
        assertTrue(operation.path("responses").has("500"));
        if (access.equals("PUBLIC")) {
            assertEquals(0, operation.path("security").size());
            assertFalse(operation.path("responses").has("403"));
            assertFalse(operation.path("responses").has("404"));
        } else {
            assertTrue(operation.path("security").get(0).has("bearerAuth"));
            assertTrue(operation.path("responses").has("401"));
        }
        if (!permission.isEmpty()) assertEquals(permission, operation.path("x-required-permissions").get(0).asText());
        if (access.equals("OWNER_OR_PERMISSION")) {
            assertFalse(operation.has("x-required-permissions"));
            assertEquals("IDENTITY_READ", operation.path("x-alternative-permissions").get(0).asText());
        }
        if (success.equals("204")) assertFalse(operation.path("responses").path(success).has("content"));
        else assertTrue(operation.path("responses").path(success).path("content").size() > 0);
        if (success.equals("201")) assertTrue(operation.path("responses").path(success).path("headers").has("Location"));
        for (var parameter : operation.path("parameters")) {
            if (parameter.path("in").asText().equals("path")) assertEquals("uuid", parameter.path("schema").path("format").asText());
        }
        for (String status : List.of("400","401","403","404","409","500")) {
            if (!operation.path("responses").has(status)) continue;
            var content = operation.path("responses").path(status).path("content");
            assertEquals("#/components/schemas/ApiProblem",content.path("application/problem+json").path("schema").path("$ref").asText());
            assertFalse(content.has("application/json"));
            assertTrue(content.path("application/problem+json").path("examples").size() > 0);
        }
    }

    @Test void requestExamplesDeserializeAndRespectActualValidation() throws Exception {
        var paths = spec().path("paths");
        var create = paths.path("/api/v1/identity-applications").path("post").path("requestBody").path("content").path("application/json").path("examples");
        assertEquals(3, create.size());
        for (var example : create) {
            var request = json.readValue(example.path("value").toString(), CreateIdentityApplicationRequest.class);
            assertTrue(validator.validate(request).isEmpty());
        }
        var approval = paths.path("/api/v1/admin/identity-applications/{identityApplicationId}/approve").path("post").path("requestBody").path("content").path("application/json").path("examples");
        assertEquals(5, approval.size());
        for (var example : approval) {
            var request = json.readValue(example.path("value").toString(), ApproveIdentityApplicationRequest.class);
            assertTrue(validator.validate(request).isEmpty());
            assertNotNull(request.affiliation().toSelection());
        }
    }

    @Test void schemasDescribePolymorphismErrorsAndReadOnlyFields() throws Exception {
        var schemas = spec().path("components").path("schemas");
        assertEquals(3, schemas.path("IdentityDetailsResponse").path("oneOf").size());
        assertEquals(3, schemas.path("CreateIdentityApplicationRequest").path("oneOf").size());
        assertTrue(schemas.path("IdentityResponse").path("properties").path("id").path("readOnly").asBoolean());
        assertTrue(schemas.path("ApiProblem").path("properties").has("code"));
        assertEquals("date-time", schemas.path("ApiProblem").path("properties").path("timestamp").path("format").asText());
        assertFalse(schemas.path("IdentityResponse").path("properties").has("password"));
        assertFalse(schemas.path("IdentityResponse").path("properties").has("refreshToken"));
    }

    @Test void tenantDeletionAndPublicAdministrativeAccessAreNotAdvertised() throws Exception {
        var paths = spec().path("paths");
        assertFalse(paths.path("/api/v1/admin/tenants/{tenantId}").has("delete"));
        assertFalse(paths.path("/api/v1/tenants/{tenantId}").has("delete"));
        assertFalse(paths.path("/api/v1/admin/tenants").path("post").path("security").isEmpty());
    }
}
