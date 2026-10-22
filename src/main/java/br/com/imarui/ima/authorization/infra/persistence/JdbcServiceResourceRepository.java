package br.com.imarui.ima.authorization.infra.persistence;

import br.com.imarui.ima.authorization.core.repository.ServiceResourceRepository;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;

@Repository
@RequiredArgsConstructor
@Transactional(propagation=Propagation.MANDATORY)
public class JdbcServiceResourceRepository implements ServiceResourceRepository {
  private final JdbcTemplate jdbc;
  public UUID resolve(String service,String type,String resource) {
    if("TENANT".equals(type)) return jdbc.query("select scope_id from authorization_tenant_scopes where tenant_id=?",
        (r,n) -> r.getObject(1,UUID.class),UUID.fromString(resource)).stream().findFirst()
        .orElseThrow(() -> new NoSuchElementException("Resource not found."));
    return jdbc.query("select scope_id from authorization_service_resources where service_code=? and resource_type=? and resource_id=?",
        (r,n) -> r.getObject(1,UUID.class),service,type,resource).stream().findFirst()
        .orElseThrow(() -> new NoSuchElementException("Resource not found."));
  }
  public String permissionOwner(String permission) {
    return jdbc.queryForList("select service_code from authorization_permissions where code=?",String.class,permission).stream()
        .findFirst().orElseThrow(() -> new NoSuchElementException("Permission not found."));
  }
  public void bind(String service,String type,String resource,UUID scope) {
    if(type==null || !type.matches("[A-Z][A-Z0-9_]{0,49}") || type.equals("TENANT")
        || resource==null || resource.isBlank() || resource.length()>150) throw new IllegalArgumentException("Invalid resource binding.");
    // Immutable binding prevents a technical client from relabeling an existing object.
    jdbc.update("insert into authorization_service_resources(service_code,resource_type,resource_id,scope_id,created_at) values(?,?,?,?,current_timestamp)",
        service,type,resource,scope);
  }
}
