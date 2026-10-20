package dev.leilaalgarve.jogoacoes.emailservice.template;

/**
 * SES could not be reached, or answered with a server-side error or throttling (spec 05-044) --
 * unlike {@link TemplateRejectedBySesException}, nothing is wrong with the template itself and the
 * same call may succeed later.
 */
public class SesUnavailableException extends RuntimeException {

    public SesUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
