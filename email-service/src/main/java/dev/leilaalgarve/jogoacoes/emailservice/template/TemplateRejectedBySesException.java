package dev.leilaalgarve.jogoacoes.emailservice.template;

/**
 * SES refused to create/update the template -- the message carries SES's own reason
 * (e.g. invalid Handlebars syntax, template limit exceeded), passed through as-is.
 */
public class TemplateRejectedBySesException extends RuntimeException {

    public TemplateRejectedBySesException(String sesMessage) {
        super(sesMessage);
    }
}
