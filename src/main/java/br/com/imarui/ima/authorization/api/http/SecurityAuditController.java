package br.com.imarui.ima.authorization.api.http;

import br.com.imarui.ima.authorization.core.application.usecase.*;
import br.com.imarui.ima.authorization.core.repository.SecurityAuditRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/authorization/admin/audit")
@RequiredArgsConstructor
@SecurityRequirement(name="bearerAuth")
public class SecurityAuditController {
  private final SecurityAuditQueries audits;
  @GetMapping
  @Operation(summary="Read paginated security events only in the caller's audit jurisdiction")
  public AuthorizationQueries.Page<SecurityAuditRepository.Entry> query(@RequestParam UUID scopeId,@RequestParam(required=false) String event,
      @RequestParam(required=false) UUID correlationId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
    return audits.query(scopeId,event,correlationId,page,size);
  }
}
