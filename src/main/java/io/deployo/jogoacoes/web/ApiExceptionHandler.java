package io.deployo.jogoacoes.web;

import io.deployo.jogoacoes.api.model.Error;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(CompetitionValidationException.class)
    public ResponseEntity<Error> handleCompetitionValidation(CompetitionValidationException ex) {
        return ResponseEntity.badRequest().body(new Error().message(ex.getMessage()));
    }

    @ExceptionHandler(LoginLinkInvalidException.class)
    public ResponseEntity<Error> handleLoginLinkInvalid(LoginLinkInvalidException ex) {
        return ResponseEntity.badRequest().body(new Error().message(ex.getMessage()));
    }

    @ExceptionHandler(CompetitionNotFoundException.class)
    public ResponseEntity<Void> handleCompetitionNotFound() {
        return ResponseEntity.notFound().build();
    }

    /**
     * Bean Validation failures from @Valid request bodies (e.g. CompetitionCreateRequest's
     * emails list has @Email on each item) -- the generated DTOs carry format constraints
     * from the OpenAPI schema, so this is the layer that turns those into the Error shape
     * the contract promises instead of Spring's default validation error body.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Error> handleValidation(MethodArgumentNotValidException ex) {
        boolean emailFieldFailed = ex.getBindingResult().getFieldErrors().stream()
                .anyMatch(fieldError -> fieldError.getField().startsWith("emails"));
        String message = emailFieldFailed ? "Invalid e-mail" : "Invalid request";
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new Error().message(message));
    }
}
