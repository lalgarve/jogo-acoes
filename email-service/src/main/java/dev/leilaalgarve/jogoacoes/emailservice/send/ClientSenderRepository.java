package dev.leilaalgarve.jogoacoes.emailservice.send;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientSenderRepository extends JpaRepository<ClientSender, String> {
}
