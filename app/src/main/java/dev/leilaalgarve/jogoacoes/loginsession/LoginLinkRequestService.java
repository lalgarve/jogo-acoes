package dev.leilaalgarve.jogoacoes.loginsession;

import dev.leilaalgarve.jogoacoes.email.EmailSender;
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
 *
 * <p>Spec 05-058: a request with the same {@code returnTo} while an unused link is still valid for
 * at least a day resends that link; only a new link invalidates the previous ones.
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

        Map<String, String> extra = ReturnToValidator.validate(returnTo)
                .map(validReturnTo -> Map.of("returnTo", validReturnTo))
                .orElse(Map.of());
        LinkPayload payload = new LinkPayload(user.getId(), email, extra);
        LinkCreationResult link = linkService.findActive(LoginLinkHandler.KEY, payload)
                .map(active -> {
                    linkService.markResent(active.id());
                    auditLogService.record(LogType.LOGIN_LINK_ISSUED, active.id(), user, "Login link issued to " + email + " (resent)");
                    return active;
                })
                .orElseGet(() -> {
                    linkService.invalidateActiveLinksFor(user.getId());
                    LinkCreationResult created = linkService.create(LoginLinkHandler.KEY, payload);
                    auditLogService.record(LogType.LOGIN_LINK_ISSUED, created.id(), user, "Login link issued to " + email);
                    return created;
                });

        emailSender.send(new LoginLinkEmail(user.getId(), email, user.getName(), link.url()));
    }
}
