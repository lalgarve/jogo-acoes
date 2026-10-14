package dev.leilaalgarve.jogoacoes.emailservice.disposable;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DisposableDomainSourceRepository extends JpaRepository<DisposableDomainSource, Long> {

    boolean existsByUrl(String url);
}
