package br.com.imarui.ima.platform.security.jwt;

import lombok.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "security.jwt")
public class JwtProperties {
  private String issuer = "imarui-iam-service";
  private String audience = "imarui-iam";
  private String privateKeyFile;
  private java.util.List<String> verificationKeyFiles = java.util.List.of();
}
