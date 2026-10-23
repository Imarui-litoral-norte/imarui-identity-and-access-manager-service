package br.com.imarui.ima.authorization.infra.persistence;
import br.com.imarui.ima.authorization.core.repository.CatalogInspectionRepository;
import br.com.imarui.ima.authorization.core.domain.model.PermissionManifest;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.json.JsonMapper;

@Repository
@RequiredArgsConstructor
@Transactional(propagation=Propagation.MANDATORY)
public class JdbcCatalogInspectionRepository implements CatalogInspectionRepository {
  private final JdbcTemplate jdbc;
  private final JsonMapper json;
  private static final org.springframework.jdbc.core.RowMapper<Owner> OWNER=(r,n) ->
      new Owner(r.getString("service_code"),r.getString("namespace"),r.getLong("version"),r.getString("digest"),r.getTimestamp("updated_at").toInstant());
  public List<Owner> owners(int offset,int size) { return jdbc.query("select * from authorization_catalog_owners order by service_code limit ? offset ?",OWNER,size,offset); }
  public long ownerCount() { return jdbc.queryForObject("select count(*) from authorization_catalog_owners",Long.class); }
  public Optional<Owner> owner(String code) { return jdbc.query("select * from authorization_catalog_owners where service_code=?",OWNER,code).stream().findFirst(); }
  public List<Revision> revisions(String service,int offset,int size) {
    return jdbc.query("select version,digest,recorded_at,jsonb_array_length(manifest->'permissions') from authorization_catalog_revisions where service_code=? order by version desc limit ? offset ?",
        (r,n) -> new Revision(r.getLong(1),r.getString(2),r.getTimestamp(3).toInstant(),r.getInt(4)),service,size,offset);
  }
  public long revisionCount(String service) { return jdbc.queryForObject("select count(*) from authorization_catalog_revisions where service_code=?",Long.class,service); }
  public Optional<PermissionManifest> manifest(String service,long version) {
    return jdbc.query("select manifest::text from authorization_catalog_revisions where service_code=? and version=?",
        (r,n) -> json.readValue(r.getString(1),PermissionManifest.class),service,version).stream().findFirst();
  }
}
