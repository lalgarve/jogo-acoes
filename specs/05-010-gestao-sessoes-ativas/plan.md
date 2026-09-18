# Plan: Gestão de sessões ativas (listar e revogar)

Traduz `spec.md` em decisões técnicas. Valida contra `memory/constitution.md`.

## Contexto técnico

`LOGIN_SESSION` (módulo `link`, spec 05-002) registra cada sessão estabelecida por login mágico,
mas hoje é só bookkeeping pro limite de dispositivos (`LoginLinkSessionService.enforceDeviceLimit`)
— nada no `SecurityFilterChain` consulta `ended_at` a cada requisição; a autenticação de fato
mora na sessão HTTP (Spring Session, `spring.session.store-type: jdbc`, tabela própria do Spring
Session JDBC). Revogar uma `LOGIN_SESSION` sem tocar a sessão HTTP correspondente marcaria o
registro como encerrado sem nenhum efeito real — o dispositivo continuaria autenticado até o
cookie expirar.

## Decisões de arquitetura

| Pergunta | Decisão | Status | Raciocínio |
|---|---|---|---|
| Como fazer a revogação ter efeito real, não só cosmético | `LOGIN_SESSION` ganha uma coluna nova, `http_session_id` (String, nullable pra registros antigos), gravada em `establish()` a partir de `request.getSession().getId()` **depois** de `securityContextRepository.saveContext(...)` (que é o que garante que a sessão HTTP já existe/persistiu). Revogar chama `SessionRepository<S>` (bean já disponível via Spring Session JDBC) `.deleteById(httpSessionId)` — apaga a sessão de verdade; a próxima requisição daquele dispositivo chega sem `SecurityContext`, cai no 401 padrão. | resolvida | Reaproveita a infraestrutura de sessão já configurada (`spring.session.store-type: jdbc`) em vez de inventar um filtro novo que consulte `LOGIN_SESSION` a cada requisição (mais barato, sem I/O extra por request). |
| `alreadyAuthenticated` (mesmo dispositivo) também grava `http_session_id`? | Não precisa gravar de novo — a sessão HTTP já existe (é a mesma), e a `LOGIN_SESSION` já foi criada na primeira vez. | resolvida | Não há `LOGIN_SESSION` nova nesse caminho, então não há nada pra atualizar. |
| Onde vive o novo controller | `login/SessionsController.java`, implementando uma interface gerada nova (`SessionsApi`) — mesmo critério de `LoginController`: gestão de sessão é um assunto de `login/`, não de `link/` (que não sabe quem é o usuário autenticado). | resolvida | Consistente com onde `LoginLinkSessionService` (que também mexe em `LoginSession`) já vive. |
| "É a sessão atual?" (flag na listagem) | Compara `LOGIN_SESSION.http_session_id` da linha com `request.getSession().getId()` da requisição de listagem. | resolvida | Direto, sem mecanismo novo. |
| Nome dos endpoints | `GET /sessions`, `DELETE /sessions/{sessionId}` — recurso `sessions`, não `login-sessions`. | resolvida | Nome curto, já suficientemente claro no contexto do jogador autenticado. |

## Estrutura de módulos/pacotes

- `link/LoginSession.java` (modificado) — campo novo `httpSessionId`.
- Migration Flyway nova — coluna `http_session_id` em `login_session`.
- `login/SessionsController.java` (novo) — implementa `SessionsApi`.
- `login/SessionsService.java` (novo, ou lógica direto no controller se ficar pequena — decidir
  na implementação) — lista/revoga.
- `docs/openapi.yaml` (modificado) — `GET /sessions`, `DELETE /sessions/{sessionId}`, schema
  `Session` (id, deviceLabel, createdAt, current).
- `app/src/test/resources/features/manage_active_sessions.feature` (novo).

## Riscos e trade-offs

- **Sessões `LOGIN_SESSION` criadas antes desta spec não têm `http_session_id`** (coluna nova,
  nullable) — revogar uma sessão antiga dessas marca `ended_at` mas não consegue invalidar
  sessão HTTP nenhuma (não há o quê). Aceitável — são sessões de antes da feature existir; o
  efeito prático (bookkeeping) continua correto, só a garantia "de fato desloga" não se aplica a
  esse caso legado.
- **Depende indiretamente da spec 05-009** pro rótulo de dispositivo ser algo reconhecível — sem
  ela, a lista ainda funciona, só com rótulos menos úteis (User-Agent cru).
