package br.com.jogoacoes.email;

import br.com.jogoacoes.domain.EmailTemplate;

public interface EmailSender {

    /**
     * @param userId nullable — the recipient may not have an account yet (e.g. a first-time
     *               invite or entry request).
     */
    void send(Long userId, String email, String link, EmailTemplate template);
}
