package br.com.imarui.ima.authorization.infra.persistence;

import br.com.imarui.ima.authorization.core.domain.model.PermissionManifest;
import br.com.imarui.ima.authorization.core.repository.PermissionCatalogRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.json.JsonMapper;

@Repository
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class JdbcPermissionCatalogRepository implements PermissionCatalogRepository {
  private final JdbcTemplate jdbc;
  private final JsonMapper json;

  public void lock() { jdbc.query("select pg_advisory_xact_lock(810082)", r -> {}); }

  public Optional<Owner> owner(String code) {
    return jdbc.query("select * from authorization_catalog_owners where service_code=? for update",
        (r,n) -> new Owner(r.getString("service_code"),r.getString("namespace"),
            r.getLong("version"),r.getString("digest")), code).stream().findFirst();
  }

  public void enroll(String code,String namespace,Instant now) {
    jdbc.update("insert into authorization_catalog_owners(service_code,namespace,updated_at) values(?,?,?)",code,namespace,Timestamp.from(now));
  }

  public void synchronize(PermissionManifest manifest, Instant now) {
    // A failed collision must roll back the whole manifest, including prior entries and deprecation.
    for (var entry : manifest.permissions()) {
      int changed = jdbc.update("""
          insert into authorization_permissions(code,description,module,service_code,namespace,catalog_version,deprecated,updated_at)
          values(?,?,?,?,?,?,?,?) on conflict(code) do update set description=excluded.description,
          module=excluded.module,catalog_version=excluded.catalog_version,deprecated=excluded.deprecated,updated_at=excluded.updated_at
          where authorization_permissions.service_code=excluded.service_code
          and authorization_permissions.namespace=excluded.namespace
          """, entry.code(),entry.description(),entry.module(),manifest.owner(),manifest.namespace(),
          manifest.version(),entry.deprecated(),Timestamp.from(now));
      if (changed != 1) throw new SecurityException("Permission code is owned by another service.");
    }
    // Entries omitted from a newer manifest keep their IDs, associations and previous revision.
    jdbc.update("update authorization_permissions set deprecated=true,updated_at=? where service_code=? and catalog_version<>? and not deprecated",
        Timestamp.from(now), manifest.owner(),manifest.version());
    jdbc.update("insert into authorization_catalog_revisions(service_code,version,digest,recorded_at,manifest) values(?,?,?,?,?::jsonb)",
        manifest.owner(),manifest.version(),manifest.digest(),Timestamp.from(now),json.writeValueAsString(manifest));
    jdbc.update("update authorization_catalog_owners set version=?,digest=?,updated_at=? where service_code=?",
        manifest.version(),manifest.digest(),Timestamp.from(now),manifest.owner());
    jdbc.update("""
        insert into authorization_audit(id,service_code,action,resource_type,resource_id,occurred_at,correlation_id,after_state)
        values(?,?,'CATALOG_SYNCHRONIZED','CATALOG',?,?,?,?::jsonb)
        """, UUID.randomUUID(),manifest.owner(),manifest.owner(),Timestamp.from(now),UUID.randomUUID(),
        json.writeValueAsString(Map.of("version",manifest.version(),"digest",manifest.digest(),"entries",manifest.permissions().size())));
  }
}
