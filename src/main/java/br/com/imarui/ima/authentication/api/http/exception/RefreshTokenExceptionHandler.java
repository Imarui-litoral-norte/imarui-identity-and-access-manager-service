package br.com.imarui.ima.authentication.api.http.exception;

import br.com.imarui.ima.authentication.core.application.exceptions.refreshtoken.RefreshTokenInvalidException;
import br.com.imarui.ima.authentication.core.application.exceptions.refreshtoken.RefreshTokenNotFoundException;
import br.com.imarui.ima.authentication.core.domain.exception.refreshtoken.RefreshTokenStateException;
import br.com.imarui.ima.authentication.core.domain.exception.refreshtoken.RefreshTokenValidationException;
import org.springframework.http.ProblemDetail;
import br.com.imarui.ima.platform.web.exception.ApiExceptionHandler;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(
        basePackages = "br.com.imarui.ima.authentication"
)
public class RefreshTokenExceptionHandler
        extends ApiExceptionHandler {
    public RefreshTokenExceptionHandler(java.time.Clock clock){super(clock);}

    @ExceptionHandler(
            RefreshTokenValidationException.class
    )
    public ResponseEntity<ProblemDetail>
    handleRefreshTokenValidation(
            RefreshTokenValidationException exception
    ) {
        return buildErrorResponse(
                "REFRESH_TOKEN_INVALID_DATA",
                exception.getMessage(),
                HttpStatus.BAD_REQUEST
        );
    }

    @ExceptionHandler(
            RefreshTokenStateException.class
    )
    public ResponseEntity<ProblemDetail>
    handleRefreshTokenState(
            RefreshTokenStateException exception
    ) {
        return buildErrorResponse(
                "REFRESH_TOKEN_STATE_CONFLICT",
                exception.getMessage(),
                HttpStatus.CONFLICT
        );
    }

    @ExceptionHandler(
            RefreshTokenInvalidException.class
    )
    public ResponseEntity<ProblemDetail>
    handleRefreshTokenInvalid(
            RefreshTokenInvalidException exception
    ) {
        return buildErrorResponse(
                "REFRESH_TOKEN_INVALID",
                exception.getMessage(),
                HttpStatus.UNAUTHORIZED
        );
    }

    @ExceptionHandler(
            RefreshTokenNotFoundException.class
    )
    public ResponseEntity<ProblemDetail>
    handleRefreshTokenNotFound(
            RefreshTokenNotFoundException exception
    ) {
        return buildErrorResponse(
                "REFRESH_TOKEN_NOT_FOUND",
                exception.getMessage(),
                HttpStatus.NOT_FOUND
        );
    }
}
