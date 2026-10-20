package dev.leilaalgarve.jogoacoes.emailservice.send;

import dev.leilaalgarve.jogoacoes.emailservice.api.EmailsApi;
import dev.leilaalgarve.jogoacoes.emailservice.api.model.SendEmailRequest;
import dev.leilaalgarve.jogoacoes.emailservice.api.model.SendEmailResponse;
import dev.leilaalgarve.jogoacoes.emailservice.auth.ClientIdentityResolver;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class EmailSendController implements EmailsApi {

    private final ClientIdentityResolver clientIdentityResolver;
    private final EmailSendService emailSendService;

    public EmailSendController(ClientIdentityResolver clientIdentityResolver, EmailSendService emailSendService) {
        this.clientIdentityResolver = clientIdentityResolver;
        this.emailSendService = emailSendService;
    }

    /**
     * 202, not 200: the e-mail is recorded as PENDING, not delivered yet -- email-service publishes
     * it to the send queue in the background (spec 05-046).
     */
    @Override
    public ResponseEntity<SendEmailResponse> sendEmail(SendEmailRequest sendEmailRequest) {
        UUID id = emailSendService.send(clientIdentityResolver.currentClientId(), sendEmailRequest);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new SendEmailResponse(id, SendEmailResponse.StatusEnum.PENDING));
    }
}
