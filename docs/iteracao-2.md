# Iteração 2 — Planejamento técnico

Este documento registra as decisões técnicas da Iteração 2 (ver objetivo geral em
[`roadmap.md`](roadmap.md)) antes da implementação, para servir de resumo caso a conversa
precise mudar de contexto. Nada aqui foi implementado ainda — é o plano acordado.

## Objetivo

Ter o projeto Spring Boot rodando com o modelo de dados do DER (`docs/diagrams/der.md`)
persistido em PostgreSQL, e o runner do Cucumber configurado para os `.feature` existentes
— sem regras de negócio, e-mail ou autenticação ainda.

## Stack e decisões técnicas

Versões verificadas contra o Maven Central nesta sessão (disponíveis e resolvíveis):

| Item | Decisão |
|---|---|
| Build | Maven (já disponível no ambiente) |
| Java | 21 |
| Spring Boot | 4.1.0 (`spring-boot-starter-parent`) |
| Cucumber | 7.34.6 (`cucumber-java`, `cucumber-junit-platform-engine`, `cucumber-spring`, via `cucumber-bom`) |
| Runner de teste | JUnit Platform Suite (`org.junit.platform:junit-platform-suite`) |
| Testes HTTP (API) | RestAssured 6.0.1 (`io.rest-assured:rest-assured`) |
| Migrations | Flyway — `flyway-core` + `flyway-database-postgresql` (a partir do Flyway 10 o suporte por banco é um módulo separado) |

**RestAssured** faz as chamadas HTTP reais aos endpoints REST do Spring Boot dentro dos
*steps* do Cucumber (`@SpringBootTest(webEnvironment = RANDOM_PORT)`), em vez de invocar
services/controllers diretamente — os cenários dos `.feature` exercitam a aplicação de fora
pra dentro, como um cliente real faria. Versão confirmada via `maven-metadata.xml` do
`repo1.maven.org` nesta sessão (última publicada, `lastUpdated` 2026-07-10).

**Três ambientes de teste, decidido:**

| Ambiente | Banco | Uso |
|---|---|---|
| Desenvolvimento | H2 em modo compatibilidade PostgreSQL (`MODE=PostgreSQL`), com `ddl-auto=create-drop` e Flyway desabilitado no perfil de teste | Ciclo rápido local e `mvn test` (inclusive dentro desta sessão sandboxed, ver nota abaixo) |
| Integração | Containers via Docker Compose, PostgreSQL real | Pegar incompatibilidades que o H2 esconderia, antes de ir pra homologação |
| Homologação | PostgreSQL, mesma stack de produção | Validação final antes do deploy |

**Por que H2 no ambiente de desenvolvimento em vez de Testcontainers/Postgres real aqui
nesta sessão:** testei — o daemon do Docker roda, mas o `docker pull` de imagens do Docker
Hub é bloqueado pela política de rede do ambiente (403 no CDN). Testcontainers, portanto,
não funciona dentro de uma sessão do Claude Code aqui — por isso o `mvn test` deste
esqueleto do Cucumber roda contra H2 neste sandbox. Nos ambientes reais (máquina de
desenvolvimento local, integração, homologação, produção) o Postgres via Docker Compose
continua sendo a "fonte da verdade".

**Nome de tabela do usuário:** `app_user`, não `user` — `user` é palavra reservada em SQL
padrão/PostgreSQL.

## Steps do Cucumber — consolidação decidida

Regra: só unificar steps que compartilham um prefixo/sufixo literal real (normalmente vindo
da mesma tabela `Examples`), nunca um padrão genérico demais que possa casar com um step de
outra intenção (causa erro de step ambíguo no Cucumber). Parâmetro usado: `{}` (anônimo, sem
aspas — os valores das `Examples` não têm aspas no `.feature`), tipado como `String` por
enquanto (sem `ParameterType` customizado — só compensaria quando os steps tiverem
implementação real, na Iteração 3).

| Step (Java) | Feature | Cobre |
|---|---|---|
| `the administrator decides to send the invite e-mails {word}` | create_competition | "now", "later" — as duas opções são a mesma ação (enviar os e-mails), só o "quando" varia; texto do `.feature` foi reescrito para isolar isso (era `the administrator chooses <option>` com a frase inteira como valor) |
| `the administrator chooses to {}` | manage_competition_players | "cancel the invite", "remove that player", "send or resend the e-mail to that player" |
| `the system rejects the competition creation and shows an error message about the {}` | create_competition | "missing name", "missing e-mail list", "invalid e-mail", "invalid start date", "invalid duration", "invalid brokerage fee" (6 textos → 1 step) |
| `the player's status is {}` | login | as 2 frases do outline de link expirado |
| `a player in the competition has status {} since {}` | manage_competition_players | as 4 combinações status/data |
| `the player list shows the player's status as {} along with the date {}` | manage_competition_players | idem |
| `the administrator filters the player list by {}` | manage_competition_players | idem |
| `only players with status {} are shown` | manage_competition_players | idem |
| `the player is {}` | request_competition_entry | "unregistered", "registered and not logged in", "registered and logged in" |
| `the user is {}` | login | "the system administrator", "a new player", "a registered player" — não inclui `"the user is logged into the system"` (Rule 2 do create_competition), que fica como step separado |

