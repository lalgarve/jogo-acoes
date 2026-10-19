package dev.leilaalgarve.jogoacoes.emailservice.disposable;

/** Spec 05-048: No repository with this id (404). */
public class DisposableDomainSourceNotFoundException extends RuntimeException {

    public DisposableDomainSourceNotFoundException(long id) {
        super("No disposable domain repository with id " + id);
    }
}
