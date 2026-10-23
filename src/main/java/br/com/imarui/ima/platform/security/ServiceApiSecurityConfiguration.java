package br.com.imarui.ima.platform.security;

import br.com.imarui.ima.authentication.api.module.TechnicalServiceAuthentication;
import br.com.imarui.ima.authentication.core.exception.TechnicalAuthenticationException;
import br.com.imarui.ima.authentication.infra.security.AuthenticationRateLimiter;
import br.com.imarui.ima.authorization.api.module.SecurityAuditEvents;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.*;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

@Configuration
@RequiredArgsConstructor
public class ServiceApiSecurityConfiguration {
  private final TechnicalServiceAuthentication authentication;
  private final AuthenticationRateLimiter limits;
  private final SecurityAuditEvents events;
  private final JsonMapper json;
  private final MeterRegistry metrics;
  private final java.time.Clock clock;

  @Bean @Order(1)
  public SecurityFilterChain technicalServiceSecurity(HttpSecurity http) throws Exception {
    var filter=new OncePerRequestFilter() {
      protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws ServletException,IOException {
        TechnicalServiceAuthentication.Caller caller=null;
        UUID correlation=UUID.randomUUID(); var sample=io.micrometer.core.instrument.Timer.start(metrics);
        try {
          // Bound unauthenticated work across instances; no user-supplied forwarded address is trusted.
          limits.check("m2m-source",req.getRemoteAddr(),6000);
          if(!"POST".equals(req.getMethod()) || req.getQueryString()!=null) throw new SecurityException("Invalid service operation.");
          String header=req.getHeader(HttpHeaders.AUTHORIZATION);
          if(header==null || !header.startsWith("Service ") || Collections.list(req.getHeaders(HttpHeaders.AUTHORIZATION)).size()!=1)
            throw new TechnicalAuthenticationException();
          try {
            var content=MediaType.parseMediaType(req.getContentType()==null?"":req.getContentType());
            if(!content.getType().equals("application") || !content.getSubtype().equals("json")
                || content.getCharset()!=null && !content.getCharset().equals(java.nio.charset.StandardCharsets.UTF_8)
                || req.getHeader("Content-Encoding")!=null && !req.getHeader("Content-Encoding").equalsIgnoreCase("identity")) {
              problem(res,415,"UTF8_JSON_REQUIRED",correlation); return;
            }
          } catch(IllegalArgumentException unsupported) { problem(res,415,"UTF8_JSON_REQUIRED",correlation); return; }
          if(req.getContentLengthLong()>65536) { problem(res,413,"REQUEST_TOO_LARGE",correlation); return; }
          var body=req.getInputStream().readNBytes(65537);
          if(body.length>65536) { problem(res,413,"REQUEST_TOO_LARGE",correlation); return; }
          caller=authentication.verify(header.substring(8),req.getMethod(),req.getRequestURI(),body);
          correlation=caller.correlationId();
          limits.check("m2m-client",caller.clientId().toString(),3000);
          var operation=switch(req.getRequestURI()) {
            case "/api/v1/authorization/service/catalog" -> TechnicalServiceAuthentication.Operation.CATALOG_SYNC;
            case "/api/v1/authorization/service/decisions", "/api/v1/authorization/service/decisions/batch" -> TechnicalServiceAuthentication.Operation.PDP_DECIDE;
            default -> throw new SecurityException("Unknown service operation.");
          };
          if(!caller.operations().contains(operation)) throw new SecurityException("Technical operation is not authorized.");
          events.record("M2M_AUTHENTICATION",null,caller.serviceCode(),caller.credentialId().toString(),null,"AUTHENTICATED",correlation);
          SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(caller,null,List.of()));
          res.setHeader("Cache-Control","no-store");
          res.setHeader("X-Correlation-ID",correlation.toString());
          chain.doFilter(new BodyRequest(req,body),res);
        } catch(TechnicalAuthenticationException e) {
          auditProblem(res,401,"INVALID_TECHNICAL_AUTHENTICATION",null,"M2M_AUTHENTICATION",e.reason()==TechnicalAuthenticationException.Reason.REPLAY?"REPLAY_DETECTED":"INVALID_CREDENTIAL",correlation);
        } catch(AuthenticationRateLimiter.Limited e) {
          problem(res,429,"RATE_LIMITED",correlation);
        } catch(SecurityException e) {
          auditProblem(res,403,"TECHNICAL_OPERATION_DENIED",caller,"M2M_AUTHORIZATION","OPERATION_DENIED",correlation);
        } catch(org.springframework.dao.DataAccessException | org.springframework.transaction.TransactionException e) {
          metrics.counter("iiam.authorization.dependency.failures","reason",
              e instanceof org.springframework.dao.QueryTimeoutException || e instanceof org.springframework.transaction.TransactionTimedOutException?"TIMEOUT":"DATABASE_OR_TRANSACTION").increment();
          problem(res,503,"AUTHORIZATION_UNAVAILABLE",correlation);
        } finally {
          sample.stop(metrics.timer("iiam.m2m.request","outcome",Integer.toString(res.getStatus())));
          SecurityContextHolder.clearContext();
        }
      }
    };
    return http.securityMatcher("/api/v1/authorization/service/**")
        .csrf(c -> c.disable()).sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(a -> a.anyRequest().authenticated())
        .addFilterBefore(filter,UsernamePasswordAuthenticationFilter.class)
        .exceptionHandling(e -> e.authenticationEntryPoint((q,r,x) -> problem(r,401,"TECHNICAL_AUTHENTICATION_REQUIRED",UUID.randomUUID()))
            .accessDeniedHandler((q,r,x) -> problem(r,403,"TECHNICAL_OPERATION_DENIED",UUID.randomUUID())))
        .build();
  }

  private void auditProblem(HttpServletResponse response,int status,String code,TechnicalServiceAuthentication.Caller caller,
      String event,String reason,UUID correlation) throws IOException {
    try {
      events.record(event,null,caller==null?null:caller.serviceCode(),null,null,reason,correlation);
      problem(response,status,code,correlation);
    } catch(RuntimeException unavailable) { problem(response,503,"SECURITY_AUDIT_UNAVAILABLE",correlation); }
  }

  private void problem(HttpServletResponse response,int status,String code,UUID correlation) throws IOException {
    if(response.isCommitted()) return;
    metrics.counter("iiam.authorization.http.failures","code",code).increment();
    var p=ProblemDetail.forStatus(status); p.setProperty("code",code); p.setProperty("correlationId",correlation);
    p.setProperty("timestamp",clock.instant());
    response.setStatus(status); response.setHeader("Cache-Control","no-store");
    response.setHeader("X-Correlation-ID",correlation.toString());
    if(status==401) response.setHeader("WWW-Authenticate","Service");
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    json.writeValue(response.getOutputStream(),p);
  }

  private static final class BodyRequest extends HttpServletRequestWrapper {
    private final byte[] body;
    BodyRequest(HttpServletRequest request,byte[] body) { super(request); this.body=body; }
    @Override public int getContentLength() { return body.length; }
    @Override public long getContentLengthLong() { return body.length; }
    @Override public ServletInputStream getInputStream() {
      var input=new ByteArrayInputStream(body);
      return new ServletInputStream() {
        public int read() { return input.read(); }
        public boolean isFinished() { return input.available()==0; }
        public boolean isReady() { return true; }
        public void setReadListener(ReadListener listener) { throw new IllegalStateException("Async body reading is unsupported."); }
      };
    }
    @Override public BufferedReader getReader() { return new BufferedReader(new InputStreamReader(getInputStream(),java.nio.charset.StandardCharsets.UTF_8)); }
  }
}
