package dev.leilaalgarve.jogoacoes.emailservice.send;

public class SenderNotConfiguredException extends RuntimeException {

    public SenderNotConfiguredException() {
        super("No sender address has been configured for this client yet");
    }
}
