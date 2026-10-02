# Módulos (pacotes) e endpoints da API — estado atual e depois da divisão

Documento de apoio à spec 05-027 (`spec.md`/`plan.md`): inventário de todos os pacotes de
`app/` e dos endpoints HTTP que cada um expõe hoje, seguido de como esses mesmos endpoints ficam
distribuídos depois da divisão de `login/`/`link/` em `user`, `loginsession` e `loginsecurity`
que a spec propõe. Levantado em 2026-10-02 contra `app/src/main/java/.../` e
`docs/openapi.yaml` (fonte de verdade dos endpoints — os controllers só implementam as
interfaces `*Api` geradas a partir dele, contract-first).

## 1. Pacotes de `app/` hoje e seu papel

| Pacote | Papel | Expõe endpoint HTTP? |
|---|---|---|
| `login/` | Usuário/papel (`User`/`Role`/`UserRole*`), link de login, sessão HTTP, segurança central (`SecurityConfig`) — tudo junto hoje, é exatamente o que a spec 05-027 separa | Sim |
| `link/` | Mecanismo genérico de link de uso único (`LinkRecord`/`LinkRouter`/`LinkHandler`) consumido por `login/` e `competition/`; também guarda `LoginSession`/`LoginSessionRepository`, que são sobre login especificamente, não sobre link genérico (achado do `plan.md` da 05-027) | Não (consumido pelos controllers de quem o usa) |
| `competition/` | Competições, participações, convites/pedidos de entrada, gestão de jogadores | Sim |
| `captcha/` | Verificação de CAPTCHA (ALTCHA real, ou aceitação total sob o profile `blackbox`) | Não (usado por `competition/` ao processar `EntryRequest`) |
| `email/` | Renderização e envio de e-mail (`SqsEmailSender`/`StubEmailSender`), registro do que foi enviado (`SentEmailRecorder`) | Não |
| `bootstrap/` | Bootstrap do primeiro administrador via `ADMIN_EMAIL` (spec 05-026) | Não (`ApplicationRunner`, roda no startup) |
| `log/` | Log de auditoria (`AuditLogService`) | Não |
| `common/` | Transversal: tratamento de exceção (`ApiExceptionHandler`), contribuição de segurança do próprio Swagger UI, aspectos de logging | Não diretamente (`ApiExceptionHandler` reage a exceções de qualquer controller, não tem rota própria) |

Só `login/` e `competition/` têm classes `@RestController` (confirmado via
`grep -rl "@RestController" app/src/main/java/`).

## 2. Endpoints por pacote, hoje

### `login/`

| Método | Caminho | Controller | Tag (openapi.yaml) |
|---|---|---|---|
| POST | `/login-requests` | `LoginController` | `login` |
| GET | `/login-links/{token}` | `LoginController` | `login` |
| POST | `/login-links/{token}/registration` | `LoginController` | `login` |
| GET | `/sessions` | `SessionsController` | `sessions` |
| DELETE | `/sessions/{sessionId}` | `SessionsController` | `sessions` |

### `competition/`

| Método | Caminho | Controller | Tag (openapi.yaml) |
|---|---|---|---|
| POST | `/competitions` | `CompetitionsController` | `competitions` |
| GET | `/competitions/public` | `CompetitionsController` | `competitions` |
| GET | `/competitions/mine` | `CompetitionsController` | `my-competitions` |
| GET | `/competitions/{competitionId}` | `CompetitionsController` | `my-competitions` |
| POST | `/competitions/{competitionId}/invite-emails` | `CompetitionsController` | `competitions` |
| POST | `/competitions/{competitionId}/entry-requests` | `EntryRequestsController` | `entry-requests` |
| GET | `/competitions/{competitionId}/players` | `PlayersController` | `players` |
| POST | `/competitions/{competitionId}/players` | `PlayersController` | `players` |
| PATCH | `/competitions/{competitionId}/players/{participationId}` | `PlayersController` | `players` |
| DELETE | `/competitions/{competitionId}/players/{participationId}` | `PlayersController` | `players` |
| POST | `/competitions/{competitionId}/players/{participationId}/invite-emails` | `PlayersController` | `players` |
| POST | `/competitions/{competitionId}/players/invite-emails` | `PlayersController` | `players` |

Nenhum endpoint em `link/`, `captcha/`, `email/`, `bootstrap/`, `log/` ou `common/` — são todos
consumidos internamente pelos controllers acima, nunca expõem rota própria.

## 3. Depois da divisão (spec 05-027)

`plan.md` já decide o destino de cada classe de `login/`/`link/`. Como só `LoginController` e
`SessionsController` são `@RestController`, e os dois migram pra `loginsession/`, **todos os 5
endpoints de `login/` continuam expostos pelo mesmo HTTP, só por classes que passam a morar em
`loginsession/`** — nenhum endpoint muda de caminho, método ou contrato, só de pacote Java por
trás. `user/` e `loginsecurity/` não ganham nenhum endpoint próprio (são suporte interno — dados
de usuário/papel, e configuração de segurança, respectivamente).

| Método | Caminho | Controller | Pacote depois da 05-027 |
|---|---|---|---|
| POST | `/login-requests` | `LoginController` | `loginsession/` |
| GET | `/login-links/{token}` | `LoginController` | `loginsession/` |
| POST | `/login-links/{token}/registration` | `LoginController` | `loginsession/` |
| GET | `/sessions` | `SessionsController` | `loginsession/` |
| DELETE | `/sessions/{sessionId}` | `SessionsController` | `loginsession/` |

`competition/` não muda de endpoint nem de pacote nesta spec — só os imports internos que hoje
apontam pra `login.User`/`login.UserRepository`/etc. passam a apontar pra `user.*` (lista
completa em `plan.md`, seção "Imports a atualizar fora de `login/`/`link/`").

## 4. Achado durante este levantamento (não estava em `plan.md`)

`login/UserProvisioningService.java` foi criado pela spec 05-026 (bootstrap do administrador),
implementada **depois** que o `plan.md` da 05-027 foi escrito — por isso não estava na lista
original de 19 classes que `plan.md` enumerava para `user/`/`loginsession/`/`loginsecurity/`.
Mesmo raciocínio que as outras classes de `User`/`Role`/`UserRole*`: não expõe endpoint, só lida
com criação de usuário/papel — destino natural é `user/`, junto do resto desse grupo.
**Atualizado em `plan.md`** (agora 20 classes, mais a nota sobre `bootstrap/AdministratorBootstrap.java`
precisar atualizar esse import também).
