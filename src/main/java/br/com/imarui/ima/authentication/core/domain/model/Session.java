package br.com.imarui.ima.authentication.core.domain.model;

import br.com.imarui.ima.authentication.core.domain.enums.SessionStatus;
import br.com.imarui.ima.authentication.core.domain.exception.session.SessionInvalidActiveStateException;
import br.com.imarui.ima.authentication.core.domain.exception.session.SessionInvalidExpirationDateException;
import br.com.imarui.ima.authentication.core.domain.exception.session.SessionInvalidLogoutStateException;
import br.com.imarui.ima.authentication.core.domain.exception.session.SessionInvalidRevokeStateException;
import br.com.imarui.ima.authentication.core.domain.exception.session.SessionInvalidTtlValueException;
import br.com.imarui.ima.authentication.core.domain.exception.session.SessionMissingCreatedAtException;
import br.com.imarui.ima.authentication.core.domain.exception.session.SessionMissingExpiresAtException;
import br.com.imarui.ima.authentication.core.domain.exception.session.SessionMissingIdException;
import br.com.imarui.ima.authentication.core.domain.exception.session.SessionMissingStatusException;
import br.com.imarui.ima.authentication.core.domain.exception.session.SessionMissingTtlException;
import br.com.imarui.ima.authentication.core.domain.exception.session.SessionNotActiveException;
import br.com.imarui.ima.authentication.core.domain.exception.session.SessionNowInstantRequiredException;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Session {

    private Long id;
    private java.util.UUID identityId;
    private java.util.UUID publicId;
    private Instant revokedAt;
    public Session authenticationContext(java.util.UUID identityId,java.util.UUID publicId,Instant revokedAt){this.identityId=identityId;this.publicId=publicId;this.revokedAt=revokedAt;return this;}
    public static Session createForIdentity(java.util.UUID identityId, Instant now){return create(Duration.ofDays(7),now).authenticationContext(java.util.Objects.requireNonNull(identityId),java.util.UUID.randomUUID(),null);}
    public void revokeAt(Instant now){if(status==SessionStatus.ACTIVE){revoke();revokedAt=now;}}

    private Instant createdAt;
    private Instant expiresAt;
    private Instant loggedOutAt;
    private SessionStatus status;

    private Session(
            Duration ttl,
            Instant now
    ) {
        if (ttl == null) {
            throw new SessionMissingTtlException("ttl is required");
        }

        if (ttl.isZero() || ttl.isNegative()) {
            throw new SessionInvalidTtlValueException("ttl must be positive");
        }

        if (now == null) {
            throw new SessionNowInstantRequiredException("now is required");
        }

        this.createdAt = now;
        this.expiresAt = now.plus(ttl);
        this.status = SessionStatus.ACTIVE;
    }

    public static Session create(
            Duration ttl,
            Instant now
    ) {
        return new Session(
                ttl,
                now
        );
    }

    public static Session reconstitute(
            Long id,
            Instant createdAt,
            Instant expiresAt,
            Instant loggedOutAt,
            SessionStatus status
    ) {
        if (id == null) {
            throw new SessionMissingIdException("id is required");
        }

        if (createdAt == null) {
            throw new SessionMissingCreatedAtException("createdAt is required");
        }

        if (expiresAt == null) {
            throw new SessionMissingExpiresAtException("expiresAt is required");
        }

        if (status == null) {
            throw new SessionMissingStatusException("status is required");
        }

        if (!expiresAt.isAfter(createdAt)) {
            throw new SessionInvalidExpirationDateException("expiresAt must be after createdAt");
        }

        switch (status) {
            case ACTIVE -> {
                if (loggedOutAt != null) {
                    throw new SessionInvalidActiveStateException("Active session must not have loggedOutAt");
                }
            }

            case LOGGED_OUT -> {
                if (loggedOutAt == null) {
                    throw new SessionInvalidLogoutStateException("Logged out session must have loggedOutAt");
                }

                if (loggedOutAt.isBefore(createdAt)) {
                    throw new SessionInvalidLogoutStateException("loggedOutAt must be after createdAt");
                }
            }

            case REVOKED -> {
                if (loggedOutAt != null) {
                    throw new SessionInvalidRevokeStateException("Revoked session must not have loggedOutAt");
                }
            }
        }

        Session session = new Session();
        session.id = id;
        session.createdAt = createdAt;
        session.expiresAt = expiresAt;
        session.loggedOutAt = loggedOutAt;
        session.status = status;

        return session;
    }

    public boolean isNotExpired(Instant now) {
        if (now == null) {
            throw new SessionNowInstantRequiredException("now is required");
        }

        return expiresAt.isAfter(now);
    }

    public boolean isActive(Instant now) {
        return status == SessionStatus.ACTIVE && isNotExpired(now);
    }

    public boolean isRevoked() {
        return status == SessionStatus.REVOKED;
    }

    public boolean isLoggedOut() {
        return status == SessionStatus.LOGGED_OUT;
    }

    public boolean isValid(Instant now) {
        return isActive(now);
    }

    public void revoke() {
        if (status != SessionStatus.ACTIVE) {
            throw new SessionNotActiveException("Only active sessions can be revoked");
        }

        this.status = SessionStatus.REVOKED;
    }

    public void logout(Instant now) {
        if (now == null) {
            throw new SessionNowInstantRequiredException("now is required");
        }

        if (this.status != SessionStatus.ACTIVE) {
            return;
        }

        this.status = SessionStatus.LOGGED_OUT;
        this.loggedOutAt = now;
    }
}
