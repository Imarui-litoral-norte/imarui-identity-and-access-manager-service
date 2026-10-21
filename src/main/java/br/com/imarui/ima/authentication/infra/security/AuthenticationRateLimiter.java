package br.com.imarui.ima.authentication.infra.security;

import br.com.imarui.ima.authentication.core.port.RefreshTokenHashService;
import java.sql.Timestamp;
import java.time.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

@Component
@RequiredArgsConstructor
public class AuthenticationRateLimiter {
  private final org.springframework.core.env.Environment environment;
  private final JdbcTemplate jdbc;
  private final Clock clock;
  private final RefreshTokenHashService hash;

  public static class Limited extends RuntimeException {}

  @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = Limited.class)
  public void check(String operation, String normalizedKey, int limit) {
    limit = environment.getProperty("security.auth.limits." + operation, Integer.class, limit);
    if (limit < 1) throw new IllegalStateException("Rate limit must be positive.");
    var now = clock.instant();
    String bucket = hash.hash(operation + ":" + normalizedKey);
    int count =
        jdbc.queryForObject(
            "insert into authentication_rate_limits(bucket,window_start,attempts) values(?,?,1) on"
                + " conflict(bucket) do update set attempts=case when"
                + " authentication_rate_limits.window_start<=? then 1 else"
                + " authentication_rate_limits.attempts+1 end,window_start=case when"
                + " authentication_rate_limits.window_start<=? then excluded.window_start else"
                + " authentication_rate_limits.window_start end returning attempts",
            Integer.class,
            bucket,
            Timestamp.from(now),
            Timestamp.from(now.minusSeconds(300)),
            Timestamp.from(now.minusSeconds(300)));
    if (count > limit) throw new Limited();
  }
}
