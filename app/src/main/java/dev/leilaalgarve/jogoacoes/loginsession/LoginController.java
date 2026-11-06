package dev.leilaalgarve.jogoacoes.loginsession;

import dev.leilaalgarve.jogoacoes.api.LoginApi;
import dev.leilaalgarve.jogoacoes.api.model.CompleteRegistrationRequest;
import dev.leilaalgarve.jogoacoes.api.model.LoginResult;
import dev.leilaalgarve.jogoacoes.api.model.RequestLoginLinkRequest;
import dev.leilaalgarve.jogoacoes.link.LinkOutcome;
import dev.leilaalgarve.jogoacoes.link.LinkService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * `consumeLoginLink`/`completeRegistration` are thin adapters over the generic
 * {@link LinkService} (login is just one more {@code LinkHandler} it dispatches to);
 * `requestLoginLink` is a thin adapter over {@link LoginLinkRequestService}, which runs the whole
 * request in one transaction so a failed send changes nothing (spec 05-056).
 *
 * <p>The `Sec-CH-UA*` parameters both methods below accept (spec 05-009) are declared in the
 * contract purely so Swagger UI shows a fillable field for them -- neither method reads its own
 * parameter, since {@link LoginLinkSessionService} already resolves the device label straight
 * from the injected {@code HttpServletRequest}, the same way it already did for `User-Agent`.
 */
@RestController
public class LoginController implements LoginApi {

    private final LinkService linkService;
    private final LoginLinkRequestService loginLinkRequestService;

    public LoginController(LinkService linkService, LoginLinkRequestService loginLinkRequestService) {
        this.linkService = linkService;
        this.loginLinkRequestService = loginLinkRequestService;
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
        // 202 whether or not the address is known, so the answer doesn't reveal it.
        loginLinkRequestService.requestLoginLink(requestLoginLinkRequest.getEmail(),
                requestLoginLinkRequest.getReturnTo());
        return ResponseEntity.status(202).build();
    }

    private LoginResult toLoginResult(LinkOutcome outcome) {
        return new LoginResult().redirectTo(outcome.redirectData().get("redirectTo"));
    }
}
