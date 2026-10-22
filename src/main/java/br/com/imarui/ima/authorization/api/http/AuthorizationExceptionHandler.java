package br.com.imarui.ima.authorization.api.http;

import br.com.imarui.ima.authorization.api.module.SecurityAuditEvents;
import br.com.imarui.ima.authentication.infra.security.AuthenticationRateLimiter;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(basePackages="br.com.imarui.ima.authorization.api.http")
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class AuthorizationExceptionHandler {
  private final SecurityAuditEvents events;
  private final java.time.Clock clock;
  @ExceptionHandler(SecurityException.class)
  public ProblemDetail denied(SecurityException e) {
    if(e instanceof br.com.imarui.ima.authorization.core.application.usecase.PdpUseCases.Rejected rejected)
      events.record("PDP_REJECTED",rejected.actor,rejected.service,null,null,"CALLER_RESOURCE_MISMATCH",rejected.correlation);
    else {
      var auth=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
      var caller=auth!=null && auth.getPrincipal() instanceof br.com.imarui.ima.authentication.api.module.TechnicalServiceAuthentication.Caller c?c:null;
      events.record("AUTHORIZATION_REJECTED",actor(),caller==null?null:caller.serviceCode(),null,null,"ACCESS_DENIED",caller==null?UUID.randomUUID():caller.correlationId());
    }
    return problem(403,"ACCESS_DENIED");
  }
  @ExceptionHandler({org.springframework.security.core.AuthenticationException.class,org.springframework.security.oauth2.jwt.JwtException.class,
      br.com.imarui.ima.authentication.core.application.exception.AuthenticationFailure.class})
  public ProblemDetail invalidActor(Exception e,jakarta.servlet.http.HttpServletResponse response) {
    response.setHeader("WWW-Authenticate","Bearer realm=\"actor\"");
    return problem(401,e instanceof br.com.imarui.ima.authentication.core.application.exception.AuthenticationFailure?"REAUTHENTICATION_FAILED":"INVALID_ACTOR_CONTEXT");
  }
  @ExceptionHandler(NoSuchElementException.class)
  public ProblemDetail missing(NoSuchElementException e) { return problem(404,"RESOURCE_NOT_FOUND"); }
  @ExceptionHandler(IllegalArgumentException.class)
  public ProblemDetail invalid(IllegalArgumentException e) { return problem(400,"INVALID_AUTHORIZATION_REQUEST"); }
  @ExceptionHandler({IllegalStateException.class,DataIntegrityViolationException.class,OptimisticLockingFailureException.class})
  public ProblemDetail conflict(Exception e) { return problem(409,"AUTHORIZATION_CONFLICT"); }
  @ExceptionHandler(AuthenticationRateLimiter.Limited.class)
  public ProblemDetail limited(Exception e) { return problem(429,"RATE_LIMITED"); }
  @ExceptionHandler({DataAccessException.class,org.springframework.transaction.TransactionException.class})
  public ProblemDetail unavailable(Exception e) { return problem(503,"AUTHORIZATION_UNAVAILABLE"); }
  private UUID actor() {
    var auth=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
    try { return auth==null?null:UUID.fromString(auth.getName()); } catch(IllegalArgumentException e) { return null; }
  }
  private ProblemDetail problem(int status,String code) {
    var auth=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
    var correlation=auth!=null && auth.getPrincipal() instanceof br.com.imarui.ima.authentication.api.module.TechnicalServiceAuthentication.Caller c?c.correlationId():UUID.randomUUID();
    var problem=ProblemDetail.forStatus(status); problem.setProperty("code",code); problem.setProperty("correlationId",correlation);
    problem.setProperty("timestamp",clock.instant());
    return problem;
  }
}
