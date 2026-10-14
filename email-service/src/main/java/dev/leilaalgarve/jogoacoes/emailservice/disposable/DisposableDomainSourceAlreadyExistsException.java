package dev.leilaalgarve.jogoacoes.emailservice.disposable;

/** Spec 05-048: A repository with this URL already exists (409). */
public class DisposableDomainSourceAlreadyExistsException extends RuntimeException {

    public DisposableDomainSourceAlreadyExistsException(String url) {
        super("A disposable domain repository with this URL already exists: " + url);
    }
}
