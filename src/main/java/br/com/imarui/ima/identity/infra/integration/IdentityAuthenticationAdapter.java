package br.com.imarui.ima.identity.infra.integration;

import br.com.imarui.ima.identity.api.module.IdentityAuthenticationApi;
import br.com.imarui.ima.identity.core.domain.enums.identity.*;
import br.com.imarui.ima.identity.core.domain.model.identity.*;
import br.com.imarui.ima.identity.core.repository.IdentityRepository;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

@Component
@RequiredArgsConstructor
public class IdentityAuthenticationAdapter implements IdentityAuthenticationApi {
  private final JdbcTemplate jdbc;
  private final IdentityRepository identities;
  private final Clock clock;

  public Optional<Account> resolve(IdentityKind kind, String identifier) {
    if (kind == null || identifier == null || identifier.length() > 128 || identifier.contains("@"))
      return Optional.empty();
    String normalized = identifier.trim().toLowerCase(Locale.ROOT);
    List<UUID> ids;
    if (normalized.matches("[0-9. /-]+") && kind != IdentityKind.SERVICE) {
      String digits = normalized.replaceAll("[^0-9]", "");
      if (kind == IdentityKind.PERSON && digits.length() == 11)
        ids =
            jdbc.queryForList(
                "select id from ima_person_identities where cpf=?", UUID.class, digits);
      else if (kind == IdentityKind.LEGAL_ENTITY && digits.length() == 14)
        ids =
            jdbc.queryForList(
                "select id from ima_legal_entity_identities where cnpj=?", UUID.class, digits);
      else return Optional.empty();
    } else {
      try {
        normalized = Nickname.from(normalized).value();
      } catch (IllegalArgumentException e) {
        return Optional.empty();
      }
      ids =
          jdbc.queryForList(
              "select id from ima_identities where kind=? and nickname=?",
              UUID.class,
              kind.name(),
              normalized);
    }
    return ids.size() == 1 ? find(ids.get(0)) : Optional.empty();
  }

  private Optional<Account> find(UUID id) {
    // Authentication discovery must not retain a managed Identity before waiting for its lock.
    return jdbc.query(
        "select id,kind,status,nickname,primary_email from ima_identities where id=?",
        (row, index) -> new Account(row.getObject("id", UUID.class),
            IdentityKind.valueOf(row.getString("kind")),
            IdentityStatus.valueOf(row.getString("status")),
            row.getString("nickname"), row.getString("primary_email")), id)
        .stream().findFirst();
  }

  private Account view(Identity i) {
    return new Account(
        i.getId().value(),
        i.getKind(),
        i.getStatus(),
        i.getNickname(),
        i.getPrimaryEmail() == null ? null : i.getPrimaryEmail().email().value());
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public Optional<Account> lock(UUID id) {
    return identities.findByIdForUpdate(IdentityId.from(id)).map(this::view);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void activate(UUID id) {
    var i = identities.findByIdForUpdate(IdentityId.from(id)).orElseThrow();
    i.activate(clock.instant());
    identities.save(i);
  }

  public boolean valid(UUID id, Instant issuedAt, String tenantId) {
    if (issuedAt == null || !find(id).map(Account::active).orElse(false)) return false;
    if (Boolean.TRUE.equals(
        jdbc.queryForObject(
            "select exists(select 1 from ima_access_revocation_requests where scope='IDENTITY' and"
                + " subject_id=? and requested_at>=?)",
            Boolean.class,
            id,
            java.sql.Timestamp.from(issuedAt)))) return false;
    if (tenantId == null) return true;
    try {
      UUID tenant = UUID.fromString(tenantId);
      return Boolean.TRUE.equals(
          jdbc.queryForObject(
              "select exists(select 1 from ima_tenants t join ima_affiliations a on"
                  + " a.tenant_id=t.id where t.id=? and t.status='ACTIVE' and a.identity_id=? and"
                  + " a.status='ACTIVE') and not exists(select 1 from"
                  + " ima_access_revocation_requests where scope='TENANT' and subject_id=? and"
                  + " requested_at>=?)",
              Boolean.class,
              tenant,
              id,
              tenant,
              java.sql.Timestamp.from(issuedAt)));
    } catch (IllegalArgumentException ex) {
      return false;
    }
  }
}
