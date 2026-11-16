package dev.leilaalgarve.jogoacoes.loginsession;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Spec 05-061: the standalone login link, with no competition involved. */
class LoginLinkEmailTest {

    @Test
    void usesTheLoginLinkTemplateWithNameAndLink() {
        LoginLinkEmail email = new LoginLinkEmail(1L, "ana@jogo-acoes.test", "Ana", "https://jogo-acoes.example/l");

        assertThat(email.templateName()).isEqualTo("login-link");
        assertThat(email.templateData()).isEqualTo(Map.of("name", "Ana", "link", "https://jogo-acoes.example/l"));
    }
}
