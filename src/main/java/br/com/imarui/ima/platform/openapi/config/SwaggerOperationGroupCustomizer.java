package br.com.imarui.ima.platform.openapi.config;

import br.com.imarui.ima.platform.openapi.group.SwaggerOperationGroup;
import io.swagger.v3.oas.models.Operation;
import org.springdoc.core.customizers.GlobalOperationCustomizer;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;

@Component
public class SwaggerOperationGroupCustomizer implements GlobalOperationCustomizer {
    @Override
    public Operation customize(Operation operation, HandlerMethod handler) {
        var group = handler.getMethodAnnotation(SwaggerOperationGroup.class);
        if (group == null) group = AnnotatedElementUtils.findMergedAnnotation(handler.getBeanType(), SwaggerOperationGroup.class);
        if (group != null) {
            operation.addExtension("x-operation-group", group.value());
            operation.addExtension("x-operation-group-order", group.order());
        }
        return operation;
    }
}
