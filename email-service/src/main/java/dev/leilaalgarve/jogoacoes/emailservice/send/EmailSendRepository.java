package dev.leilaalgarve.jogoacoes.emailservice.send;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EmailSendRepository extends JpaRepository<EmailSend, UUID> {
}
