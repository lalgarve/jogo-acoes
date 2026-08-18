package io.deployo.jogoacoes.testsupport;

import io.deployo.jogoacoes.domain.LoginLink;
import io.deployo.jogoacoes.domain.Participation;
import io.deployo.jogoacoes.domain.ParticipationStatus;
import io.deployo.jogoacoes.domain.RequestType;
import io.deployo.jogoacoes.repository.LoginLinkRepository;
import io.deployo.jogoacoes.repository.ParticipationRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** Builds LoginLink/Participation fixtures directly, bypassing HTTP -- these are Given-step
 * setup facts for login.feature, not the behavior under test. */
@Component
public class LoginLinkFixtures {

    private final LoginLinkRepository loginLinkRepository;
    private final ParticipationRepository participationRepository;

    public LoginLinkFixtures(LoginLinkRepository loginLinkRepository, ParticipationRepository participationRepository) {
        this.loginLinkRepository = loginLinkRepository;
        this.participationRepository = participationRepository;
    }

    /** A pending (not yet finished) participation with a fresh, unused link -- the "clicked
     * the login link received by e-mail" step consumes it. */
    public LoginLink pendingParticipationLink(io.deployo.jogoacoes.domain.Competition competition, String email, RequestType requestType) {
        Participation participation = new Participation();
        participation.setCompetition(competition);
        participation.setEmail(email);
        participation.setStatus(ParticipationStatus.EMAIL_SENT);
        participation.setRequestType(requestType);
        participation.setFirstEmailSentDate(LocalDate.now());
        participation = participationRepository.save(participation);
        return linkForParticipation(participation);
    }

    public LoginLink linkForParticipation(Participation participation) {
        LoginLink link = new LoginLink();
        link.setToken(UUID.randomUUID().toString());
        link.setEmail(participation.getEmail());
        link.setUser(participation.getUser());
        link.setParticipation(participation);
        link.setExpiresAt(LocalDateTime.now().plusDays(7));
        return loginLinkRepository.save(link);
    }

    public LoginLink expiredLink(String email) {
        LoginLink link = new LoginLink();
        link.setToken(UUID.randomUUID().toString());
        link.setEmail(email);
        link.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        return loginLinkRepository.save(link);
    }
}
