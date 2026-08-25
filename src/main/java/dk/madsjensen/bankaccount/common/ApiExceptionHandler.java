package dk.madsjensen.bankaccount.common;

import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

// Return the same simple error format for expected API errors.
@RestControllerAdvice
public class ApiExceptionHandler {

    // Preserve the status and safe message chosen by the service or external client.
    @ExceptionHandler(ResponseStatusException.class)
    ProblemDetail handleStatus(ResponseStatusException exception) {
        return ProblemDetail.forStatusAndDetail(
                exception.getStatusCode(),
                exception.getReason() == null ? "Request failed" : exception.getReason()
        );
    }

    // Body validation and query-parameter validation enter Spring through different exceptions.
    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class})
    ProblemDetail handleValidation(Exception exception) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "One or more request values are invalid"
        );
    }

    // Optimistic locking prevents two requests from silently overwriting one balance update.
    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail handleConcurrentUpdate() {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                "The account was changed by another request"
        );
    }
}
