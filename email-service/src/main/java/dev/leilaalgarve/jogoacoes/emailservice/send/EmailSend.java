package dev.leilaalgarve.jogoacoes.emailservice.send;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * One accepted send request (spec 05-031). Its {@link #id} is the queue message's
 * {@code correlationId} and the SES message tag, so later SES events can be tied back to it.
 * Never holds the template data: it may carry personal data.
 */
@Entity
@Table(name = "email_send")
public class EmailSend {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "client_id", nullable = false)
    private String clientId;

    @Column(name = "template_id", nullable = false)
    private Long templateId;

    @Column(name = "recipient_email", nullable = false)
    private String recipientEmail;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected EmailSend() {
        // JPA
    }

    public EmailSend(String clientId, Long templateId, String recipientEmail, Instant createdAt) {
        this.clientId = clientId;
        this.templateId = templateId;
        this.recipientEmail = recipientEmail;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getClientId() {
        return clientId;
    }

    public Long getTemplateId() {
        return templateId;
    }

    public String getRecipientEmail() {
        return recipientEmail;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
