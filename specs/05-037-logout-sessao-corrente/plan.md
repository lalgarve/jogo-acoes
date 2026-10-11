# Plan: Logout da sessão corrente

Traduz `spec.md` em decisões técnicas. Valida contra `memory/constitution.md`.

## Contexto técnico

`loginsession/SessionsService.revoke(sessionId, userId)` (spec 05-010) já faz tudo que o logout
precisa: apaga a sessão HTTP pelo `SessionRepository` do Spring Session JDBC e grava `ended_at`
na `LOGIN_SESSION`. `SessionsService.isCurrent(session)` já identifica a sessão da requisição
comparando `LOGIN_SESSION.http_session_id` com `request.getSession().getId()`. Nenhuma tabela
nova, nenhuma migration.

## Decisões de arquitetura

| Pergunta | Decisão | Status | Raciocínio |
|---|---|---|---|
| Nome do caminho | `DELETE /sessions/current` (`operationId: logoutCurrentSession`, tag `sessions`, `x-roles: [PLAYER, ADMINISTRATOR]`). | resolvida | O pedido original era `/sessions/this`. `current` acompanha a flag `current` que o schema `Session` já usa para a mesma ideia. Os dois nomes colidem com o template `/sessions/{sessionId}` (`int64`): o Spring resolve (caminho literal ganha do template), mas geradores e validadores de OpenAPI podem acusar ambiguidade — o literal precisa ficar declarado no contrato e coberto por teste para não virar `400` de conversão de tipo. |
| Como achar a sessão corrente | `LoginSessionRepository.findByHttpSessionIdAndEndedAtIsNull(request.getSession(false).getId())` (método derivado novo) e então o mesmo caminho de `revoke`. | resolvida | Uma consulta só, sem listar todas as sessões do usuário. Extrair de `revoke` um método privado `end(LoginSession)` usado pelos dois caminhos, para não duplicar a sequência "apagar sessão HTTP + gravar `ended_at`". |
| Requisição autenticada sem `LOGIN_SESSION` correspondente | Não acontece no fluxo normal (toda sessão autenticada nasce em `LoginLinkSessionService.establish`, que grava a linha). Se acontecer, invalidar a sessão HTTP mesmo assim e responder `204`. | resolvida | O objetivo do chamador é ficar deslogado; falhar com erro deixaria o dispositivo autenticado. Esse ramo precisa de teste (critério de aceite da constitution: todo ramo de erro exercitado). |
| Onde fica o código | `loginsession/SessionsController` (novo método da interface gerada `SessionsApi`) e `loginsession/SessionsService` (método `endCurrent()`). | resolvida | Mesmo lugar das outras operações de `/sessions`. Nenhuma regra nova em `SecurityConfigContributor`: `/sessions/**` já exige autenticação. |
| Proteção CSRF | Igual às demais operações autenticadas de escrita do projeto — nada específico. | resolvida | Não é o logout padrão do Spring Security (`LogoutFilter` em `POST /logout`); é uma operação de API comum. |

## Cenários Gherkin (texto a acrescentar em `manage_active_sessions.feature`)

```gherkin
  Scenario: Player logs out of the current device
    Given they also log in on a second device sending User-Agent Client Hints for "Windows" and browser "Chromium"
    When they log out of the current session
    Then they are logged out of the current device
    And the second device's session still works for authenticated requests

  Scenario: Logged-out session no longer appears in the active sessions list
    Given they also log in on a second device sending User-Agent Client Hints for "Windows" and browser "Chromium"
    When they log out of the current session
    And they list their active sessions from the second device
    Then the system shows one session, marked as the current one

  Scenario: Anonymous user tries to log out
    Given the user is not logged in
    When they try to log out of the current session
    Then the system rejects the request as not logged in
```

O último cenário não usa o `Background` da feature (que loga o usuário) — ou vai para um
arquivo próprio, ou o `Background` é dividido em `Rule`s. Decidir ao escrever o `.feature`,
seguindo o que os outros arquivos já fazem.

## Estrutura de módulos/pacotes

- `docs/openapi.yaml` (modificado) — `DELETE /sessions/current`, logo depois de `/sessions` na
  ordem do arquivo.
- `loginsession/LoginSessionRepository.java` (modificado) — `findByHttpSessionIdAndEndedAtIsNull`.
- `loginsession/SessionsService.java` (modificado) — `endCurrent()`; `revoke` passa a reaproveitar
  o mesmo passo de encerramento.
- `loginsession/SessionsController.java` (modificado) — `logoutCurrentSession()`.
- `app/src/test/resources/features/manage_active_sessions.feature` (modificado).
- Teste para o ramo "sessão autenticada sem `LOGIN_SESSION`".

## Riscos e trade-offs

- **Colisão `/sessions/current` × `/sessions/{sessionId}`.** Mitigação: o cenário de logout já
  exercita o caminho literal; um teste extra de `DELETE /sessions/123` continuando a funcionar
  garante que a ordem de resolução não mudou.
