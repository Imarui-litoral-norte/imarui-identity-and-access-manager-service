package br.com.imarui.ima.identity.api.exception;

import br.com.imarui.ima.identity.core.application.exception.*;
import br.com.imarui.ima.identity.core.application.exception.identity.IdentityNotFoundException;
import br.com.imarui.ima.identity.core.domain.exception.identity.*;
import br.com.imarui.ima.identity.core.domain.exception.identityapplication.*;
import br.com.imarui.ima.identity.core.domain.exception.affiliation.*;
import br.com.imarui.ima.identity.core.domain.exception.tenant.*;
import br.com.imarui.ima.identity.core.application.exception.tenant.TenantNotFoundException;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.ConcurrencyFailureException;

@RestControllerAdvice(basePackages = "br.com.imarui.ima.identity.api.controller")
@Order(10)
@RequiredArgsConstructor
public class IdentityExceptionHandler {
    private final Clock clock;
    private ProblemDetail problem(HttpStatus status, String code, String detail) {
        var response = ProblemDetail.forStatusAndDetail(status, detail);
        response.setProperty("code", code);
        response.setProperty("timestamp", clock.instant());
        return response;
    }
    @ExceptionHandler({IdentityNotFoundException.class, IdentityApplicationNotFoundException.class, TenantNotFoundException.class})
    public ProblemDetail notFound(RuntimeException ex) {
        return problem(HttpStatus.NOT_FOUND, ex instanceof IdentityNotFoundException ? "IDENTITY_NOT_FOUND" : ex instanceof TenantNotFoundException ? "TENANT_NOT_FOUND" : "IDENTITY_APPLICATION_NOT_FOUND", "Requested resource was not found.");
    }
    @ExceptionHandler(CurrentActorUnavailableException.class)
    public ProblemDetail unauthenticated() { return problem(HttpStatus.UNAUTHORIZED, "CURRENT_ACTOR_REQUIRED", "An authenticated Identity subject is required."); }
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ProblemDetail forbidden() { return problem(HttpStatus.FORBIDDEN, "IDENTITY_ACCESS_DENIED", "Access to this resource is not permitted."); }
    @ExceptionHandler({IdentityConflictException.class, DataIntegrityViolationException.class, ConcurrencyFailureException.class})
    public ProblemDetail conflict() { return problem(HttpStatus.CONFLICT, "IDENTITY_CONFLICT", "Operation conflicts with an existing record or concurrent change."); }
    @ExceptionHandler({IdentityAlreadyActiveException.class, IdentityAlreadyDisabledException.class, IdentityNotDisabledException.class,
        InvalidIdentityStateException.class, IdentityApplicationReviewNotAllowedException.class, InvalidIdentityApplicationStateException.class,
        AffiliationAlreadyActiveException.class, AffiliationAlreadyInactiveException.class, InvalidAffiliationStateException.class, AffiliationEndBeforeStartException.class, TenantUnavailableException.class})
    public ProblemDetail invalidState() { return problem(HttpStatus.CONFLICT, "IDENTITY_INVALID_STATE", "Operation is not permitted in the current state."); }
    @ExceptionHandler({IllegalArgumentException.class, InvalidIdentityEmailException.class, InvalidFullNameException.class,
        InvalidCpfException.class, InvalidCnpjException.class, InvalidLegalNameException.class, InvalidTradeNameException.class,
        InvalidPhoneNumberException.class, InvalidServiceNameException.class, InvalidServiceDescriptionException.class,
        InvalidEmployeeRegistrationException.class, InvalidCustomerCodeException.class, InvalidSupplierCodeException.class, InvalidTenantIdException.class, InvalidTenantNameException.class, InvalidTenantCodeException.class})
    public ProblemDetail validation() { return problem(HttpStatus.BAD_REQUEST, "IDENTITY_VALIDATION_ERROR", "One or more supplied values are invalid."); }
}
