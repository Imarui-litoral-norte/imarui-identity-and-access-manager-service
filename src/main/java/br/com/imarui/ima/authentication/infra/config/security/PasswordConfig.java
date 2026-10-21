package br.com.imarui.ima.authentication.infra.config.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class PasswordConfig {

    @Bean
    public PasswordEncoder passwordEncoder(@org.springframework.beans.factory.annotation.Value("${security.auth.bcrypt-cost:12}") int cost) {
        if(cost<4 || cost>16)throw new IllegalStateException("BCrypt cost must be between 4 and 16.");
        return new BCryptPasswordEncoder(cost);
    }
}
