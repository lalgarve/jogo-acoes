package dev.leilaalgarve.jogoacoes.common.testsupport;

import dev.leilaalgarve.jogoacoes.competition.CompetitionLinkHandler;
import dev.leilaalgarve.jogoacoes.competition.Participation;
import dev.leilaalgarve.jogoacoes.competition.ParticipationStatus;
import dev.leilaalgarve.jogoacoes.competition.RequestType;
import dev.leilaalgarve.jogoacoes.link.LinkRecord;
import dev.leilaalgarve.jogoacoes.link.LinkRecordRepository;
import dev.leilaalgarve.jogoacoes.competition.ParticipationRepository;
import dev.leilaalgarve.jogoacoes.login.LoginLinkHandler;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/** Builds LinkRecord/Participation fixtures directly, bypassing HTTP -- these are Given-step
 * setup facts for login.feature, not the behavior under test. */
@Component
public class LoginLinkFixtures {

    private final LinkRecordRepository linkRecordRepository;
    private final ParticipationRepository participationRepository;

    public LoginLinkFixtures(LinkRecordRepository linkRecordRepository, ParticipationRepository participationRepository) {
        this.linkRecordRepository = linkRecordRepository;
        this.participationRepository = participationRepository;
    }

    /** A pending (not yet finished) participation with a fresh, unused link -- the "clicked
     * the login link received by e-mail" step consumes it. */
    public LinkRecord pendingParticipationLink(dev.leilaalgarve.jogoacoes.competition.Competition competition, String email, RequestType requestType) {
        Participation participation = new Participation();
        participation.setCompetition(competition);
        participation.setEmail(email);
        participation.setStatus(ParticipationStatus.EMAIL_SENT);
        participation.setRequestType(requestType);
        participation.setFirstEmailSentDate(LocalDate.now());
        participation = participationRepository.save(participation);
        return linkForParticipation(participation);
    }

    public LinkRecord linkForParticipation(Participation participation) {
        LinkRecord link = new LinkRecord();
        link.setToken(UUID.randomUUID().toString());
        link.setServiceKey(CompetitionLinkHandler.KEY);
        link.setUserId(participation.getUser() != null ? participation.getUser().getId() : null);
        link.setEmail(participation.getEmail());
        link.setExtraJson("{\"" + CompetitionLinkHandler.PARTICIPATION_ID_EXTRA_KEY + "\":\"" + participation.getId() + "\"}");
        link.setExpiresAt(LocalDateTime.now().plusDays(7));
        return linkRecordRepository.save(link);
    }

    public LinkRecord expiredLink(String email) {
        LinkRecord link = new LinkRecord();
        link.setToken(UUID.randomUUID().toString());
        link.setServiceKey(LoginLinkHandler.KEY);
        link.setEmail(email);
        link.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        return linkRecordRepository.save(link);
    }
}
