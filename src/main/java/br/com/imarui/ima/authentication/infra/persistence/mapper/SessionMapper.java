package br.com.imarui.ima.authentication.infra.persistence.mapper;

import br.com.imarui.ima.authentication.core.domain.model.Session;
import br.com.imarui.ima.authentication.infra.persistence.entity.SessionEntity;

public class SessionMapper {

    private SessionMapper() {}

    public static Session toDomain(SessionEntity entity) {
        if (entity == null) return null;
        return Session.reconstitute(
                entity.getId(),
                entity.getCreatedAt(),
                entity.getExpiresAt(),
                entity.getLoggedOutAt(),
                entity.getStatus()
        ).authenticationContext(entity.getIdentityId(),entity.getPublicId(),entity.getRevokedAt());
    }

    public static SessionEntity toEntity(Session domain) {
        if (domain == null) return null;
        SessionEntity entity = new SessionEntity();
        entity.setId(domain.getId());
        entity.setIdentityId(domain.getIdentityId()); entity.setPublicId(domain.getPublicId()); entity.setRevokedAt(domain.getRevokedAt());
        entity.setCreatedAt(domain.getCreatedAt());
        entity.setExpiresAt(domain.getExpiresAt());
        entity.setLoggedOutAt(domain.getLoggedOutAt());
        entity.setStatus(domain.getStatus());
        return entity;
    }
}
