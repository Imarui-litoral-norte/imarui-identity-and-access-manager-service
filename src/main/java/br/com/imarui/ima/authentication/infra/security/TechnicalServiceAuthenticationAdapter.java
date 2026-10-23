package br.com.imarui.ima.authentication.infra.security;

import br.com.imarui.ima.authentication.api.module.TechnicalServiceAuthentication;
import br.com.imarui.ima.authentication.core.exception.TechnicalAuthenticationException;
import br.com.imarui.ima.platform.security.jwt.JwtProperties;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

/** Request-bound RSA proof, shared by PDP and catalog. Private keys never reach IIAM. */
@Component
@RequiredArgsConstructor
public class TechnicalServiceAuthenticationAdapter implements TechnicalServiceAuthentication {
  private final JdbcTemplate jdbc;
  private final Clock clock;
  private final JwtProperties properties;
  private final io.micrometer.core.instrument.MeterRegistry metrics;

  private record ProofKey(String publicKey,String service,UUID clientId,Set<Operation> operations,Set<UUID> roots) {}

  @Transactional(propagation=Propagation.REQUIRES_NEW,timeout=5)
  public Caller verify(String assertion,String method,String path,byte[] body) {
    String outcome="ERROR";
    try {
      if (assertion==null || assertion.length()>12000 || body.length>65536) throw new TechnicalAuthenticationException();
      var jwt=SignedJWT.parse(assertion); var h=jwt.getHeader(); var c=jwt.getJWTClaimsSet(); var now=clock.instant();
      if (!JWSAlgorithm.RS256.equals(h.getAlgorithm()) || !new JOSEObjectType("iiam-service+jwt").equals(h.getType())
          || h.getJWK()!=null || h.getJWKURL()!=null || h.getCriticalParams()!=null || h.getKeyID()==null) throw new TechnicalAuthenticationException();
      var credential=UUID.fromString(h.getKeyID());
      var rows=jdbc.query("""
          select k.public_key,k.service_code,s.client_id,s.operations,
            array(select j.scope_id from authorization_service_jurisdictions j where j.service_code=s.service_code) as roots
          from authentication_service_keys k
          join authentication_service_clients s on s.service_code=k.service_code
          where k.id=? and k.active and k.expires_at>? and s.enabled
          """,(r,n) -> new ProofKey(r.getString(1),r.getString(2),r.getObject(3,UUID.class),decode(r.getString(4)),
              Arrays.stream((Object[])r.getArray(5).getArray()).map(UUID.class::cast).collect(java.util.stream.Collectors.toUnmodifiableSet())),
          credential,Timestamp.from(now));
      if(rows.size()!=1) throw new TechnicalAuthenticationException();
      var proofKey=rows.get(0); var key=publicKey(proofKey.publicKey()); String service=proofKey.service();
      if(!jwt.verify(new RSASSAVerifier(key)) || !service.equals(c.getSubject()) || !service.equals(c.getIssuer())
          || !List.of(properties.getAudience()+":services").equals(c.getAudience())
          || c.getIssueTime()==null || c.getExpirationTime()==null
          || !c.getExpirationTime().after(c.getIssueTime())
          || c.getIssueTime().toInstant().isAfter(now.plusSeconds(5)) || !c.getExpirationTime().toInstant().isAfter(now)
          || Duration.between(c.getIssueTime().toInstant(),c.getExpirationTime().toInstant()).getSeconds()>60
          || c.getIssueTime().toInstant().isBefore(now.minusSeconds(60))
          || c.getNotBeforeTime()!=null && c.getNotBeforeTime().toInstant().isAfter(now)
          || !method.equals(c.getStringClaim("htm")) || !path.equals(c.getStringClaim("htu"))
          || !digest(body).equals(c.getStringClaim("body_sha256"))) throw new TechnicalAuthenticationException();
      if(c.getJWTID()==null || c.getStringClaim("correlation_id")==null) throw new TechnicalAuthenticationException();
      var nonce=UUID.fromString(c.getJWTID()); var correlation=UUID.fromString(c.getStringClaim("correlation_id"));
      // Bounded maintenance must not serialize fresh proofs behind an unrelated
      // expired nonce held by another transaction. Expired proofs are rejected above.
      jdbc.update("""
          with expired as materialized (
            select credential_id,nonce from authentication_service_nonces where expires_at<=?
            order by expires_at limit 256 for update skip locked
          ) delete from authentication_service_nonces n using expired
          where n.credential_id=expired.credential_id and n.nonce=expired.nonce
          """,Timestamp.from(now));
      if(jdbc.update("insert into authentication_service_nonces(credential_id,nonce,expires_at) values(?,?,?) on conflict do nothing",
          credential,nonce,Timestamp.from(c.getExpirationTime().toInstant()))!=1)
        throw new TechnicalAuthenticationException(TechnicalAuthenticationException.Reason.REPLAY);
      outcome="VERIFIED";
      return new Caller(proofKey.clientId(),service,credential,proofKey.operations(),proofKey.roots(),correlation);
    } catch (TechnicalAuthenticationException e) { outcome=e.reason().name(); throw e; }
      catch (java.text.ParseException | GeneralSecurityException | JOSEException | IllegalArgumentException e) {
        outcome="INVALID_PROOF";
        throw new TechnicalAuthenticationException();
      } finally { metrics.counter("iiam.m2m.proofs","outcome",outcome).increment(); }
  }

