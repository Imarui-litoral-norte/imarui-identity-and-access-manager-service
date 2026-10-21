package br.com.imarui.ima.authentication.core.port;

import java.time.Duration;

public interface ApplicationTimeProperties {
    Duration activationInvitationTtl();
    Duration sessionTtl();
    Duration refreshTokenTtl();
    Duration passwordRecoveryRequestTtl();
    Duration passwordChangeChallengeTtl();
}
