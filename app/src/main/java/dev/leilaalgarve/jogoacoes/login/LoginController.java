package dev.leilaalgarve.jogoacoes.login;

import dev.leilaalgarve.jogoacoes.api.LoginApi;
import dev.leilaalgarve.jogoacoes.api.model.CompleteRegistrationRequest;
import dev.leilaalgarve.jogoacoes.api.model.LoginResult;
import dev.leilaalgarve.jogoacoes.api.model.RequestLoginLinkRequest;
import dev.leilaalgarve.jogoacoes.email.EmailRequest;
import dev.leilaalgarve.jogoacoes.email.EmailSender;
import dev.leilaalgarve.jogoacoes.email.EmailTemplate;
import dev.leilaalgarve.jogoacoes.link.LinkCreationResult;
import dev.leilaalgarve.jogoacoes.link.LinkOutcome;
import dev.leilaalgarve.jogoacoes.link.LinkService;
import dev.leilaalgarve.jogoacoes.link.dto.LinkPayload;
import dev.leilaalgarve.jogoacoes.log.AuditLogService;
import dev.leilaalgarve.jogoacoes.log.LogType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * `consumeLoginLink`/`completeRegistration` are thin adapters over the generic
 * {@link LinkService} (login is just one more {@code LinkHandler} it dispatches to);
 * `requestLoginLink` stays here because creating a standalone login link is entirely a `login`
 * concern (nothing generic to dispatch) -- see plan.md's revision of this controller's home.
 *
 * <p>The `Sec-CH-UA*` parameters both methods below accept (spec 05-009) are declared in the
 * contract purely so Swagger UI shows a fillable field for them -- neither method reads its own
 * parameter, since {@link LoginLinkSessionService} already resolves the device label straight
 * from the injected {@code HttpServletRequest}, the same way it already did for `User-Agent`.
 */
@RestController
public class LoginController implements LoginApi {

    private final LinkService linkService;
    private final UserRepository userRepository;
    private final EmailSender emailSender;
    private final AuditLogService auditLogService;

    public LoginController(LinkService linkService, UserRepository userRepository, EmailSender emailSender,
                            AuditLogService auditLogService) {
        this.linkService = linkService;
        this.userRepository = userRepository;
        this.emailSender = emailSender;
        this.auditLogService = auditLogService;
    }

    @Override
    public ResponseEntity<LoginResult> consumeLoginLink(String token, String secCHUA, String secCHUAPlatform,
                                                          String secCHUAPlatformVersion, String secCHUAMobile) {
        LinkOutcome outcome = linkService.consume(token);
        if (outcome.isPending()) {
            return ResponseEntity.status(202).build();
        }
        return ResponseEntity.ok(toLoginResult(outcome));
    }

    @Override
    public ResponseEntity<LoginResult> completeRegistration(String token, CompleteRegistrationRequest completeRegistrationRequest,
                                                              String secCHUA, String secCHUAPlatform,
                                                              String secCHUAPlatformVersion, String secCHUAMobile) {
        LinkOutcome outcome = linkService.complete(token, Map.of("name", completeRegistrationRequest.getName()));
        return ResponseEntity.ok(toLoginResult(outcome));
    }

    @Override
    public ResponseEntity<Void> requestLoginLink(RequestLoginLinkRequest requestLoginLinkRequest) {
        String email = requestLoginLinkRequest.getEmail();
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return ResponseEntity.status(202).build(); // Don't reveal whether the address is known.
        }

        linkService.invalidateActiveLinksFor(user.getId());
        Map<String, String> extra = ReturnToValidator.validate(requestLoginLinkRequest.getReturnTo())
                .map(returnTo -> Map.of("returnTo", returnTo))
                .orElse(Map.of());
        LinkCreationResult created = linkService.create(LoginLinkHandler.KEY, new LinkPayload(user.getId(), email, extra));
        auditLogService.record(LogType.LOGIN_LINK_ISSUED, created.id(), user, "Login link issued to " + email);

        emailSender.send(new EmailRequest(user.getId(), email, user.getName(), null, null,
                "/login-links/" + created.token(), EmailTemplate.LOGIN_LINK));
        return ResponseEntity.status(202).build();
    }

    private LoginResult toLoginResult(LinkOutcome outcome) {
        return new LoginResult().redirectTo(outcome.redirectData().get("redirectTo"));
    }
}
