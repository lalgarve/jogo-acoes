package dev.leilaalgarve.jogoacoes.loginsession;

import dev.leilaalgarve.jogoacoes.email.OutgoingEmail;

import java.util.HashMap;
import java.util.Map;

/** A standalone login link, with no competition involved (spec 05-061). Template in {@link LoginEmailTemplates}. */
record LoginLinkEmail(Long userId, String recipient, String name, String link) implements OutgoingEmail {

    @Override
    public String templateName() {
        return "login-link";
    }

    @Override
    public Map<String, Object> templateData() {
        Map<String, Object> data = new HashMap<>();
        data.put("link", link);
        if (name != null) {
            data.put("name", name);
        }
        return data;
    }
}
