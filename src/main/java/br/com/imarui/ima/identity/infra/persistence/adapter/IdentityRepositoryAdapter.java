package br.com.imarui.ima.identity.infra.persistence.adapter;

import br.com.imarui.ima.identity.core.domain.enums.identity.IdentityStatus;
import br.com.imarui.ima.identity.core.domain.model.identity.*;
import br.com.imarui.ima.identity.core.domain.model.identity.LegalEntity.Cnpj;
import br.com.imarui.ima.identity.core.domain.model.identity.person.Cpf;
import br.com.imarui.ima.identity.core.repository.IdentityRepository;
import br.com.imarui.ima.identity.infra.persistence.entity.identity.IdentityEntity;
import br.com.imarui.ima.identity.infra.persistence.jpa.IdentityJpaRepository;
import br.com.imarui.ima.identity.infra.persistence.mapper.IdentityPersistenceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class IdentityRepositoryAdapter implements IdentityRepository {

    private final IdentityJpaRepository jpaRepository;
    private final IdentityPersistenceMapper mapper;

    @Override
    public Identity save(Identity identity) {
        IdentityEntity entity = jpaRepository
                .findById(identity.getId().value())
                .map(existing -> {
                    mapper.updateEntity(identity, existing);
                    return existing;
                })
                .orElseGet(() -> mapper.toEntity(identity));

        return mapper.toDomain(
                jpaRepository.save(entity)
        );
    }

    @Override
    public List<Identity> findAllActive() {
        return jpaRepository
                .findAllByStatusOrderByCreatedAtDesc(
                        IdentityStatus.ACTIVE
                )
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public Optional<Identity> findById(IdentityId identityId) {
        return jpaRepository
                .findById(identityId.value())
                .map(mapper::toDomain);
    }

    @Override
    public Optional<Identity> findByIdForUpdate(
            IdentityId identityId
    ) {
        return jpaRepository
                .findByIdForUpdate(identityId.value())
                .map(mapper::toDomain);
    }

    @Override
    public Optional<Identity> findPersonByCpf(Cpf cpf) {
        return jpaRepository
                .findPersonByCpf(
                        cpf.value()
                )
                .map(mapper::toDomain);
    }

    @Override
    public Optional<Identity> findLegalEntityByCnpj(Cnpj cnpj) {
        return jpaRepository
                .findLegalEntityByCnpj(
                        cnpj.value()
                )
                .map(mapper::toDomain);
    }

    @Override
    public boolean existsByPrimaryEmailAndKind(Email email, br.com.imarui.ima.identity.core.domain.enums.identity.IdentityKind kind) {
        return jpaRepository.existsEmailForKind(email.value(), kind.name());
    }

    @Override
    public boolean existsByPrimaryEmail(Email email) {
        return jpaRepository.existsByPrimaryEmail(
                email.value()
        );
    }
}
