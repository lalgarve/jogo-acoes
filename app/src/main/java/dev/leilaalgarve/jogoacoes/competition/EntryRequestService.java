package dev.leilaalgarve.jogoacoes.competition;

import dev.leilaalgarve.jogoacoes.api.model.EntryRequest;
import dev.leilaalgarve.jogoacoes.captcha.CaptchaInvalidException;
import dev.leilaalgarve.jogoacoes.captcha.CaptchaVerifier;
import dev.leilaalgarve.jogoacoes.competition.Competition;
import dev.leilaalgarve.jogoacoes.competition.CompetitionRepository;
import dev.leilaalgarve.jogoacoes.competition.CompetitionType;
import dev.leilaalgarve.jogoacoes.competition.Participation;
import dev.leilaalgarve.jogoacoes.competition.ParticipationRepository;
import dev.leilaalgarve.jogoacoes.competition.ParticipationStatus;
import dev.leilaalgarve.jogoacoes.competition.RequestType;
import dev.leilaalgarve.jogoacoes.competition.exception.CompetitionNotFoundException;
import dev.leilaalgarve.jogoacoes.competition.exception.EntryRequestValidationException;
import dev.leilaalgarve.jogoacoes.email.EmailSender;
import dev.leilaalgarve.jogoacoes.link.LinkCreationResult;
import dev.leilaalgarve.jogoacoes.link.LinkService;
import dev.leilaalgarve.jogoacoes.link.dto.LinkPayload;
import dev.leilaalgarve.jogoacoes.log.AuditLogService;
import dev.leilaalgarve.jogoacoes.log.LogType;
import dev.leilaalgarve.jogoacoes.loginsession.CurrentUserService;
import dev.leilaalgarve.jogoacoes.user.User;
import dev.leilaalgarve.jogoacoes.user.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

@Service
public class EntryRequestService {

    private final CompetitionRepository competitionRepository;
    private final ParticipationRepository participationRepository;
    private final LinkService linkService;
    private final UserService userService;
    private final CurrentUserService currentUserService;
    private final EmailSender emailSender;
    private final CaptchaVerifier captchaService;
    private final AuditLogService auditLogService;

    public EntryRequestService(CompetitionRepository competitionRepository, ParticipationRepository participationRepository,
                                LinkService linkService, UserService userService, CurrentUserService currentUserService,
                                EmailSender emailSender, CaptchaVerifier captchaService, AuditLogService auditLogService) {
        this.competitionRepository = competitionRepository;
        this.participationRepository = participationRepository;
        this.linkService = linkService;
        this.userService = userService;
        this.currentUserService = currentUserService;
        this.emailSender = emailSender;
        this.captchaService = captchaService;
        this.auditLogService = auditLogService;
    }

    /**
     * Logged-in player confirming entry directly from their session -- no e-mail round trip.
     * Spec 05-057: confirming again leaves an IN_COMPETITION participation as it is (same
     * joinedAt, no new audit entry).
     */
    @Transactional
    public Participation confirmEntry(Long competitionId) {
        Competition competition = findCompetition(competitionId);
        User user = currentUserService.currentUser();

        Participation participation = participationRepository.findByCompetition_IdAndUser_Id(competitionId, user.getId())
                .orElse(null);

        if (competition.getType() == CompetitionType.PRIVATE && participation == null) {
            // No invite on record -- 404 rather than 403, so as not to reveal the competition exists.
            throw new CompetitionNotFoundException(competitionId);
        }

        if (participation != null && participation.getStatus() == ParticipationStatus.IN_COMPETITION) {
            return participation;
        }
        if (participation == null) {
            participation = participationFor(competition, user.getEmail(), user);
            if (participation.getStatus() == ParticipationStatus.IN_COMPETITION) {
                return participation;
            }
            participation.setUser(user);
        }
        participation.setStatus(ParticipationStatus.IN_COMPETITION);
        participation.setJoinedAt(LocalDate.now());
        participation = participationRepository.save(participation);
        auditLogService.record(LogType.PARTICIPATION_STATUS_CHANGED, participation.getId(), user,
                "Participation status changed to IN_COMPETITION");
        return participation;
    }

