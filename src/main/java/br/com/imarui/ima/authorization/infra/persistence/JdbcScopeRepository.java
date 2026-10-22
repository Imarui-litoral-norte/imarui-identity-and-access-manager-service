package br.com.imarui.ima.authorization.infra.persistence;

import br.com.imarui.ima.authorization.core.domain.model.AuthorizationScope;
import br.com.imarui.ima.authorization.core.repository.ScopeRepository;
import java.sql.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;

@Repository
@RequiredArgsConstructor
public class JdbcScopeRepository implements ScopeRepository {
  private final JdbcTemplate jdbc;
  private static final RowMapper<AuthorizationScope> MAPPER = (r, n) -> new AuthorizationScope(
      r.getObject("id", UUID.class), r.getString("code"), r.getString("name"),
      AuthorizationScope.Status.valueOf(r.getString("status")), r.getObject("parent_id", UUID.class),
      r.getTimestamp("created_at").toInstant(), r.getTimestamp("updated_at").toInstant(), r.getLong("version"));

  public List<AuthorizationScope> all() {
    return jdbc.query("select * from authorization_scopes order by code", MAPPER);
  }

  public Optional<AuthorizationScope> find(UUID id) {
    return jdbc.query("select * from authorization_scopes where id=?", MAPPER, id).stream().findFirst();
  }

  public List<AuthorizationScope> ancestors(UUID id) {
    return jdbc.query("""
        with recursive tree as (
          select s.*,array[s.id] as visited,0 as depth from authorization_scopes s where id=?
          union all select s.*,t.visited||s.id,t.depth+1 from authorization_scopes s
          join tree t on s.id=t.parent_id where not s.id=any(t.visited)
        ) select * from tree order by depth
        """, MAPPER, id);
  }

  public List<AuthorizationScope> descendants(UUID id) {
    return jdbc.query("""
        with recursive tree as (
          select s.*,array[s.id] as visited from authorization_scopes s where id=?
          union all select s.*,t.visited||s.id from authorization_scopes s
          join tree t on s.parent_id=t.id where not s.id=any(t.visited)
        ) select * from tree order by code
        """, MAPPER, id);
  }

  public Optional<UUID> tenantScope(UUID tenantId) {
    return jdbc.queryForList("select scope_id from authorization_tenant_scopes where tenant_id=?",
        UUID.class, tenantId).stream().findFirst();
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void save(AuthorizationScope scope) {
    if (scope.version() == 0) {
      jdbc.update("insert into authorization_scopes(id,code,name,status,parent_id,created_at,updated_at,version) values(?,?,?,?,?,?,?,0)",
          scope.id(), scope.code(), scope.name(), scope.status().name(), scope.parentId(),
          Timestamp.from(scope.createdAt()), Timestamp.from(scope.updatedAt()));
    } else if (jdbc.update("update authorization_scopes set name=?,status=?,parent_id=?,updated_at=?,version=? where id=? and version=?",
        scope.name(), scope.status().name(), scope.parentId(), Timestamp.from(scope.updatedAt()),
        scope.version(), scope.id(), scope.version()-1) != 1) {
      throw new org.springframework.dao.OptimisticLockingFailureException("Scope changed concurrently.");
    }
  }
}
