package br.com.imarui.ima.identity.infra.persistence.adapter;

import br.com.imarui.ima.identity.core.application.port.IdentityRegistrationLock;
import br.com.imarui.ima.identity.core.domain.model.identityapplication.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

@Component
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class PostgresIdentityRegistrationLock implements IdentityRegistrationLock {
  private final JdbcTemplate jdbc;

  public void acquire(IdentityApplication application) {
    var keys = new ArrayList<String>();
    if (application.getNickname() != null)
      keys.add("nickname:" + application.getKind() + ":" + application.getNickname());
    if (application.getDetails() instanceof PersonIdentityApplicationDetails person)
      keys.add("cpf:" + person.cpf().value());
    if (application.getDetails() instanceof LegalEntityIdentityApplicationDetails legal)
      keys.add("cnpj:" + legal.cnpj().value());
    keys.stream()
        .map(this::hash)
        .distinct()
        .sorted()
        .forEach(key -> jdbc.query("select pg_advisory_xact_lock(?)", rs -> {}, key));
  }

  private long hash(String key) {
    try {
      return ByteBuffer.wrap(
              MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8)))
          .getLong();
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException(exception);
    }
  }
}
