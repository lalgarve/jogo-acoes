package dev.leilaalgarve.jogoacoes.emailservice.disposable.batch;

/**
 * Spec 05-048: a list could not be downloaded, or what came is not a list this job imports
 * (status other than 200, timeout, larger than the limit, empty). Fails the execution of that
 * repository only; its message is the import's exitMessage.
 */
public class DisposableListDownloadException extends RuntimeException {

    public DisposableListDownloadException(String message) {
        super(message);
    }

    public DisposableListDownloadException(String message, Throwable cause) {
        super(message, cause);
    }
}
