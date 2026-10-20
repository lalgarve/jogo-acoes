package dev.leilaalgarve.jogoacoes.emailservice.send;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * One accepted send request (spec 05-031). Its {@link #id} is the queue message's
 * {@code correlationId} and the SES message tag, so later SES events can be tied back to it.
 *
 * <p>Spec 05-046: also the work item of the background publisher -- status, attempts, last error,
 * next attempt. {@link #templateData} (may carry personal data) is kept only while PENDING.
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EmailSendStatus status;

    @Column(name = "sender_address", nullable = false)
    private String senderAddress;

    @Column(name = "ses_template_name", nullable = false)
    private String sesTemplateName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "template_data")
    private String templateData;

    @Column(nullable = false)
    private int attempts;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_error_type")
    private DeliveryErrorType lastErrorType;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "queued_at")
    private Instant queuedAt;

    protected EmailSend() {
        // JPA
    }

    /** A new PENDING send, due for publishing right away. */
    public EmailSend(String clientId, Long templateId, String recipientEmail, String senderAddress,
                     String sesTemplateName, String templateData, Instant createdAt) {
        this.clientId = clientId;
        this.templateId = templateId;
        this.recipientEmail = recipientEmail;
        this.senderAddress = senderAddress;
        this.sesTemplateName = sesTemplateName;
        this.templateData = templateData;
        this.createdAt = createdAt;
        this.status = EmailSendStatus.PENDING;
        this.nextAttemptAt = createdAt;
    }

    void markQueued(Instant now) {
        status = EmailSendStatus.QUEUED;
        queuedAt = now;
        templateData = null;
    }

    void recordFailure(DeliveryErrorType type, String message) {
        attempts++;
        lastErrorType = type;
        lastError = message;
    }

    /** Back to PENDING, to be published again at {@code at} with {@code templateData}. */
    void retryAt(Instant at, String templateData) {
        status = EmailSendStatus.PENDING;
        nextAttemptAt = at;
        this.templateData = templateData;
    }

    void fail() {
        status = EmailSendStatus.FAILED;
        templateData = null;
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

    public EmailSendStatus getStatus() {
        return status;
    }

    public String getSenderAddress() {
        return senderAddress;
    }

    public String getSesTemplateName() {
        return sesTemplateName;
    }

    public String getTemplateData() {
        return templateData;
    }

    public int getAttempts() {
        return attempts;
    }

    public DeliveryErrorType getLastErrorType() {
        return lastErrorType;
    }
}
