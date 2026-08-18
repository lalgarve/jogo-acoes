package io.deployo.jogoacoes.service;

import io.deployo.jogoacoes.api.model.EntryRequest;
import io.deployo.jogoacoes.captcha.CaptchaService;
import io.deployo.jogoacoes.domain.Competition;
import io.deployo.jogoacoes.domain.CompetitionType;
import io.deployo.jogoacoes.domain.EmailTemplate;
import io.deployo.jogoacoes.domain.LoginLink;
import io.deployo.jogoacoes.domain.Participation;
import io.deployo.jogoacoes.domain.ParticipationStatus;
import io.deployo.jogoacoes.domain.RequestType;
import io.deployo.jogoacoes.domain.User;
import io.deployo.jogoacoes.email.EmailSender;
import io.deployo.jogoacoes.repository.CompetitionRepository;
import io.deployo.jogoacoes.repository.LoginLinkRepository;
import io.deployo.jogoacoes.repository.ParticipationRepository;
import io.deployo.jogoacoes.repository.UserRepository;
import io.deployo.jogoacoes.web.CaptchaInvalidException;
import io.deployo.jogoacoes.web.CompetitionNotFoundException;
import io.deployo.jogoacoes.web.EntryRequestValidationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class EntryRequestService {

    private static final int LINK_VALIDITY_DAYS = 7;

    private final CompetitionRepository competitionRepository;
    private final ParticipationRepository participationRepository;
    private final LoginLinkRepository loginLinkRepository;
    private final UserRepository userRepository;
    private final EmailSender emailSender;
    private final CaptchaService captchaService;

    public EntryRequestService(CompetitionRepository competitionRepository, ParticipationRepository participationRepository,
                                LoginLinkRepository loginLinkRepository, UserRepository userRepository,
                                EmailSender emailSender, CaptchaService captchaService) {
        this.competitionRepository = competitionRepository;
        this.participationRepository = participationRepository;
        this.loginLinkRepository = loginLinkRepository;
        this.userRepository = userRepository;
        this.emailSender = emailSender;
        this.captchaService = captchaService;
    }

    /** Logged-in player confirming entry directly from their session -- no e-mail round trip. */
    @Transactional
    public Participation confirmEntry(Long competitionId) {
        Competition competition = findCompetition(competitionId);
        User user = currentUser();

        Participation participation = participationRepository.findByCompetition_IdAndUser_Id(competitionId, user.getId())
                .orElse(null);

        if (competition.getType() == CompetitionType.PRIVATE && participation == null) {
            // No invite on record -- 404 rather than 403, so as not to reveal the competition exists.
            throw new CompetitionNotFoundException(competitionId);
        }

        if (participation == null) {
            participation = new Participation();
            participation.setCompetition(competition);
            participation.setUser(user);
            participation.setEmail(user.getEmail());
            participation.setRequestType(RequestType.REQUEST);
        }
        participation.setStatus(ParticipationStatus.IN_COMPETITION);
        participation.setJoinedAt(LocalDate.now());
        return participationRepository.save(participation);
    }

    /** Unregistered or logged-out player: e-mail + captcha, gets a link instead of immediate entry. */
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

        Optional<User> existingUser = userRepository.findByEmail(email);
        EmailTemplate template = existingUser.filter(User::isRegistered).isPresent()
                ? EmailTemplate.LOGIN_LINK
                : EmailTemplate.REGISTRATION_LINK;

        Participation participation = participationRepository
                .findByCompetition_IdAndEmailAndStatusNot(competitionId, email, ParticipationStatus.IN_COMPETITION)
                .orElseGet(() -> {
                    Participation p = new Participation();
                    p.setCompetition(competition);
                    p.setEmail(email);
                    p.setUser(existingUser.orElse(null));
                    p.setStatus(ParticipationStatus.EMAIL_NOT_SENT);
                    p.setRequestType(RequestType.REQUEST);
                    return p;
                });
        participation = participationRepository.save(participation);

        String token = UUID.randomUUID().toString();
        LoginLink link = new LoginLink();
        link.setToken(token);
        link.setEmail(email);
        link.setUser(existingUser.orElse(null));
        link.setParticipation(participation);
        link.setEmailSentAt(LocalDateTime.now());
        link.setExpiresAt(LocalDateTime.now().plusDays(LINK_VALIDITY_DAYS));
        loginLinkRepository.save(link);

        Long userId = existingUser.map(User::getId).orElse(null);
        emailSender.send(userId, email, "/login-links/" + token, template);

        if (participation.getFirstEmailSentDate() == null) {
            participation.setFirstEmailSentDate(LocalDate.now());
        }
        participation.setStatus(ParticipationStatus.EMAIL_SENT);
        participationRepository.save(participation);
    }

    private Competition findCompetition(Long competitionId) {
        return competitionRepository.findById(competitionId)
                .orElseThrow(() -> new CompetitionNotFoundException(competitionId));
    }

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + email));
    }
}
