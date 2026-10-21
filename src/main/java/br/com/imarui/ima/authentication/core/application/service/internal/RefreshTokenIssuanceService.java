package br.com.imarui.ima.authentication.core.application.service.internal;

import br.com.imarui.ima.authentication.core.application.result.IssuedRefreshToken;
import br.com.imarui.ima.authentication.core.domain.model.RefreshToken;
import br.com.imarui.ima.authentication.core.domain.model.Session;
import br.com.imarui.ima.authentication.core.port.ApplicationTimeProperties;
import br.com.imarui.ima.authentication.core.port.RefreshTokenGenerator;
import br.com.imarui.ima.authentication.core.port.RefreshTokenHashService;
import br.com.imarui.ima.authentication.core.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class RefreshTokenIssuanceService {
    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenHashService tokenHashService;
    private final RefreshTokenGenerator tokenGenerator;
    private final ApplicationTimeProperties timeProperties;

    public IssuedRefreshToken issue(Session session, Instant now) {
        String rawToken = tokenGenerator.generate();
        RefreshToken saved = refreshTokenRepository.save(RefreshToken.create(
                session,
                tokenHashService.hash(rawToken),
                java.time.Duration.between(now, session.getExpiresAt()),
                now
        ));
        return new IssuedRefreshToken(saved, rawToken);
    }
}
