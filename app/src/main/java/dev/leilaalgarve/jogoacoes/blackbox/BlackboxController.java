package dev.leilaalgarve.jogoacoes.blackbox;

import dev.leilaalgarve.jogoacoes.email.SentEmail;
import dev.leilaalgarve.jogoacoes.email.SentEmailRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * Spec 05-014 -- the only way, outside the Java process, to read the link of an e-mail sent
 * during a blackbox test: {@code POST /login-requests} always returns {@code 202} with no
 * body (deliberately, so it never reveals whether an e-mail exists), and the link otherwise
 * only lives in {@code sent_email}, readable today only from inside the process (Cucumber).
 *
 * Not a real mailbox -- no history, no multiple e-mails, no read/unread state, just the most
 * recent link sent to an address. Deliberately not part of {@code docs/openapi.yaml}: this is
 * test scaffolding, not a product contract (see plan.md) -- the Python suite (spec 05-015)
 * calls it directly via HTTP, outside the client generated from the contract.
 *
 * Unauthenticated, and by nature an information disclosure that would be unacceptable in
 * production -- acceptable here only because it's structurally confined to the {@code
 * blackbox} profile (never {@code sandbox}/{@code staging}/{@code production}), the same
 * guarantee applied to {@link BlackboxDataSeeder} and
 * {@link dev.leilaalgarve.jogoacoes.captcha.AlwaysPassCaptchaVerifier}.
 */
@RestController
@Profile("blackbox")
public class BlackboxController {

    private final SentEmailRepository sentEmailRepository;

    public BlackboxController(SentEmailRepository sentEmailRepository) {
        this.sentEmailRepository = sentEmailRepository;
    }

    @GetMapping("/blackbox/last-email")
    public ResponseEntity<LastEmailResponse> lastEmail(@RequestParam String email) {
        return sentEmailRepository.findTopByEmailOrderBySentAtDesc(email)
                .map(BlackboxController::toResponse)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private static LastEmailResponse toResponse(SentEmail sentEmail) {
        return new LastEmailResponse(sentEmail.getLink(), sentEmail.getTemplate().name(), sentEmail.getSentAt());
    }

    public record LastEmailResponse(String link, String template, LocalDateTime sentAt) {
    }
}