  @Transactional(propagation=Propagation.MANDATORY)
  public Client enroll(String service,Set<Operation> operations,Set<UUID> scopeRoots,String publicKey,Instant expiresAt) {
    validate(service,operations,scopeRoots);
    var now=clock.instant();
    jdbc.update("insert into authentication_service_clients(service_code,client_id,enabled,operations,created_at,updated_at) values(?,?,true,?,?,?)",
        service,UUID.randomUUID(),encode(operations),Timestamp.from(now),Timestamp.from(now));
    for(var root:scopeRoots) jdbc.update("insert into authorization_service_jurisdictions values(?,?)",service,root);
    rotate(service,publicKey,expiresAt);
    return client(service);
  }
  @Transactional(propagation=Propagation.MANDATORY)
  public Caller current(Caller authenticated) {
    var now=clock.instant();
    var caller=jdbc.query("""
        select s.client_id,s.service_code,s.operations,
          array(select j.scope_id from authorization_service_jurisdictions j where j.service_code=s.service_code) as roots
        from authentication_service_keys k join authentication_service_clients s on s.service_code=k.service_code
        where k.id=? and k.active and k.expires_at>? and s.enabled
        """,(r,n) -> new Caller(r.getObject(1,UUID.class),r.getString(2),authenticated.credentialId(),decode(r.getString(3)),
            Arrays.stream((Object[])r.getArray(4).getArray()).map(UUID.class::cast).collect(java.util.stream.Collectors.toUnmodifiableSet()),
            authenticated.correlationId()),authenticated.credentialId(),Timestamp.from(now)).stream().findFirst()
        .orElseThrow(TechnicalAuthenticationException::new);
    if(!caller.clientId().equals(authenticated.clientId()) || !caller.serviceCode().equals(authenticated.serviceCode()))
      throw new TechnicalAuthenticationException();
    return caller;
  }

