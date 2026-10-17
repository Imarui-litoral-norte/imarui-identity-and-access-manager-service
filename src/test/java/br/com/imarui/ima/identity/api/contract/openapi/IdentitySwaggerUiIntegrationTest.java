package br.com.imarui.ima.identity.api.contract.openapi;

import br.com.imarui.ima.identity.support.IdentityIntegrationSupport;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IdentitySwaggerUiIntegrationTest extends IdentityIntegrationSupport {
    @Test void docsUsesLocalSwaggerAssetsAndDoesNotPersistCredentials() throws Exception {
        var response = http("GET", "/docs", null, null);
        assertEquals(200, response.statusCode(), response.body());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").contains("text/html"));
        assertTrue(response.body().contains("Imarui IAM"));
        assertTrue(response.body().contains("/v3/api-docs"));
        assertTrue(response.body().contains("persistAuthorization: false"));
        for (String asset : new String[]{"/openapi-custom/swagger-operation-groups.js","/openapi-custom/swagger-operation-groups.css","/swagger-ui/swagger-ui-bundle.js","/swagger-ui/swagger-ui.css"}) {
            var resource = http("GET", asset, null, null);
            assertEquals(200, resource.statusCode(), asset);
            assertFalse(resource.body().isBlank(), asset);
        }
    }
    @Test void groupingAssetUsesSpecMetadataAndPreservesUserExpansion() throws Exception {
        var response = http("GET", "/openapi-custom/swagger-operation-groups.js", null, null);
        assertEquals(200,response.statusCode());
        assertTrue(response.body().contains("x-operation-group-order"));
        assertTrue(response.body().contains("x-operation-group"));
        assertTrue(response.body().contains("swagger-ui-ready"));
    }
}
