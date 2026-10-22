package br.com.imarui.ima.authorization.infra.persistence;

import br.com.imarui.ima.authorization.core.repository.SecurityAuditRepository;
import java.util.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.json.JsonMapper;

@Repository
@Transactional(propagation=Propagation.MANDATORY)
public class JdbcSecurityAuditRepository implements SecurityAuditRepository {
  private final NamedParameterJdbcTemplate jdbc;
  private final JsonMapper json;
  public JdbcSecurityAuditRepository(org.springframework.jdbc.core.JdbcTemplate jdbc,JsonMapper json) {
    this.jdbc=new NamedParameterJdbcTemplate(jdbc); this.json=json;
  }
  public Result query(Set<UUID> scopes,boolean includeGlobal,String event,UUID correlation,int offset,int size) {
    var parameters=new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
        .addValue("scopes",scopes).addValue("global",includeGlobal).addValue("offset",offset).addValue("size",size);
    String union="""
        select id,action as event,actor_identity_id,service_code,resource_type,resource_id,scope_id,occurred_at,
          reason,correlation_id,before_state::text as before_json,after_state::text as after_json
        from authorization_audit where (scope_id in (:scopes) or :global and scope_id is null)
        union all
        select id,event,actor_identity_id,service_code,'SECURITY_EVENT',resource_id,scope_id,occurred_at,
          reason_code,correlation_id,null,details::text
        from authorization_security_events where (scope_id in (:scopes) or :global and scope_id is null)
        """;
    String filter=" where true";
    if(event!=null) { filter+=" and event=:event"; parameters.addValue("event",event); }
    if(correlation!=null) { filter+=" and correlation_id=:correlation"; parameters.addValue("correlation",correlation); }
    String source=" from ("+union+") events"+filter;
    long total=jdbc.queryForObject("select count(*)"+source,parameters,Long.class);
    var list=jdbc.query("select *"+source+" order by occurred_at desc,id limit :size offset :offset",parameters,(r,n) ->
        new Entry(r.getObject("id",UUID.class),r.getString("event"),r.getObject("actor_identity_id",UUID.class),
            r.getString("service_code"),r.getString("resource_type"),r.getString("resource_id"),r.getObject("scope_id",UUID.class),
            r.getTimestamp("occurred_at").toInstant(),r.getString("reason"),r.getObject("correlation_id",UUID.class),
            r.getString("before_json")==null?null:json.readTree(r.getString("before_json")),
            r.getString("after_json")==null?null:json.readTree(r.getString("after_json"))));
    return new Result(list,total);
  }
  public Set<UUID> includeHistoricalScopes(Set<UUID> scopes) {
    return Set.copyOf(jdbc.query("""
        with recursive permitted(id) as (
          select id from authorization_scopes where id in (:scopes)
          union select h.original_scope_id from authorization_scopes__history h
          join permitted p on (h.scope_state->>'parentId')::uuid=p.id
        ) select id from permitted
        """,Map.of("scopes",scopes),(r,n) -> r.getObject(1,UUID.class)));
  }
}
