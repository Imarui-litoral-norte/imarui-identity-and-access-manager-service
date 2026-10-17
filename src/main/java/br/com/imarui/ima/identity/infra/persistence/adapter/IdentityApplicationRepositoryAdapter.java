package br.com.imarui.ima.identity.infra.persistence.adapter;

import br.com.imarui.ima.identity.core.domain.enums.identityapplication.IdentityApplicationStatus;
import br.com.imarui.ima.identity.core.domain.model.identity.person.Cpf;
import br.com.imarui.ima.identity.core.domain.model.identityapplication.IdentityApplication;
import br.com.imarui.ima.identity.core.domain.model.identityapplication.IdentityApplicationId;
import br.com.imarui.ima.identity.core.repository.IdentityApplicationRepository;
import br.com.imarui.ima.identity.infra.persistence.entity.identityapplication.IdentityApplicationEntity;
import br.com.imarui.ima.identity.infra.persistence.jpa.IdentityApplicationJpaRepository;
import br.com.imarui.ima.identity.infra.persistence.mapper.IdentityApplicationPersistenceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class IdentityApplicationRepositoryAdapter
        implements IdentityApplicationRepository {

    private final IdentityApplicationJpaRepository jpaRepository;
    private final IdentityApplicationPersistenceMapper mapper;

    @Override
    public IdentityApplication save(
            IdentityApplication identityApplication
    ) {
        IdentityApplicationEntity entity = jpaRepository
                .findById(identityApplication.getId().value())
                .map(existing -> {
                    mapper.updateEntity(identityApplication, existing);
                    return existing;
                })
                .orElseGet(() -> mapper.toEntity(identityApplication));

        return mapper.toDomain(jpaRepository.save(entity));
    }

    @Override
    public Optional<IdentityApplication> findById(
            IdentityApplicationId identityApplicationId
    ) {
        return jpaRepository
                .findById(identityApplicationId.value())
                .map(mapper::toDomain);
    }

    @Override
    public Optional<IdentityApplication> findByIdForUpdate(
            IdentityApplicationId identityApplicationId
    ) {
        return jpaRepository
                .findByIdForUpdate(identityApplicationId.value())
                .map(mapper::toDomain);
    }

    @Override
    public List<IdentityApplication> findAll() {
        return jpaRepository
                .findAllByOrderByRequestedAtDesc()
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<IdentityApplication> findAllPending() {
        return jpaRepository
                .findByStatusOrderByRequestedAtAsc(
                        IdentityApplicationStatus.PENDING
                )
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<IdentityApplication> findByStatus(
            IdentityApplicationStatus status
    ) {
        return jpaRepository
                .findByStatusOrderByRequestedAtAsc(status)
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public boolean existsPendingByCnpj(br.com.imarui.ima.identity.core.domain.model.identity.LegalEntity.Cnpj cnpj) {
        return jpaRepository.existsByCnpjAndStatus(cnpj.value(), IdentityApplicationStatus.PENDING);
    }
    @Override
    public boolean existsPendingByEmailAndKind(br.com.imarui.ima.identity.core.domain.model.identity.Email email, br.com.imarui.ima.identity.core.domain.enums.identity.IdentityKind kind) {
        return jpaRepository.existsByEmailIgnoreCaseAndKindAndStatus(email.value(), kind, IdentityApplicationStatus.PENDING);
    }

    @Override
    public boolean existsPendingByCpf(Cpf cpf) {
        return jpaRepository.existsByCpfAndStatus(
                cpf.value(),
                IdentityApplicationStatus.PENDING
        );
    }
}
