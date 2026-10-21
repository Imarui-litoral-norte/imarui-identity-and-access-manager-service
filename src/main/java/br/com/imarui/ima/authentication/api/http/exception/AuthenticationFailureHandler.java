package br.com.imarui.ima.authentication.api.http.exception;

import br.com.imarui.ima.authentication.core.application.exception.AuthenticationFailure;
import br.com.imarui.ima.authentication.infra.security.AuthenticationRateLimiter;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(basePackages = "br.com.imarui.ima.authentication.api.http.controller")
@Order(-10)
@RequiredArgsConstructor
public class AuthenticationFailureHandler {
  private final Clock clock;

  @ExceptionHandler(AuthenticationFailure.class)
  public ResponseEntity<ProblemDetail> failure() {
    return problem(401, "AUTHENTICATION_FAILED");
  }

  @ExceptionHandler(AuthenticationRateLimiter.Limited.class)
  public ResponseEntity<ProblemDetail> limit() {
    return problem(429, "TOO_MANY_ATTEMPTS");
  }

  @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
  public ResponseEntity<ProblemDetail> forbidden() {
    return problem(403, "ACCESS_DENIED");
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ProblemDetail> validation() {
    return problem(400, "AUTHENTICATION_VALIDATION_ERROR");
  }

  private ResponseEntity<ProblemDetail> problem(int status, String code) {
    var p = ProblemDetail.forStatus(status);
    p.setTitle(org.springframework.http.HttpStatus.valueOf(status).getReasonPhrase());
    p.setProperty("code", code);
    p.setProperty("timestamp", clock.instant());
    return ResponseEntity.status(status)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .body(p);
  }
}
