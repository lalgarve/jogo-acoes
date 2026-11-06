package dev.leilaalgarve.jogoacoes.email;

import java.util.Map;

/**
 * An e-mail a client module wants sent (spec 05-061). The client builds it and picks which of its
 * own templates (see {@link EmailTemplateSource}) renders it; the email module only sends it and
 * records the send, without knowing which module asked.
 */
public interface OutgoingEmail {

    /** The recipient's account, or null when they don't have one yet. */
    Long userId();

    String recipient();

    /** The link the e-mail carries; recorded in {@code sent_email}. */
    String link();

    /** One of the names the client's {@link EmailTemplateSource} declares. */
    String templateName();

    /** The variables its template uses; absent values are left out rather than sent as null. */
    Map<String, Object> templateData();
}
