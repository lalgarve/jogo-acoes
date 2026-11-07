package dev.leilaalgarve.jogoacoes.competition;

import dev.leilaalgarve.jogoacoes.api.model.EntryRequest;
import dev.leilaalgarve.jogoacoes.common.testsupport.CompetitionFixtures;
import dev.leilaalgarve.jogoacoes.competition.exception.EntryRequestValidationException;
import dev.leilaalgarve.jogoacoes.email.SentEmailRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Spec 05-036: an entry request with no e-mail, against the real Postgres of the docker profile.
 * The e-mail is checked before the captcha, so the token never reaches the verifier. With the
 * test context's stub sender, "no e-mail sent" means no new sent_email row.
 */
@SpringBootTest
class EntryRequestServiceIntegrationTest {

    @Autowired
    private EntryRequestService entryRequestService;

    @Autowired
    private ParticipationRepository participationRepository;

    @Autowired
    private SentEmailRepository sentEmailRepository;

    @Autowired
    private CompetitionFixtures competitionFixtures;

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"   "})
    void requestingEntryWithoutAnEmailIsRejectedAndCreatesNothing(String email) {
        Competition competition = competitionFixtures.publicCompetition();
        long sentBefore = sentEmailRepository.count();

        assertThatThrownBy(() -> entryRequestService.requestEntry(competition.getId(),
                new EntryRequest().email(email).captchaToken("not-checked")))
                .isInstanceOf(EntryRequestValidationException.class);

        assertThat(participationRepository.findByCompetition_Id(competition.getId())).isEmpty();
        assertThat(sentEmailRepository.count()).isEqualTo(sentBefore);
    }
}
