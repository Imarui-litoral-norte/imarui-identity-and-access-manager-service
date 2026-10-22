package br.com.imarui.ima.platform.openapi;

import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.*;

@Configuration
public class AuthorizationOpenApiConfiguration {
  @Bean OpenApiCustomizer authorizationContracts() {
    return api -> {
      api.getComponents().addSecuritySchemes("serviceProof",new SecurityScheme().type(SecurityScheme.Type.APIKEY)
          .in(SecurityScheme.In.HEADER).name("Authorization")
          .description("Service <request-bound RS256 JWT>: independent of the human actorToken. Bind issuer/subject to the enrolled service, audience to the configured IAM audience + :services, kid to the registered public key, method/path/body hash, correlation_id and one-use jti. TTL <=60s. Generate in a trusted backend; never enter a private key in Swagger."));
      api.getPaths().forEach((path,item) -> {
        if(!path.startsWith("/api/v1/authorization/")) return;
        item.readOperations().forEach(operation -> {
          for(String status:new String[]{"400","401","403","404","409","413","429","503"}) {
            if(operation.getResponses().containsKey(status)) continue;
            operation.getResponses().addApiResponse(status,new ApiResponse().description(switch(status) {
              case "400" -> "Invalid contract or parameters";
              case "401" -> "Invalid technical proof, human session or reauthentication";
              case "403" -> "Permission, ownership, delegation, DENY or caller jurisdiction rejected";
              case "404" -> "Resource not found";
              case "409" -> "Concurrent version conflict or lifecycle/integrity conflict";
              case "413" -> "Technical request exceeds 64 KiB";
              case "429" -> "Distributed rate limit exceeded";
              default -> "Dependency/transaction unavailable; fail closed";
            }).content(new Content().addMediaType("application/problem+json",
                new MediaType().schema(new Schema<>().$ref("#/components/schemas/ApiProblem")))));
          }
          if(path.equals("/api/v1/authorization/service/decisions"))
            operation.getResponses().get("403").description("Evaluated DENY returns the decision schema as application/json; caller authorization errors use application/problem+json.")
                .getContent().addMediaType("application/json",new MediaType().schema(new Schema<>().$ref("#/components/schemas/PdpDecisionResponse")));
        });
      });
    };
  }
}
