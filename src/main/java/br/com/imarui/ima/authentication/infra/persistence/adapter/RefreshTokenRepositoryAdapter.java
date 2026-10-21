package br.com.imarui.ima.authentication.infra.persistence.adapter;

import br.com.imarui.ima.authentication.core.domain.model.RefreshToken;
import br.com.imarui.ima.authentication.core.domain.model.Session;
import br.com.imarui.ima.authentication.core.repository.RefreshTokenRepository;
import br.com.imarui.ima.authentication.infra.persistence.entity.RefreshTokenEntity;
import br.com.imarui.ima.authentication.infra.persistence.jpa.RefreshTokenJpaRepository;
import br.com.imarui.ima.authentication.infra.persistence.jpa.SessionJpaRepository;
import br.com.imarui.ima.authentication.infra.persistence.mapper.RefreshTokenMapper;
import br.com.imarui.ima.authentication.infra.persistence.mapper.SessionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class RefreshTokenRepositoryAdapter
        implements RefreshTokenRepository {

    private final RefreshTokenJpaRepository jpaRepository;
    private final SessionJpaRepository sessionJpaRepository;

    @Override
    public RefreshToken save(RefreshToken token) {
        validateTokenSession(token);

        RefreshTokenEntity entity =
                RefreshTokenMapper.toEntity(token);

        RefreshTokenEntity savedEntity =
                jpaRepository.save(entity);

        return RefreshTokenMapper.toDomain(
                savedEntity,
                token.getSession()
        );
    }

    @Override
    public Optional<RefreshToken> findByTokenHashForUpdate(
            String tokenHash
    ) {
        return jpaRepository.findByTokenHashForUpdate(tokenHash)
                .flatMap(this::mapToDomain);
    }

    @Override
    public List<RefreshToken> findAll() {
        List<RefreshTokenEntity> tokenEntities =
                jpaRepository.findAll();

        return mapToDomains(tokenEntities);
    }

    @Override
    public Optional<RefreshToken> findById(Long id) {
        return jpaRepository.findById(id)
                .flatMap(this::mapToDomain);
    }

    @Override
    public Optional<RefreshToken> findByIdForUpdate(Long id) {
        return jpaRepository.findByIdForUpdate(id)
                .flatMap(this::mapToDomain);
    }

    @Override
    public Optional<RefreshToken> findByTokenHash(
            String tokenHash
    ) {
        return jpaRepository.findByTokenHash(tokenHash)
                .flatMap(this::mapToDomain);
    }

    @Override
    public Optional<Long> findSessionIdByTokenHash(
            String tokenHash
    ) {
        return jpaRepository.findSessionIdByTokenHash(
                tokenHash
        );
    }

    @Override
    public void revokeActiveBySessionId(Long sessionId) {
        jpaRepository.revokeActiveBySessionId(sessionId);
    }

    private Optional<RefreshToken> mapToDomain(
            RefreshTokenEntity entity
    ) {
        return sessionJpaRepository
                .findById(entity.getSessionId())
                .map(SessionMapper::toDomain)
                .map(session ->
                        RefreshTokenMapper.toDomain(
                                entity,
                                session
                        )
                );
    }

    private List<RefreshToken> mapToDomains(
            Collection<RefreshTokenEntity> tokenEntities
    ) {
        if (tokenEntities.isEmpty()) {
            return List.of();
        }

        List<Long> sessionIds = tokenEntities.stream()
                .map(RefreshTokenEntity::getSessionId)
                .distinct()
                .toList();

        Map<Long, Session> sessionsById =
                sessionJpaRepository.findAllById(sessionIds)
                        .stream()
                        .map(SessionMapper::toDomain)
                        .collect(Collectors.toMap(
                                Session::getId,
                                Function.identity()
                        ));

        return tokenEntities.stream()
                .map(entity -> mapToDomain(
                        entity,
                        sessionsById
                ))
                .toList();
    }

    private RefreshToken mapToDomain(
            RefreshTokenEntity entity,
            Map<Long, Session> sessionsById
    ) {
        Session session =
                sessionsById.get(entity.getSessionId());

        if (session == null) {
            throw new IllegalStateException(
                    "Session not found for refresh token id: "
                            + entity.getId()
            );
        }

        return RefreshTokenMapper.toDomain(
                entity,
                session
        );
    }

    private void validateTokenSession(
            RefreshToken token
    ) {
        if (token == null) {
            throw new IllegalArgumentException(
                    "Refresh token is required."
            );
        }

        if (token.getSession() == null) {
            throw new IllegalArgumentException(
                    "Refresh token session is required."
            );
        }

        if (token.getSession().getId() == null) {
            throw new IllegalArgumentException(
                    "Refresh token session id is required."
            );
        }
    }

    public List<RefreshToken> findPage(int page,int size) {
        var values=jpaRepository.findAll(org.springframework.data.domain.PageRequest.of(page,size,org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC,"id"))).getContent();
        return mapToDomains(values);
    }
}
