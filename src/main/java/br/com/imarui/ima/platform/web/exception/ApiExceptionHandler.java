package br.com.imarui.ima.platform.web.exception;
import org.springframework.http.*;import java.time.Clock;
/** Shared RFC9457 formatting for the preserved Authentication administration endpoints. */
public abstract class ApiExceptionHandler {
 private final Clock clock;
 protected ApiExceptionHandler(Clock clock){this.clock=clock;}
 protected ResponseEntity<ProblemDetail> buildErrorResponse(String code,String ignoredMessage,HttpStatus status){
  var problem=ProblemDetail.forStatusAndDetail(status,status==HttpStatus.NOT_FOUND?"Requested resource was not found.":"Operation is not permitted in the current state.");problem.setProperty("code",code);problem.setProperty("timestamp",clock.instant());return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
 }
}
