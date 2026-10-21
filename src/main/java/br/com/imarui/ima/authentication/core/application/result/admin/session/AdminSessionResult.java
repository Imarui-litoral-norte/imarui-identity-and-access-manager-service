package br.com.imarui.ima.authentication.core.application.result.admin.session;

import br.com.imarui.ima.authentication.core.domain.model.Session;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record AdminSessionResult(
        Long id,
        java.util.UUID identityId,
        java.util.UUID sid,
        Instant revokedAt,
        String status,
        Instant createdAt,
        Instant expiresAt,
        Instant loggedOutAt
) {

    public static AdminSessionResult from(Session session) {
        Objects.requireNonNull(session, "session cannot be null");

        return new AdminSessionResult(
                session.getId(),
                session.getIdentityId(), session.getPublicId(), session.getRevokedAt(),
                session.getStatus().name(),
                session.getCreatedAt(),
                session.getExpiresAt(),
                session.getLoggedOutAt()
        );
    }

    public static List<AdminSessionResult> from(List<Session> sessions) {
        Objects.requireNonNull(sessions, "sessions cannot be null");

        return sessions.stream()
                .map(AdminSessionResult::from)
                .toList();
    }
}
