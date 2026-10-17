package br.com.imarui.ima.platform.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfiguration {
    @Bean
    public Clock clock() { return Clock.tick(Clock.systemUTC(), java.time.Duration.ofNanos(1000)); }
}
