package dev.leilaalgarve.jogoacoes.loginsession;

import dev.leilaalgarve.jogoacoes.email.EmailRequest;
import dev.leilaalgarve.jogoacoes.email.EmailSender;
import dev.leilaalgarve.jogoacoes.email.EmailTemplate;
import dev.leilaalgarve.jogoacoes.link.LinkCreationResult;
import dev.leilaalgarve.jogoacoes.link.LinkService;
import dev.leilaalgarve.jogoacoes.link.dto.LinkPayload;
import dev.leilaalgarve.jogoacoes.log.AuditLogService;
import dev.leilaalgarve.jogoacoes.log.LogType;
import dev.leilaalgarve.jogoacoes.user.User;
import dev.leilaalgarve.jogoacoes.user.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Issues a standalone login link and e-mails it (spec 05-056). One transaction around the whole
 * request, like the other flows that send e-mail: if the send fails, the previous links stay
 * valid, the new link is not stored and no {@code LOGIN_LINK_ISSUED} is recorded.
 */
@Service
public class LoginLinkRequestService {

    private final LinkService linkService;
    private final UserService userService;
    private final EmailSender emailSender;
    private final AuditLogService auditLogService;

    public LoginLinkRequestService(LinkService linkService, UserService userService, EmailSender emailSender,
                                   AuditLogService auditLogService) {
        this.linkService = linkService;
        this.userService = userService;
        this.emailSender = emailSender;
        this.auditLogService = auditLogService;
    }

    /** Does nothing for an unknown address, so the caller can't tell whether it is known. */
    @Transactional
    public void requestLoginLink(String email, String returnTo) {
        User user = userService.findByEmail(email).orElse(null);
        if (user == null) {
            return;
        }

        linkService.invalidateActiveLinksFor(user.getId());
        Map<String, String> extra = ReturnToValidator.validate(returnTo)
                .map(validReturnTo -> Map.of("returnTo", validReturnTo))
                .orElse(Map.of());
        LinkCreationResult created = linkService.create(LoginLinkHandler.KEY, new LinkPayload(user.getId(), email, extra));
        auditLogService.record(LogType.LOGIN_LINK_ISSUED, created.id(), user, "Login link issued to " + email);

        emailSender.send(new EmailRequest(user.getId(), email, user.getName(), null, null,
                created.url(), EmailTemplate.LOGIN_LINK));
    }
}
