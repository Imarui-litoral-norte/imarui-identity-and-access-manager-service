package br.com.imarui.ima.platform.openapi;

import org.springframework.context.annotation.*;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.tags.Tag;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.List;

@Configuration
public class IdentityOpenApiConfiguration {
    @Bean
    public OpenAPI identityApi() {
        Schema<Object> problem = new Schema<>().type("object")
            .addProperty("type", new StringSchema().format("uri-reference").description("Categoria RFC 9457; about:blank quando não especificada."))
            .addProperty("title", new StringSchema().description("Título HTTP do problema."))
            .addProperty("status", new IntegerSchema().description("Status HTTP da resposta."))
            .addProperty("detail", new StringSchema().description("Descrição sem dados pessoais ou detalhes internos."))
            .addProperty("instance", new StringSchema().format("uri-reference").description("URI da requisição, quando disponível."))
            .addProperty("code", new StringSchema().description("Código estável para tratamento pelo consumidor."))
            .addProperty("timestamp", new StringSchema().format("date-time").description("Instante UTC do problema."));
        problem.setRequired(List.of("title", "status", "code", "timestamp"));
        return new OpenAPI().info(new Info().title("Imarui IAM API").version("v1")
            .description("API do IAM Imaruí. O módulo Identity cobre cadastro, ciclo de vida, solicitações, Tenants e vínculos. Cada operação descreve acesso, efeitos, exemplos e erros observáveis. Datas usam ISO 8601/UTC com precisão de microssegundos. Navegação por feature em /docs."))
            .addTagsItem(new Tag().name("Identity").description("Cadastro e ciclo de vida de identidades; onboarding e validade contextual de vínculos. Os subgrupos separam as features e superfícies administrativas."))
            .components(new Components().addSchemas("ApiProblem", problem).addSecuritySchemes("bearerAuth", new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
                .description("JWT de sessão com sub UUID de Identity ACTIVE, sid, issuer/audience, iat e exp validados. Permissions são consultadas na política atual; claims antigas não concedem acesso. Não existe Scope ativo na sessão. Tokens anteriores à revogação permanecem inválidos após reativação.")));
    }
}