  @Transactional(propagation=Propagation.MANDATORY)
  public Key rotate(String service,String publicKey,Instant expiresAt) {
    client(service);
    try {
      var key=publicKey(publicKey); var now=clock.instant();
      if(expiresAt==null || !expiresAt.isAfter(now) || expiresAt.isAfter(now.plus(Duration.ofDays(90))))
        throw new IllegalArgumentException("Technical keys must expire within 90 days.");
      var fingerprint=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key.getEncoded()));
      var id=UUID.randomUUID();
      jdbc.update("insert into authentication_service_keys(id,service_code,public_key,fingerprint,active,created_at,expires_at) values(?,?,?,?,true,?,?)",
          id,service,Base64.getEncoder().encodeToString(key.getEncoded()),fingerprint,Timestamp.from(now),Timestamp.from(expiresAt));
      return new Key(id,fingerprint,true,expiresAt);
    } catch(GeneralSecurityException e) { throw new IllegalArgumentException("Invalid RSA public key."); }
  }

  @Transactional(propagation=Propagation.MANDATORY)
  public void revokeKey(String service,UUID key) {
    if(jdbc.update("update authentication_service_keys set active=false,revoked_at=? where id=? and service_code=?",
        Timestamp.from(clock.instant()),key,service)!=1) throw new NoSuchElementException("Technical key not found.");
  }

  @Transactional(propagation=Propagation.MANDATORY)
  public Client configure(String service,boolean enabled,Set<Operation> operations,Set<UUID> scopeRoots,long expectedVersion) {
    validate(service,operations,scopeRoots);
    if(jdbc.update("update authentication_service_clients set enabled=?,operations=?,updated_at=?,version=version+1 where service_code=? and version=?",
        enabled,encode(operations),Timestamp.from(clock.instant()),service,expectedVersion)!=1)
      throw new org.springframework.dao.OptimisticLockingFailureException("Technical client changed.");
    jdbc.update("delete from authorization_service_jurisdictions where service_code=?",service);
    for(var root:scopeRoots) jdbc.update("insert into authorization_service_jurisdictions values(?,?)",service,root);
    return client(service);
  }

  @Transactional(readOnly=true)
  public Client client(String service) {
    var roots=Set.copyOf(jdbc.queryForList("select scope_id from authorization_service_jurisdictions where service_code=?",UUID.class,service));
    var keys=jdbc.query("select id,fingerprint,active,expires_at from authentication_service_keys where service_code=? order by created_at",
        (r,n) -> new Key(r.getObject(1,UUID.class),r.getString(2),r.getBoolean(3),r.getTimestamp(4).toInstant()),service);
    return jdbc.query("select * from authentication_service_clients where service_code=?",(r,n) ->
        new Client(r.getObject("client_id",UUID.class),service,r.getBoolean("enabled"),decode(r.getString("operations")),roots,r.getLong("version"),keys),
        service).stream().findFirst().orElseThrow(() -> new NoSuchElementException("Technical client not found."));
  }

  private void validate(String service,Set<Operation> operations,Set<UUID> scopes) {
    if(service==null || !service.matches("[a-z][a-z0-9-]{1,49}") || service.equals("iiam")
        || operations==null || operations.isEmpty() || operations.stream().anyMatch(Objects::isNull)
        || scopes==null || scopes.isEmpty() || scopes.size()>100 || scopes.stream().anyMatch(Objects::isNull))
      throw new IllegalArgumentException("Invalid technical client policy.");
  }
  @Transactional(readOnly=true)
  public ClientPage clients(int offset,int size) {
    return new ClientPage(jdbc.query("select client_id,service_code,enabled,operations,version from authentication_service_clients order by service_code limit ? offset ?",
        (r,n) -> new ClientSummary(r.getObject(1,UUID.class),r.getString(2),r.getBoolean(3),decode(r.getString(4)),r.getLong(5)),size,offset),
        jdbc.queryForObject("select count(*) from authentication_service_clients",Long.class));
  }
  private String encode(Set<Operation> operations) { return operations.stream().map(Enum::name).sorted().collect(java.util.stream.Collectors.joining(",")); }
  private Set<Operation> decode(String operations) { return Set.copyOf(Arrays.stream(operations.split(",")).map(Operation::valueOf).toList()); }
  private RSAPublicKey publicKey(String encoded) throws GeneralSecurityException {
    if(encoded==null || encoded.length()>4096) throw new IllegalArgumentException("Invalid public key.");
    var key=(RSAPublicKey)KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(encoded)));
    if(key.getModulus().bitLength()<2048 || key.getModulus().bitLength()>4096) throw new IllegalArgumentException("RSA key size must be 2048–4096 bits.");
    return key;
  }
  public static String digest(byte[] body) {
    try { return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(body)); }
    catch(NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
  }
}
