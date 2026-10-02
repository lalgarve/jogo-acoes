package dev.leilaalgarve.jogoacoes.emailservice.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

/**
 * Esqueleto (spec 05-025): o valor bruto do header {@code X-API-Key} -- já garantido não-vazio
 * por {@link ApiKeyAuthenticationFilter} antes de qualquer controller rodar -- dobra como
 * identificador do cliente dono dos templates. Único ponto a trocar quando a validação real de
 * API-KEY existir (resolvendo o cliente de verdade a partir de uma tabela/biblioteca), sem mudar
 * o contrato HTTP dos endpoints de template (plan.md).
 */
@Component
@RequestScope
public class ClientIdentityResolver {

    private final HttpServletRequest request;

    public ClientIdentityResolver(HttpServletRequest request) {
        this.request = request;
    }

    public String currentClientId() {
        return request.getHeader(ApiKeyAuthenticationFilter.API_KEY_HEADER);
    }
}