    /**
     * Unregistered or logged-out player: e-mail + captcha, gets a link instead of immediate entry.
     * Spec 05-057: a repeated request reuses the e-mail's participation, whatever its status; one
     * already IN_COMPETITION gets the link again but keeps its status.
     */
    @Transactional
    public void requestEntry(Long competitionId, EntryRequest request) {
        Competition competition = findCompetition(competitionId);
        if (competition.getType() == CompetitionType.PRIVATE) {
            throw new CompetitionNotFoundException(competitionId);
        }

        String email = request != null ? request.getEmail() : null;
        if (email == null || email.isBlank()) {
            throw new EntryRequestValidationException("Invalid e-mail");
        }
        String captchaToken = request.getCaptchaToken();
        if (!captchaService.verify(captchaToken)) {
            throw new CaptchaInvalidException("Failed captcha");
        }

        Optional<User> existingUser = userService.findByEmail(email);
        Participation participation = participationFor(competition, email, existingUser.orElse(null));
        boolean alreadyInCompetition = participation.getStatus() == ParticipationStatus.IN_COMPETITION;

        Long userId = existingUser.map(User::getId).orElse(null);
        Map<String, String> extra = Map.of(CompetitionLinkHandler.PARTICIPATION_ID_EXTRA_KEY, String.valueOf(participation.getId()));
        LinkCreationResult created = entryLink(new LinkPayload(userId, email, extra), existingUser.orElse(null));

        String name = existingUser.map(User::getName).orElse(null);
        emailSender.send(existingUser.filter(User::isRegistered).isPresent()
                ? CompetitionEmail.loginLink(userId, email, name, competition.getName(), RequestType.REQUEST, created.url())
                : CompetitionEmail.registrationLink(userId, email, competition.getName(), created.url()));

        if (alreadyInCompetition) {
            return;
        }
        if (participation.getFirstEmailSentDate() == null) {
            participation.setFirstEmailSentDate(LocalDate.now());
        }
        participation.setStatus(ParticipationStatus.EMAIL_SENT);
        participationRepository.save(participation);
        auditLogService.record(LogType.PARTICIPATION_STATUS_CHANGED, participation.getId(), existingUser.orElse(null),
                "Participation status changed to EMAIL_SENT");
    }

    /**
     * The e-mail's participation in the competition, created (EMAIL_NOT_SENT, REQUEST) when there
     * is none. Spec 05-057: inserted with ON CONFLICT DO NOTHING, so two simultaneous requests for
     * the same e-mail end up with the same row instead of one failing on the unique index.
     */
    /**
     * Spec 05-058: resends the entry link still valid for at least a day, or creates a new one.
     * Never invalidates: an older link keeps working until it expires.
     */
    private LinkCreationResult entryLink(LinkPayload payload, User user) {
        Optional<LinkCreationResult> active = linkService.findActive(CompetitionLinkHandler.KEY, payload);
        if (active.isPresent()) {
            linkService.markResent(active.get().id());
            auditLogService.record(LogType.LOGIN_LINK_ISSUED, active.get().id(), user,
                    "Entry request login link issued to " + payload.email() + " (resent)");
            return active.get();
        }
        LinkCreationResult created = linkService.create(CompetitionLinkHandler.KEY, payload);
        auditLogService.record(LogType.LOGIN_LINK_ISSUED, created.id(), user,
                "Entry request login link issued to " + payload.email());
        return created;
    }

    private Participation participationFor(Competition competition, String email, User user) {
        participationRepository.insertIfAbsent(competition.getId(), user != null ? user.getId() : null, email,
                ParticipationStatus.EMAIL_NOT_SENT.name(), RequestType.REQUEST.name());
        return participationRepository.findByCompetition_IdAndEmailIgnoreCase(competition.getId(), email)
                .orElseThrow();
    }

    private Competition findCompetition(Long competitionId) {
        return competitionRepository.findById(competitionId)
                .orElseThrow(() -> new CompetitionNotFoundException(competitionId));
    }

}
