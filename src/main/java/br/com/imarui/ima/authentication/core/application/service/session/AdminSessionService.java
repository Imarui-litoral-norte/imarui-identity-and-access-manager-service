package br.com.imarui.ima.authentication.core.application.service.session;

import br.com.imarui.ima.authentication.core.application.exceptions.session.SessionNotFoundException;
import br.com.imarui.ima.authentication.core.application.result.admin.session.AdminSessionResult;
import br.com.imarui.ima.authentication.core.domain.model.Session;
import br.com.imarui.ima.authentication.core.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminSessionService {

    private final SessionRepository sessionRepository;
    private final Clock clock;
    private final br.com.imarui.ima.authentication.core.repository.RefreshTokenRepository refreshTokens;

    @Transactional(readOnly=true)
    public List<AdminSessionResult> findPage(int page,int size){return AdminSessionResult.from(sessionRepository.findPage(page,size));}

    @Transactional(readOnly = true)
    public List<AdminSessionResult> findAll() {
        return AdminSessionResult.from(
                sessionRepository.findAll()
        );
    }

    @Transactional(readOnly = true)
    public AdminSessionResult findById(
            Long sessionId
    ) {
        Session session =
                findSessionById(sessionId);

        return AdminSessionResult.from(session);
    }

    @Transactional
    public AdminSessionResult logout(
            Long sessionId
    ) {
        Session session =
                findSessionByIdForUpdate(sessionId);

        Instant now = Instant.now(clock);

        session.logout(now);

        refreshTokens.revokeActiveBySessionId(sessionId);
        Session savedSession =
                sessionRepository.save(session);

        return AdminSessionResult.from(
                savedSession
        );
    }

    private Session findSessionById(
            Long sessionId
    ) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(
                        () -> new SessionNotFoundException(
                                sessionId
                        )
                );
    }

    private Session findSessionByIdForUpdate(
            Long sessionId
    ) {
        return sessionRepository
                .findByIdForUpdate(sessionId)
                .orElseThrow(
                        () -> new SessionNotFoundException(
                                sessionId
                        )
                );
    }

}
