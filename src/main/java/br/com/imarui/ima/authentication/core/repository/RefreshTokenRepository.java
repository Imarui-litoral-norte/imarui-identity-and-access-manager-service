package br.com.imarui.ima.authentication.core.repository;

import br.com.imarui.ima.authentication.core.domain.model.RefreshToken;

import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository {

    RefreshToken save(RefreshToken token);

    List<RefreshToken> findAll();
    List<RefreshToken> findPage(int page,int size);

    Optional<RefreshToken> findById(Long id);

    Optional<RefreshToken> findByIdForUpdate(Long id);

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    Optional<Long> findSessionIdByTokenHash(String tokenHash);

    Optional<RefreshToken> findByTokenHashForUpdate(
            String tokenHash
    );

    void revokeActiveBySessionId(Long sessionId);
}
