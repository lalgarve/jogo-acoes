package dev.leilaalgarve.jogoacoes.emailservice.template;

/**
 * SES could not render the template with the given variables (e.g. a referenced variable is
 * missing) -- the message carries SES's own reason, passed through as-is.
 */
public class TemplateRenderFailedException extends RuntimeException {

    public TemplateRenderFailedException(String sesMessage) {
        super(sesMessage);
    }
}
