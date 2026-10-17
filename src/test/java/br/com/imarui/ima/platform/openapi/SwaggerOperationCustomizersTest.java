package br.com.imarui.ima.platform.openapi;

import br.com.imarui.ima.platform.openapi.config.*;
import br.com.imarui.ima.platform.openapi.group.SwaggerOperationGroup;
import br.com.imarui.ima.platform.openapi.security.RequiredPermission;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.method.HandlerMethod;
import static org.junit.jupiter.api.Assertions.*;

class SwaggerOperationCustomizersTest {
    @SwaggerOperationGroup(value="Classe",order=20)
    @RequiredPermission("CLASS_PERMISSION")
    static class DocumentedController {
        public void inherited() {}
        @SwaggerOperationGroup(value="Método",order=10)
        @RequiredPermission("METHOD_PERMISSION")
        public void overridden() {}
    }
    static class PlainController { public void plain() {} }

    @Test void methodMetadataOverridesClassMetadataAndFallbackWorks() throws Exception {
        var groups = new SwaggerOperationGroupCustomizer();
        var permissions = new RequiredPermissionOperationCustomizer();
        var controller = new DocumentedController();
        for (String method : List.of("inherited","overridden")) {
            var handler = new HandlerMethod(controller,DocumentedController.class.getMethod(method));
            var operation = permissions.customize(groups.customize(new Operation(),handler),handler);
            assertEquals(method.equals("inherited")?"Classe":"Método",operation.getExtensions().get("x-operation-group"));
            assertEquals(List.of(method.equals("inherited")?"CLASS_PERMISSION":"METHOD_PERMISSION"),operation.getExtensions().get("x-required-permissions"));
        }
    }
    @Test void permissionCustomizerComposesWithImmutableExistingSecurityWithoutDuplicatingBearer() throws Exception {
        var handler = new HandlerMethod(new DocumentedController(),DocumentedController.class.getMethod("inherited"));
        var operation = new Operation().security(List.of(new SecurityRequirement().addList("bearerAuth")));
        var permissions = new RequiredPermissionOperationCustomizer();
        assertDoesNotThrow(()->permissions.customize(operation,handler));
        assertEquals(1,operation.getSecurity().size());
    }
    @Test void unannotatedHandlersDoNotAcquireDocumentedPermissionsOrGroups() throws Exception {
        var handler = new HandlerMethod(new PlainController(),PlainController.class.getMethod("plain"));
        var operation = new Operation();
        new SwaggerOperationGroupCustomizer().customize(operation,handler);
        new RequiredPermissionOperationCustomizer().customize(operation,handler);
        assertNull(operation.getExtensions());
        assertNull(operation.getSecurity());
    }
}
