package dev.leilaalgarve.jogoacoes.emailservice.common;

import dev.leilaalgarve.jogoacoes.emailservice.api.model.Error;
import dev.leilaalgarve.jogoacoes.emailservice.send.EmailQueuePublishException;
import dev.leilaalgarve.jogoacoes.emailservice.send.SenderNotConfiguredException;
import dev.leilaalgarve.jogoacoes.emailservice.template.TemplateAlreadyExistsException;
import dev.leilaalgarve.jogoacoes.emailservice.template.TemplateNotFoundException;
import dev.leilaalgarve.jogoacoes.emailservice.template.TemplateRejectedBySesException;
import dev.leilaalgarve.jogoacoes.emailservice.template.TemplateRenderFailedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
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

    @ExceptionHandler(SenderNotConfiguredException.class)
    public ResponseEntity<Error> handleSenderNotConfigured(SenderNotConfiguredException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new Error().message(ex.getMessage()));
    }

    @ExceptionHandler(EmailQueuePublishException.class)
    public ResponseEntity<Error> handleEmailQueuePublish(EmailQueuePublishException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new Error().message(ex.getMessage()));
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

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Error> handleUnreadableRequest(HttpMessageNotReadableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new Error().message("Malformed request body"));
    }
}
