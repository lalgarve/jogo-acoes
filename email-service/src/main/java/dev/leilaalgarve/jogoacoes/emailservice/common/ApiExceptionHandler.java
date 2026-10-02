package dev.leilaalgarve.jogoacoes.emailservice.common;

import dev.leilaalgarve.jogoacoes.emailservice.api.model.Error;
import dev.leilaalgarve.jogoacoes.emailservice.template.TemplateAlreadyExistsException;
import dev.leilaalgarve.jogoacoes.emailservice.template.TemplateNotFoundException;
import dev.leilaalgarve.jogoacoes.emailservice.template.TemplateRejectedBySesException;
import dev.leilaalgarve.jogoacoes.emailservice.template.TemplateRenderFailedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
}
