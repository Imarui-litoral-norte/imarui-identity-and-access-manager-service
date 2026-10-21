package br.com.imarui.ima.authentication.core.repository;

import br.com.imarui.ima.authentication.core.domain.model.Session;

import java.util.List;
import java.util.Optional;

public interface SessionRepository {

    Session save(Session session);

    List<Session> findAll();
    List<Session> findPage(int page,int size);

    Optional<Session> findById(Long id);

    Optional<Session> findByIdForUpdate(Long id);
}
