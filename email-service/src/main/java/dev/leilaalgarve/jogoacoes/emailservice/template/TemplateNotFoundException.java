package dev.leilaalgarve.jogoacoes.emailservice.template;

public class TemplateNotFoundException extends RuntimeException {

    public TemplateNotFoundException(String name) {
        super("No template named '" + name + "' for this client");
    }
}
