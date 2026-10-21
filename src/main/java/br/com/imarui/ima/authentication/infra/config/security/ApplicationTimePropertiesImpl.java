package br.com.imarui.ima.authentication.infra.config.security;

import br.com.imarui.ima.authentication.core.port.ApplicationTimeProperties;
import jakarta.validation.constraints.NotNull;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Component
@Validated
@Setter
@ConfigurationProperties(prefix = "security.auth")
public class ApplicationTimePropertiesImpl implements ApplicationTimeProperties {

    @NotNull
    private Duration activationInvitationTtl = Duration.ofHours(24);

    @Override public Duration activationInvitationTtl() { return activationInvitationTtl; }

    @jakarta.validation.constraints.AssertTrue(message="Challenge TTLs must be positive and at most seven days.")
    public boolean isChallengeTtlsValid() { return validChallengeTtl(activationInvitationTtl) && validChallengeTtl(passwordRecoveryRequestTtl); }
    private boolean validChallengeTtl(Duration value) { return value != null && !value.isNegative() && !value.isZero() && value.compareTo(Duration.ofDays(7)) <= 0; }

    @NotNull
    private Duration refreshTokenTtl;

    @NotNull
    private Duration sessionTtl;

    @NotNull
    private Duration passwordRecoveryRequestTtl;

    @NotNull
    private Duration passwordChangeChallengeTtl;

    @Override
    public Duration sessionTtl() {
        return sessionTtl;
    }

    @Override
    public Duration refreshTokenTtl() {
        return refreshTokenTtl;
    }

    @Override
    public Duration passwordRecoveryRequestTtl() {
        return passwordRecoveryRequestTtl;
    }

    @Override
    public Duration passwordChangeChallengeTtl() {
        return passwordChangeChallengeTtl;
    }


}