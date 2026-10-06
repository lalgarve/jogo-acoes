package dev.leilaalgarve.jogoacoes.emailservice.send;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * The fixed sender address of a client (spec 05-031): set by operations with
 * scripts/set-email-sender.sh, never through the API, so this service only reads it. Considered
 * valid as long as it is shaped like an e-mail address -- the script checks that; nobody checks the
 * identity on SES.
 */
@Entity
@Table(name = "client_sender")
public class ClientSender {

    @Id
    @Column(name = "client_id")
    private String clientId;

    @Column(nullable = false)
    private String address;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ClientSender() {
        // JPA
    }

    public ClientSender(String clientId, String address, Instant createdAt) {
        this.clientId = clientId;
        this.address = address;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public String getClientId() {
        return clientId;
    }

    public String getAddress() {
        return address;
    }
}
