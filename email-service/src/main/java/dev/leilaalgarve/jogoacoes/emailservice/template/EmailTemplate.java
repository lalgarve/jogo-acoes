package dev.leilaalgarve.jogoacoes.emailservice.template;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * A client's registered e-mail template, mirrored on Amazon SES under {@link #sesTemplateName}
 * (see data-model.md). {@code clientId} is the client name the registering API key was issued
 * for (spec 05-030), resolved by {@code auth.ClientIdentityResolver}.
 */
@Entity
@Table(name = "email_template")
public class EmailTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id", nullable = false)
    private String clientId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false)
    private String body;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "variables_schema")
    private String variablesSchema;

    @Column(name = "ses_template_name", nullable = false)
    private String sesTemplateName;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected EmailTemplate() {
        // JPA
    }

    public EmailTemplate(String clientId, String name, String subject, String body, String variablesSchema,
                          String sesTemplateName, Instant createdAt) {
        this.clientId = clientId;
        this.name = name;
        this.subject = subject;
        this.body = body;
        this.variablesSchema = variablesSchema;
        this.sesTemplateName = sesTemplateName;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public void update(String subject, String body, String variablesSchema, Instant updatedAt) {
        this.subject = subject;
        this.body = body;
        this.variablesSchema = variablesSchema;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public String getClientId() {
        return clientId;
    }

    public String getName() {
        return name;
    }

    public String getSubject() {
        return subject;
    }

    public String getBody() {
        return body;
    }

    public String getVariablesSchema() {
        return variablesSchema;
    }

    public String getSesTemplateName() {
        return sesTemplateName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
