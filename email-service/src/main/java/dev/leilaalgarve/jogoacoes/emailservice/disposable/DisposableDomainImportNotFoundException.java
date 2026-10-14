package dev.leilaalgarve.jogoacoes.emailservice.disposable;

/** Spec 05-048: No import execution with this id (404). */
public class DisposableDomainImportNotFoundException extends RuntimeException {

    public DisposableDomainImportNotFoundException(long executionId) {
        super("No disposable domain import with id " + executionId);
    }
}
