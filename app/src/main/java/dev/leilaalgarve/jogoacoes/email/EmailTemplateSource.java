package dev.leilaalgarve.jogoacoes.email;

import java.util.List;

/**
 * The e-mail templates a client module owns (spec 05-061). Every implementation is a bean; the
 * email module wraps each content in the shared layout and registers it in email-service when app
 * starts. A new client adds its own source instead of changing the email module.
 */
public interface EmailTemplateSource {

    List<TemplateDefinition> templates();
}
