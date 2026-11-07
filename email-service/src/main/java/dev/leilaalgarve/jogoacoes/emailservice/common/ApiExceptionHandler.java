package dev.leilaalgarve.jogoacoes.emailservice.common;

import dev.leilaalgarve.jogoacoes.emailservice.api.model.Error;
import dev.leilaalgarve.jogoacoes.emailservice.disposable.DisposableDomainImportAlreadyRunningException;
import dev.leilaalgarve.jogoacoes.emailservice.disposable.DisposableDomainImportNotFoundException;
import dev.leilaalgarve.jogoacoes.emailservice.disposable.DisposableDomainSourceAlreadyExistsException;
import dev.leilaalgarve.jogoacoes.emailservice.disposable.DisposableDomainSourceNotFoundException;
import dev.leilaalgarve.jogoacoes.emailservice.domain.RecipientDomainRejectedException;
import dev.leilaalgarve.jogoacoes.emailservice.send.IdempotencyKeyReusedException;
import dev.leilaalgarve.jogoacoes.emailservice.send.SenderNotConfiguredException;
import dev.leilaalgarve.jogoacoes.emailservice.template.SesUnavailableException;
import dev.leilaalgarve.jogoacoes.emailservice.template.TemplateAlreadyExistsException;
import dev.leilaalgarve.jogoacoes.emailservice.template.TemplateNotFoundException;
import dev.leilaalgarve.jogoacoes.emailservice.template.TemplateRejectedBySesException;
import dev.leilaalgarve.jogoacoes.emailservice.template.TemplateRenderFailedException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(TemplateNotFoundException.class)
    public ResponseEntity<Error> handleTemplateNotFound(TemplateNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new Error().message(ex.getMessage()));
    }

    @ExceptionHandler(TemplateAlreadyExistsException.class)
    public ResponseEntity<Error> handleTemplateAlreadyExists(TemplateAlreadyExistsException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new Error().message(ex.getMessage()));
    }

    @ExceptionHandler(TemplateRejectedBySesException.class)
    public ResponseEntity<Error> handleTemplateRejectedBySes(TemplateRejectedBySesException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(new Error().message(ex.getMessage()));
    }

    @ExceptionHandler(TemplateRenderFailedException.class)
    public ResponseEntity<Error> handleTemplateRenderFailed(TemplateRenderFailedException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(new Error().message(ex.getMessage()));
    }

    /** Spec 05-044: SES unreachable or failing on its side -- the request may succeed later. */
    @ExceptionHandler(SesUnavailableException.class)
    public ResponseEntity<Error> handleSesUnavailable(SesUnavailableException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new Error().message(ex.getMessage()));
    }

    @ExceptionHandler(SenderNotConfiguredException.class)
    public ResponseEntity<Error> handleSenderNotConfigured(SenderNotConfiguredException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new Error().message(ex.getMessage()));
    }

    /** Spec 05-047/05-048: the recipient's domain does not accept e-mail, or is disposable. */
    @ExceptionHandler(RecipientDomainRejectedException.class)
    public ResponseEntity<Error> handleRecipientDomainRejected(RecipientDomainRejectedException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(new Error().message(ex.getMessage()));
    }

    @ExceptionHandler({DisposableDomainSourceNotFoundException.class, DisposableDomainImportNotFoundException.class})
    public ResponseEntity<Error> handleDisposableDomainNotFound(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new Error().message(ex.getMessage()));
    }

    /** Spec 05-048: the URL is already registered, or an import of the repository is running. */
    @ExceptionHandler({DisposableDomainSourceAlreadyExistsException.class,
            DisposableDomainImportAlreadyRunningException.class})
    public ResponseEntity<Error> handleDisposableDomainConflict(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new Error().message(ex.getMessage()));
    }

    /** Bean Validation on the generated request DTOs (required fields, {@code format: email}). */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Error> handleInvalidRequest(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .sorted()
                .reduce((a, b) -> a + "; " + b)
                .orElse("Invalid request");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new Error().message(message));
    }

    /** Spec 05-059: the same Idempotency-Key with a different body -- nothing new was recorded. */
    @ExceptionHandler(IdempotencyKeyReusedException.class)
    public ResponseEntity<Error> handleIdempotencyKeyReused(IdempotencyKeyReusedException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(new Error().message(ex.getMessage()));
    }

    /** A required header is missing (spec 05-059: Idempotency-Key on POST /emails). */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<Error> handleMissingHeader(MissingRequestHeaderException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new Error().message("Missing header " + ex.getHeaderName()));
    }

    /**
     * A parameter outside its declared constraints (spec 05-059: Idempotency-Key empty or too
     * long). The generated API interface is {@code @Validated}, so method validation reports it as
     * a {@link ConstraintViolationException}, named by the last node of the path ("idempotencyKey").
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Error> handleInvalidParameter(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .map(violation -> lastNode(violation.getPropertyPath().toString()) + " " + violation.getMessage())
                .sorted()
                .reduce((a, b) -> a + "; " + b)
                .orElse("Invalid request");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new Error().message(message));
    }

    private static String lastNode(String path) {
        return path.substring(path.lastIndexOf('.') + 1);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Error> handleUnreadableRequest(HttpMessageNotReadableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new Error().message("Malformed request body"));
    }
}
