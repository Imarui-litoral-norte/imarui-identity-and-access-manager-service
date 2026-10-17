package br.com.imarui.ima.platform.openapi.config;

import br.com.imarui.ima.platform.openapi.security.RequiredPermission;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import java.util.List;
import org.springdoc.core.customizers.GlobalOperationCustomizer;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;

/** Documentation metadata; enforcement remains exclusively in Spring Method Security. */
@Component
public class RequiredPermissionOperationCustomizer implements GlobalOperationCustomizer {
    @Override
    public Operation customize(Operation operation, HandlerMethod handler) {
        var annotation = handler.getMethodAnnotation(RequiredPermission.class);
        if (annotation == null) annotation = AnnotatedElementUtils.findMergedAnnotation(handler.getBeanType(), RequiredPermission.class);
        if (annotation != null) {
            var permissions = List.of(annotation.value());
            var security = new java.util.ArrayList<>(operation.getSecurity() == null
                    ? List.<SecurityRequirement>of() : operation.getSecurity());
            if (security.stream().noneMatch(item -> item.containsKey("bearerAuth"))) security.add(new SecurityRequirement().addList("bearerAuth"));
            operation.setSecurity(security);
            operation.addExtension("x-required-permissions", permissions);
            String text = "### Permissão necessária\n" + String.join(", ", permissions.stream().map(p -> "`" + p + "`").toList());
            operation.setDescription(operation.getDescription() == null ? text : operation.getDescription() + "\n\n" + text);
        }
        return operation;
    }
}
