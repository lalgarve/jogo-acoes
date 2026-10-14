package dev.leilaalgarve.jogoacoes.emailservice.disposable;

/** Spec 05-048: An import of the repository is already running; nothing was started (409). */
public class DisposableDomainImportAlreadyRunningException extends RuntimeException {

    public DisposableDomainImportAlreadyRunningException(long sourceId) {
        super("An import of disposable domain repository " + sourceId + " is already running");
    }
}
