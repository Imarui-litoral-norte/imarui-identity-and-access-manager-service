package br.com.imarui.ima.platform.bootstrap;

import br.com.imarui.ima.authentication.core.application.usecase.AuthenticationUseCases;
import br.com.imarui.ima.authentication.core.domain.model.AuthenticationChallenge.Purpose;
import br.com.imarui.ima.identity.api.module.InitialIdentityProvisioning;
import br.com.imarui.ima.identity.core.domain.enums.identity.IdentityKind;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.time.Clock;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@Order(10)
@ConditionalOnProperty(name = "security.bootstrap.enabled", havingValue = "true")
public class AuthenticationBootstrap implements ApplicationRunner {
  private final InitialIdentityProvisioning provisioning;
  private final AuthenticationUseCases auth;
  private final JdbcTemplate jdbc;
  private final tools.jackson.databind.json.JsonMapper json;
  private final TransactionTemplate tx;
  private final Clock clock;
  private final br.com.imarui.ima.authorization.api.module.InitialRootAuthorization rootAuthorization;
  private final String input, output;

  public AuthenticationBootstrap(
      InitialIdentityProvisioning provisioning,
      AuthenticationUseCases auth,
      JdbcTemplate jdbc,
      tools.jackson.databind.json.JsonMapper json,
      PlatformTransactionManager manager,
      Clock clock,
      br.com.imarui.ima.authorization.api.module.InitialRootAuthorization rootAuthorization,
      @Value("${security.bootstrap.input-file}") String input,
      @Value("${security.bootstrap.output-file}") String output) {
    this.provisioning = provisioning;
    this.auth = auth;
    this.jdbc = jdbc;
    this.json = json;
    this.tx = new TransactionTemplate(manager);
    this.clock = clock;
    this.rootAuthorization = rootAuthorization;
    this.input = input;
    this.output = output;
  }

  @SuppressWarnings("unchecked")
  public void run(ApplicationArguments args) throws Exception {
    Map<String, String> config = json.readValue(Files.readString(Path.of(input)), Map.class);
    Path target = Path.of(output).toAbsolutePath();
    Files.createFile(target);
    var acl = Files.getFileAttributeView(target, AclFileAttributeView.class);
    if (acl != null) {
      var owner = Files.getOwner(target);
      acl.setAcl(
          List.of(
              AclEntry.newBuilder()
                  .setType(AclEntryType.ALLOW)
                  .setPrincipal(owner)
                  .setPermissions(EnumSet.allOf(AclEntryPermission.class))
                  .build()));
    } else Files.setPosixFilePermissions(target, PosixFilePermissions.fromString("rw-------"));
    try {
      tx.executeWithoutResult(
          status -> {
            jdbc.query("select pg_advisory_xact_lock(390076)", r -> {});
            if (Boolean.TRUE.equals(
                jdbc.queryForObject(
                    "select exists(select 1 from authentication_bootstrap where"
                        + " name='initial-root')",
                    Boolean.class)))
              throw new IllegalStateException("Bootstrap already completed.");
            var id =
                provisioning.create(
                    IdentityKind.valueOf(config.get("kind")),
                    config.get("nickname"),
                    config.get("email"),
                    config);
            jdbc.update(
                "insert into authentication_bootstrap values('initial-root',?,?)",
                id,
                java.sql.Timestamp.from(clock.instant()));
            rootAuthorization.assign(id);
            String invite = auth.issueChallenge(id, Purpose.ACCOUNT_ACTIVATION);
            try {
              Files.writeString(
                  target,
                  json.writeValueAsString(
                      Map.of("identityId", id.toString(), "invitation", invite)));
            } catch (java.io.IOException e) {
              throw new IllegalStateException("Cannot write protected bootstrap output.");
            }
          });
    } catch (RuntimeException e) {
      Files.deleteIfExists(target);
      throw e;
    }
  }
}
