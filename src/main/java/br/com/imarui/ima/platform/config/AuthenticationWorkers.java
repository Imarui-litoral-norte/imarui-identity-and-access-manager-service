package br.com.imarui.ima.platform.config;

import br.com.imarui.ima.authentication.infra.integration.RevocationProcessor;
import br.com.imarui.ima.authentication.infra.mail.ChallengeDeliveryWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.*;

@Configuration
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnProperty(
    name = "security.auth.workers-enabled",
    havingValue = "true",
    matchIfMissing = true)
public class AuthenticationWorkers {
  private final io.micrometer.core.instrument.MeterRegistry metrics;
  private final RevocationProcessor revocations;
  private final ChallengeDeliveryWorker delivery;

  @Scheduled(fixedDelayString = "${security.auth.revocation-poll-ms:1000}")
  public void revoke() {
    try {
      revocations.process();
    } catch (RuntimeException failure) {
      metrics.counter("iiam.authentication.worker.failures", "worker", "revocation").increment();
    }
  }

  @Scheduled(fixedDelayString = "${security.auth.delivery-poll-ms:5000}")
  public void deliver() {
    try {
      delivery.processOne();
    } catch (RuntimeException failure) {
      metrics.counter("iiam.authentication.worker.failures", "worker", "delivery").increment();
    }
  }
}
