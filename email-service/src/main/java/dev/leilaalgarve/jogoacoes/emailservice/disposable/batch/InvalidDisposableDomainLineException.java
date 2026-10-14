package dev.leilaalgarve.jogoacoes.emailservice.disposable.batch;

/**
 * Spec 05-048: a line of a list that is not a domain. The import step skips it (counted as
 * the import's skipped lines) and goes on with the rest of the list.
 */
public class InvalidDisposableDomainLineException extends RuntimeException {

    public InvalidDisposableDomainLineException(String line) {
        super("Not a domain: " + line);
    }
}
