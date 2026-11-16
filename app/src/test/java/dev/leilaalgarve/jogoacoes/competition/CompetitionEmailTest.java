package dev.leilaalgarve.jogoacoes.competition;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Spec 05-061: competition picks its own templates; the same five choices email used to make. */
class CompetitionEmailTest {

    private static final String LINK = "https://jogo-acoes.example/links/abc";

    @Test
    void inviteToSomeoneWithoutAnAccount() {
        CompetitionEmail email = CompetitionEmail.invite(null, "ana@jogo-acoes.test", "Copa", LINK);

        assertThat(email.templateName()).isEqualTo("invite");
        assertThat(email.templateData()).isEqualTo(Map.of("competitionName", "Copa", "link", LINK));
    }

    @Test
    void registrationLinkToSomeoneWithoutAnAccount() {
        assertThat(CompetitionEmail.registrationLink(null, "ana@jogo-acoes.test", "Copa", LINK).templateName())
                .isEqualTo("registration-link");
    }

    @Test
    void loginLinkFollowsHowThePlayerGotThere() {
        assertThat(CompetitionEmail.loginLink(1L, "ana@jogo-acoes.test", "Ana", "Copa", RequestType.INVITE, LINK)
                .templateName()).isEqualTo("login-link-invite");
        assertThat(CompetitionEmail.loginLink(1L, "ana@jogo-acoes.test", "Ana", "Copa", RequestType.REQUEST, LINK)
                .templateName()).isEqualTo("login-link-request");
    }

    @Test
    void templateDataCarriesTheNameOfARegisteredPlayer() {
        CompetitionEmail email = CompetitionEmail.loginLink(1L, "ana@jogo-acoes.test", "Ana", "Copa",
                RequestType.INVITE, LINK);

        assertThat(email.templateData()).isEqualTo(Map.of("name", "Ana", "competitionName", "Copa", "link", LINK));
    }
}