Steps novos de `login.feature` (dispositivo/sessão) — todos literais, sem repetição de
prefixo com outros steps, sem necessidade de unificação:
- `a registered player used the login link to log in on one device`
- `they are not logged in on this other device`
- `they click the same login link on this other device`
- `the player is already logged in on this device`
- `the login link was already used to log in on a different device`
- `a registered player has an active login link that has not been used yet`
- `they request a new login link`
- `the previous login link is invalidated`
- `only the new login link can be used to log in`
- `the player is already logged in on the maximum number of devices allowed by the system`
- `they log in successfully on one more device`
- `the system ends the oldest active session`
- `the player remains within the configured device limit`

**Não unificados (textos realmente diferentes, sem ganho possível):**
- `define the start date` vs. `define the start date as {}` (e o mesmo para `duration`,
  `buy brokerage fee`, `sell brokerage fee`) — a versão sem "as X" não tem valor pra
  parametrizar.
- `they click the "create" button` vs. `click the "create" button` — diferem pela palavra
  "they".
- `"create"` no botão e os dois status entre aspas fixos no texto (`"e-mail sent but link
  not clicked"`, `"e-mail not sent"`) — nunca variam, não compensa parametrizar.

## Pontos em aberto (decisão pendente)

1. ~~`the user is {}`~~ — **resolvido**: unificar `"the system administrator"` /
   `"a new player"` / `"a registered player"` num único step parametrizado
   `the user is {}`. O step de sessão (`"the user is logged into the system"`, Rule 2 do
   create_competition) fica **separado** — mistura identidade do usuário com estado de
   sessão, então não entra nessa unificação.
2. ~~Nome/redação do step de envio de convite~~ — **resolvido**: as duas opções da tabela
   `Examples` eram, na verdade, a mesma ação ("enviar os e-mails de convite") variando só o
   "quando". O `.feature` foi reescrito — a coluna `option` (valores `"send invites now"` /
   `"send invites later"`) virou `timing` (valores `now` / `later`), e o step passou a ser
   `the administrator decides to send the invite e-mails <timing>`.
3. ~~Campo para "link clicado"~~ — **resolvido**: em vez de um campo de conveniência em
   `Participation`, `PARTICIPATION.email_sent_date` virou `first_email_sent_date` (só o
   primeiro envio) e `LOGIN_LINK` ganhou `email_sent_at` (envio daquele link específico) e
   `invalidated_at` (invalidação antecipada — substituído por um link novo, ou usado no
   dispositivo errado). Ver `docs/diagrams/der.md`.
4. ~~Comportamento ao exceder o limite de dispositivos~~ — **resolvido, confirmado**:
   desloga a sessão mais antiga automaticamente (padrão tipo streaming) — a sessão mais
   antiga é invalidada silenciosamente e o novo login é permitido, sem erro exibido ao
   usuário. O número do limite continua fora do BDD (configurável, não fixo).

## Dados de teste

### Criação de competição: Object Mother + Test Data Builder

Para os cenários de dado inválido (`Scenario Outline: Administrator tries to create a
competition with invalid data`), o padrão combinado:

- Um **Object Mother** (`CompetitionMother`) expõe fábricas como
  `CompetitionMother.validPublicCompetition()` / `.validPrivateCompetition()`, cada uma
  retornando um **Test Data Builder** já pré-preenchido com dados válidos padrão (passo 1 —
  os steps `define the start date` / `define the duration` / etc. sem "as X" partem desse
  builder).
- Os steps parametrizados (`define the start date as {}`, `define the duration as {}`,
  etc.) pegam esse builder (guardado no contexto do cenário/World do Cucumber) e sobrescrevem
  **só o campo sob teste** com o valor inválido do `Examples` (passo 2) — o resto continua
  vindo do Mother. Isso espelha exatamente a estrutura do `.feature`: cada linha da tabela
  varia um campo por vez, mantendo os demais válidos.

### Participação em um status específico: fixture em cascata

Criar uma `Participation` já num status X (`Scenario Outline: Player list shows and can be
filtered by status`) é mais complicado do que preencher um campo `status` isolado, porque os
quatro status (`EMAIL_NOT_SENT` → `EMAIL_SENT` → `LINK_CLICKED` → `IN_COMPETITION`) formam
uma progressão: uma linha no status `IN_COMPETITION`, por exemplo, só é uma fixture realista
se também tiver a data em que o e-mail foi enviado e a data em que o link foi clicado — ou
seja, **os dados dos status anteriores precisam existir e ter suas próprias datas**, não só
o status final. Isso envolve mais de uma tabela: "link clicado" é `LoginLink.used_at`, não um
campo de `Participation` (decisão confirmada — ver ponto 3 acima).

Decisão de desenho: um Test Data Builder de `Participation` com quatro fábricas, uma por
status, cada uma construindo a cadeia completa até ali:

- `ParticipationMother.emailNotSent()`
- `ParticipationMother.emailSent(date)` — inclui o `firstEmailSentDate`
- `ParticipationMother.linkClicked(date)` — inclui `firstEmailSentDate` (com uma data
  anterior coerente) **e** cria/associa o `LoginLink` correspondente com `emailSentAt` e
  `usedAt` preenchidos
- `ParticipationMother.inCompetition(date)` — inclui tudo acima **e** `joinedAt`

### Sessões/dispositivos: fixture de `LoginSession`

Os novos cenários de dispositivo (`login.feature`) também precisam de fixtures compostas:
"jogador já logado em N dispositivos" = N linhas em `LOGIN_SESSION` (cada uma com seu
`LOGIN_LINK` de origem) associadas ao mesmo `user_id`, com `ended_at` nulo (sessão ativa). O
mesmo `ParticipationMother`/uma `LoginSessionMother` própria deve expor algo como
`LoginSessionMother.activeSessionsFor(user, count)`.
