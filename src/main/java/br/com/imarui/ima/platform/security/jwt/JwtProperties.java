package br.com.imarui.ima.platform.security.jwt;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "security.jwt")
public class JwtProperties {

    private String issuer = "imarui-authentication-service";
    private String secret;
    private long accessTokenExpirationSeconds = 3600;
}
