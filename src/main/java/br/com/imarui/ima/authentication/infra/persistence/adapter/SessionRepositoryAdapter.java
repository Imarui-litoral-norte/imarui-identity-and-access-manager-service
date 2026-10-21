package br.com.imarui.ima.authentication.infra.persistence.adapter;

import br.com.imarui.ima.authentication.core.domain.model.Session;
import br.com.imarui.ima.authentication.core.repository.SessionRepository;
import br.com.imarui.ima.authentication.infra.persistence.jpa.SessionJpaRepository;
import br.com.imarui.ima.authentication.infra.persistence.mapper.SessionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class SessionRepositoryAdapter
        implements SessionRepository {

    private final SessionJpaRepository jpaRepository;

    @Override
    public Session save(Session session) {
        return SessionMapper.toDomain(
                jpaRepository.save(
                        SessionMapper.toEntity(session)
                )
        );
    }

    @Override
    public Optional<Session> findById(Long sessionId) {
        return jpaRepository.findById(sessionId)
                .map(SessionMapper::toDomain);
    }

    @Override
    public Optional<Session> findByIdForUpdate(Long sessionId) {
        return jpaRepository.findByIdForUpdate(sessionId)
                .map(SessionMapper::toDomain);
    }

    @Override
    public List<Session> findAll() {
        return jpaRepository.findAll()
                .stream()
                .map(SessionMapper::toDomain)
                .toList();
    }


    public List<Session> findPage(int page,int size) {
        var values=jpaRepository.findAll(org.springframework.data.domain.PageRequest.of(page,size,org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC,"id"))).getContent();
        return values.stream().map(SessionMapper::toDomain).toList();
    }
}
