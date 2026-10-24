package br.com.imarui.ima.identity.api.documentation;

import br.com.imarui.ima.platform.openapi.security.RequiredPermission;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import java.util.*;
import org.springdoc.core.customizers.GlobalOperationCustomizer;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import tools.jackson.databind.json.JsonMapper;

/** Documents the existing Identity HTTP behavior; contains no authorization enforcement. */
@Component
@Order(100)
public class IdentityOperationCustomizer implements GlobalOperationCustomizer {
    private final JsonMapper json;
    public IdentityOperationCustomizer(JsonMapper json) { this.json = json; }

    @Override
    public Operation customize(Operation operation, HandlerMethod handler) {
        if (!handler.getBeanType().getPackageName().startsWith("br.com.imarui.ima.identity.api.controller")) return operation;
        String controller = handler.getBeanType().getSimpleName();
        String method = handler.getMethod().getName();
        boolean publicCreation = controller.equals("IdentityApplicationController") && (method.equals("createUser") || method.equals("createService"));
        boolean ownerRead = controller.equals("IdentityController");
        boolean selfDelete = controller.equals("SelfIdentityController");
        operation.setOperationId("identity_" + controller.replace("Controller", "") + "_" + method);
        operation.addExtension("x-access-mode", publicCreation ? "PUBLIC" : ownerRead ? "OWNER_OR_PERMISSION" : selfDelete ? "AUTHENTICATED_OWNER" : "PERMISSION");
        if (ownerRead) operation.addExtension("x-alternative-permissions", List.of("IDENTITY_READ"));
        operation.setSecurity(publicCreation ? new ArrayList<>() : new ArrayList<>(List.of(new SecurityRequirement().addList("bearerAuth"))));

        var errors = new LinkedHashMap<String, List<String>>();
        if (!method.equals("findAll")) errors.put("400", List.of("HTTP_400", "IDENTITY_VALIDATION_ERROR"));
        if (!publicCreation) {
            errors.put("401", List.of("AUTHENTICATION_REQUIRED", "CURRENT_ACTOR_REQUIRED"));
            if (!selfDelete) errors.put("403", List.of("IDENTITY_ACCESS_DENIED"));
        }
        if (!method.startsWith("create") && !method.equals("findAll")) {
            String code = controller.contains("Tenant") ? "TENANT_NOT_FOUND" : controller.contains("Application") ? "IDENTITY_APPLICATION_NOT_FOUND" : "IDENTITY_NOT_FOUND";
            errors.put("404", method.equals("approve") ? List.of(code, "TENANT_NOT_FOUND") : List.of(code));
        }
        if (!method.equals("findById") && !method.equals("findAll")) {
            String code = controller.contains("Tenant") ? switch(method) {
                case "create" -> "TENANT_CODE_ALREADY_EXISTS";
                case "activate" -> "TENANT_ACTIVATION_NOT_ALLOWED";
                case "disable" -> "TENANT_DISABLE_NOT_ALLOWED";
                case "reactivate" -> "TENANT_REACTIVATION_NOT_ALLOWED";
                default -> "TENANT_RENAME_NOT_ALLOWED";
            } : "IDENTITY_INVALID_STATE";
            errors.put("409", selfDelete || publicCreation ? List.of("IDENTITY_CONFLICT") : List.of(code, "IDENTITY_CONFLICT"));
        }
        errors.put("500", List.of("INTERNAL_SERVER_ERROR"));
        errors.forEach((status, codes) -> operation.getResponses().addApiResponse(status, error(status, codes)));
        if (operation.getParameters() != null) operation.getParameters().forEach(parameter -> {
            if ("path".equals(parameter.getIn())) {
                parameter.setDescription("UUID do recurso. O identificador não concede acesso; autenticação e permissões continuam obrigatórias.");
                parameter.setSchema(new StringSchema().format("uuid"));
                parameter.setExample("11111111-1111-4111-8111-111111111111");
            }
        });
        if (operation.getRequestBody() != null && operation.getRequestBody().getContent() != null) {
            operation.getRequestBody().setDescription(requestDescription(method, controller));
            operation.getRequestBody().getContent().values().forEach(media -> requestExamples(method, controller).forEach((name, value) -> media.addExamples(name, example(value))));
        }
        String successCode = selfDelete ? "204" : method.startsWith("create") ? "201" : "200";
        var success = operation.getResponses().get(successCode);
        if (success != null) {
            success.setDescription(selfDelete ? "Exclusão confirmada atomicamente; resposta sem corpo." : "Operação concluída. Os exemplos representam dados fictícios.");
            if (selfDelete) success.setContent(null);
            else if (success.getContent() != null) success.getContent().values().forEach(media ->
                responseExamples(controller, method).forEach((name, value) -> media.addExamples(name, example(value))));
            if (method.startsWith("create")) success.addHeaderObject("Location", new Header().description("URI do recurso criado: relativa para IdentityApplication e absoluta para Tenant. A leitura exige a permissão da superfície de consulta.").schema(new StringSchema().format("uri-reference")));
        }
        return operation;
    }
    private ApiResponse error(String status, List<String> codes) {
        var media = new MediaType().schema(new Schema<>().$ref("#/components/schemas/ApiProblem"));
        codes.forEach(code -> media.addExamples(code, new Example().summary(code).value(Map.of(
            "type", "about:blank", "title", status.equals("409") ? "Conflict" : "Erro HTTP",
            "status", Integer.parseInt(status), "detail", "Consulte a regra da operação e o código do erro.",
            "code", code, "timestamp", "2026-10-17T12:00:00Z"))));
        return new ApiResponse().description("Códigos possíveis: " + String.join(", ", codes) + ". Sem valores pessoais ou detalhes SQL.")
            .content(new Content().addMediaType("application/problem+json", media));
    }
    private Example example(String value) { return new Example().value(json.readValue(value, Object.class)); }
    private String requestDescription(String method, String controller) {
        return switch(method) {
            case "approve" -> "Tenant ativo e tipo do vínculo. Os campos específicos são condicionais ao tipo; enviar campos de outro tipo é inválido.";
            case "reject" -> "Motivo obrigatório: 1–500 caracteres após aparar.";
            case "delete" -> "Corpo e motivo opcionais. O alvo sempre vem da identidade autenticada; não há campo de ID de destino.";
            case "rename" -> "Novo nome do Tenant. Código e estado não são editáveis por esta operação.";
            default -> controller.contains("Application") ? "Usuários exigem CPF/CNPJ válido; serviços não recebem documento. Apelido e e-mail são únicos globalmente." : "Código estável e nome do novo Tenant.";
        };
    }
    private Map<String,String> requestExamples(String method, String controller) {
        if (method.equals("createUser")) return Map.of("Pessoa", "{\"name\":\"Pessoa Exemplo\",\"document\":\"52998224725\",\"nickname\":\"person-example\",\"email\":\"pessoa@example.test\"}", "Empresa", "{\"name\":\"Empresa Exemplo\",\"document\":\"11222333000181\",\"nickname\":\"company-example\",\"email\":\"empresa@example.test\"}");
        if (method.equals("createService")) return Map.of("Serviço", "{\"name\":\"Integração Exemplo\",\"nickname\":\"service-example\",\"email\":\"servico@example.test\",\"description\":\"Integração\"}");
        if (method.equals("approve")) return IdentityApiExamples.APPROVAL_REQUESTS;
        if (method.equals("reject")) return Map.of("Motivo", "{\"reason\":\"Dados insuficientes para o cadastro\"}");
        if (method.equals("delete")) return Map.of("Solicitação própria", "{\"reason\":\"Encerramento solicitado pelo titular\"}");
        if (method.equals("rename")) return Map.of("Renomear", "{\"name\":\"Unidade Litoral Norte\"}");
        if (method.equals("create")) return Map.of("Novo Tenant", "{\"code\":\"LITORAL_NORTE\",\"name\":\"Unidade Litoral Norte\"}");
        return Map.of();
    }
    private Map<String,String> responseExamples(String controller, String method) {
        if (controller.contains("Tenant")) return Map.of("Tenant", IdentityApiExamples.tenant(method));
        if (controller.contains("Application")) return Map.of("Solicitação", method.equals("findAll") ? "[]" : IdentityApiExamples.application(method));
        return IdentityApiExamples.identities(method);
    }
}
