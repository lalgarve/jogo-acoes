package dev.leilaalgarve.jogoacoes.emailservice.template;

public class TemplateAlreadyExistsException extends RuntimeException {

    public TemplateAlreadyExistsException(String name) {
        super("A template named '" + name + "' already exists for this client");
    }
}
