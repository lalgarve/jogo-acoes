# Iteração 3 — Planejamento técnico

Este documento registra as decisões técnicas da Iteração 3 (ver objetivo geral em
[`roadmap.md`](roadmap.md)) antes da implementação, no mesmo espírito do
[`iteracao-2.md`](iteracao-2.md): um resumo pra servir de base caso a conversa precise
mudar de contexto. Nada aqui foi implementado ainda — é o plano em discussão.

## Objetivo

Implementar as regras de negócio dos quatro `.feature` já especificados
(`create_competition`, `login`, `manage_competition_players`, `request_competition_entry`),
com envio de e-mail representado por um stub (log), sobre o esqueleto de persistência já
pronto da Iteração 2. Entregável: todos os cenários dos `.feature` passando de verdade
(não mais `Pending`).

**Nota sobre escopo vs. `roadmap.md`:** o texto original da Iteração 3 no roadmap não
menciona os cenários de dispositivo/sessão de `login.feature` (limite de dispositivos,
invalidação de link) — eles foram adicionados depois, junto com `LOGIN_SESSION` no DER,
já durante a Iteração 2. Como já estão especificados no `.feature` e modelados no banco,
tratamos como parte do escopo desta iteração também.

## O que já existe (herdado da Iteração 2)

- Sete entidades JPA + repositórios (`io.deployo.jogoacoes.domain`/`repository`): `User`,
  `Role`, `UserRole`, `Competition`, `Participation`, `LoginLink`, `LoginSession`, `Log`.
- Migrations Flyway (schema completo, incluindo a tabela `log`).
- Runner do Cucumber (`RunCucumberTest`) e um método Java "pending" por texto de step único
  dos quatro `.feature` (`src/test/java/io/deployo/jogoacoes/steps/`).
- Dependências já no `pom.xml`: `spring-boot-starter-web`, `-validation`, RestAssured
  (testes HTTP), `spring-boot-data-jpa-test` (`@DataJpaTest`, já usado em
  `LogRepositoryTest`).

O que falta e esta iteração precisa criar: camada de serviço, camada HTTP (não existe
nenhum `@RestController` ainda), forma de manter "usuário logado" entre requisições, stub
de e-mail, stub de captcha, dados de teste (Object Mother/Test Data Builder já desenhados
em `iteracao-2.md`), e a implementação de verdade dos steps (hoje todos são
`throw new PendingException()`).

## Escopo por `.feature`

### `create_competition.feature`

- Criação de competição pública/privada com todas as validações já especificadas: nome
  obrigatório, data futura, duração > 0, taxas não-negativas, lista de e-mails válida.
- Controle de acesso: só quem tem `USER_ROLE` = administrador pode criar (Rule "Only the
  administrator can create competitions").
- Envio de convites (`timing` now/later) via o stub de e-mail.

### `request_competition_entry.feature`

- Pedido de entrada em competição pública para jogador novo, registrado deslogado e
  registrado logado.
- Negação de pedido em competição privada sem convite.
- Validação de e-mail e captcha (stub — ver "Decisões a tomar").
- Pedido duplicado reenvia o link em vez de duplicar o registro.

### `login.feature`

- Geração/validação de `LOGIN_LINK` (token, expiração, uso único) e os fluxos de
  confirmação de entrada / redirecionamento.
- Link inválido/expirado.
- Cenários de dispositivo: um link só loga no dispositivo que o usou primeiro; gerar um
  link novo invalida o anterior; exceder o limite de dispositivos encerra a sessão mais
  antiga (`LOGIN_SESSION`).

### `manage_competition_players.feature`

- Filtro da lista de jogadores por status.
- Reenvio de e-mail (individual e em grupo), edição de e-mail (validação de formato e
  duplicidade), remoção de jogador, cancelamento de convite pendente.
- Convite de novos jogadores a uma competição privada já criada.

## Decisões a tomar antes de implementar

Diferente da Iteração 2 (onde as decisões técnicas foram resolvidas antes de codificar),
aqui há mais perguntas de arquitetura em aberto — a lista abaixo é o que precisa de decisão
consciente antes (ou logo no início) da implementação:

1. ~~Camada HTTP~~ — **resolvido: API-first com OpenAPI.** Mesmo espírito do projeto até
   aqui (Gherkin antes do código, DER antes das entidades): as rotas estão desenhadas em
   [`docs/openapi.yaml`](openapi.yaml) antes de qualquer `@RestController` existir,
   cobrindo as ações dos quatro `.feature` (`POST /competitions`,
   `POST /competitions/{id}/entry-requests`, `GET /login-links/{token}`, endpoints de
   `players`, etc.) — os *steps* do Cucumber (RestAssured,
   `@SpringBootTest(webEnvironment = RANDOM_PORT)` já configurado desde a Iteração 2) devem
   seguir esse contrato. Validado com `openapi-spec-validator` (`docs/openapi.yaml: OK`).
   Falta decidir se haverá geração de código (client/interfaces de controller) a partir do
   arquivo, ou se ele fica só como contrato de referência para os controllers escritos à
   mão — ver próximo ponto.

2. ~~Geração de código a partir do OpenAPI ou não~~ — **resolvido: sim, com
   `openapi-generator-maven-plugin`** (versão 7.24.0, verificada contra o Maven Central
   nesta sessão). Configurado no `pom.xml`, gerador `spring`, `interfaceOnly=true`
   (só interfaces + DTOs — a implementação continua sendo escrita à mão),
   `useTags=true` (uma interface por *tag* do YAML: `CompetitionsApi`, `EntryRequestsApi`,
   `LoginApi`, `PlayersApi`), rodando na fase `generate-sources` — ou seja, todo `mvn
   compile` regenera a partir de `docs/openapi.yaml`, e a implementação para de compilar
   se ficar desalinhada do contrato.

   **Testado nesta sessão, `mvn compile`/`mvn test` passam** com a stack atual (Spring
   Boot 4.1, Java 21). Duas coisas precisaram de ajuste que não eram óbvias de antemão:
   - Faltava `operationId` explícito em cada rota do YAML — sem isso o gerador inventa
     nomes de método a partir do path (`login_requestsPost`, feio e frágil a mudanças de
     rota). Adicionado um `operationId` por operação.
   - O código gerado pelo template `spring` depende de duas bibliotecas que não estavam
     no projeto: `io.swagger.core.v3:swagger-annotations` (2.2.53) e
     `org.openapitools:jackson-databind-nullable` (0.2.11) — adicionadas ao `pom.xml`.
3. ~~Sessão/"usuário logado"~~ — **resolvido: opção (c), Spring Security.**
   `spring-boot-starter-security` entra no `pom.xml` agora. Como não há senha (login é só
   por `LOGIN_LINK`), não tem `UserDetailsService` com credencial pra validar — o fluxo é:
   ao validar o `LOGIN_LINK` (token válido, não expirado, não usado/invalidado, dispositivo
   correto), o *controller*/*service* monta a `Authentication` (`GrantedAuthority` por
   `USER_ROLE` do usuário, ex. `ROLE_ADMINISTRATOR`, `ROLE_PLAYER`) e coloca no
   `SecurityContextHolder` — o `SecurityContextHolderFilter` do Spring Security já
   persiste isso na `HttpSession` (cookie `JSESSIONID`) automaticamente, sem código extra
   de sessão. Nos *steps* do Cucumber, o RestAssured mantém a sessão entre chamadas de um
   mesmo cenário via `io.restassured.filter.session.SessionFilter` (guardado no World do
   *step*, reaproveitado entre `Given`/`When`/`Then`).

   **Achado favorável:** o Spring Security tem controle de sessões concorrentes pronto
   (`SessionManagementConfigurer.maximumSessions(n)` + `SessionRegistry`,
   `maxSessionsPreventsLogin(false)` pra expulsar a mais antiga em vez de bloquear o
   login novo) — é exatamente a regra de negócio de "limite de dispositivos" de
   `login.feature`.

   **Sub-decisão `LOGIN_SESSION` vs. `SessionRegistry` — resolvida.** O ponto de partida
   ("`SessionRegistry` em memória serve por enquanto, só importa se escalar horizontalmente")
   não se sustentou: **mesmo com uma única instância**, tanto a `HttpSession` quanto o
   `SessionRegistry` padrão vivem só na memória do processo — um restart da aplicação
   desloga todo mundo, o que contraria um requisito direto do produto (jogador continua
   logado por dias, sem precisar pedir link de novo a cada reinício/deploy). Resolvido com
   **Spring Session** (`spring-session-jdbc` 4.1.0), que troca o armazenamento da
   `HttpSession` (com a `Authentication` dentro dela) por um apoiado no PostgreSQL — a
   sessão sobrevive a restart porque está no banco, não na memória. De bônus, o Spring
   Session já integra `SpringSessionBackedSessionRegistry`, então o controle de sessões
   concorrentes citado acima também fica persistido, sem precisar de `SessionRegistry`
   customizado nem de uma implementação própria apoiada em `LOGIN_SESSION`. Timeout de
   sessão configurado em 30 dias (`spring.session.timeout`) — ajustável, é só um ponto de
   partida razoável para "logado por dias", não uma decisão de produto fechada.

   `LOGIN_SESSION` (nossa tabela) continua existindo, mas como registro de domínio (qual
   `LOGIN_LINK` originou a sessão, nome do dispositivo pra mostrar pro jogador) — a
   persistência técnica da sessão em si é responsabilidade do Spring Session agora.

   **Testado nesta sessão:**
   - `spring-session-jdbc` sozinho não ativa nada (mesmo padrão do Flyway/`@DataJpaTest`
     no Boot 4.x): faltavam os módulos de autoconfiguração
     `org.springframework.boot:spring-boot-session` e `spring-boot-session-jdbc`
     (ambos 4.1.0) — sem eles, nenhum bean `SessionRepository` existe no contexto.
   - `V3__add_spring_session_schema.sql` (schema oficial `schema-postgresql.sql`, extraído
     do próprio jar do `spring-session-jdbc`) aplicado de verdade contra H2 nesta sessão,
     junto com V1 e V2 (`Successfully applied 3 migrations`), sem conflito com a
     auto-inicialização de schema do Spring Session (que só roda se as tabelas ainda não
     existirem).
   - `SpringSessionSmokeTest`: cria uma sessão, salva, confirma a linha **direto no banco
     via `JdbcTemplate`** (não só via o mesmo objeto em memória), recarrega por um
     `findById` novo e confere o atributo — 1/1 passando, prova a persistência de ponta a
     ponta.
   - `org.json:json` (usado pelo ALTCHA) colidia com `com.vaadin.external.google:android-json`
     (trazido pelo `jsonassert`, transitivo de `spring-boot-starter-test`) — mesma classe
     `org.json.JSONObject` duplicada no classpath de teste. Excluído o `android-json`.
4. ~~Autorização (`USER_ROLE` = administrador)~~ — **resolvido, decorre da #3**: com
   Spring Security no lugar, autorização vira `@PreAuthorize("hasRole('ADMINISTRATOR')")`
   nos métodos de *service*/*controller* (ou `SecurityFilterChain` com
   `.requestMatchers(...).hasRole(...)` pras rotas), em vez de checagem manual.
5. ~~Stub de e-mail~~ — **resolvido.** `io.deployo.jogoacoes.email.EmailSender`:
   `send(Long userId, String email, String link, EmailTemplate template)` — `userId` opcional
   (destinatário pode não ter conta ainda). `StubEmailSender` (única implementação por
   enquanto) não envia nada de verdade, só grava uma linha na nova tabela `sent_email`
   (entidade `SentEmail`, migration `V4__add_sent_email_table.sql`) com o e-mail, o link e
   o template usado — mesma regra de imutabilidade de `LOG` (`jogo_acoes_app` só tem
   `SELECT`/`INSERT`, ver `docs/diagrams/der.md`). `EmailTemplate` (enum, placeholder por
   enquanto): `INVITE`, `REGISTRATION_LINK`, `LOGIN_LINK` — cobre os três tipos de e-mail
   que os `.feature` pedem hoje (convite, link de confirmação de registro, link de login).
   Gerar o texto de verdade (Thymeleaf) fica pra outra iteração.

   **Testado nesta sessão:** `StubEmailSenderTest` — grava com usuário associado, grava
   sem usuário (destinatário sem conta), rejeita `userId` inexistente — 3/3 passando.
   `V4` validada rodando de verdade contra H2 junto com V1–V3
   (`Successfully applied 4 migrations`).
6. ~~Stub de captcha~~ — **resolvido: ALTCHA (v2), sem stub.** Captcha de prova-de-trabalho
   auto-hospedado (`org.altcha:altcha:2.0.3` + `org.json:json:20260814`, versões
   verificadas contra o Maven Central nesta sessão) — sem depender de serviço de
   terceiros (diferente de reCAPTCHA/hCaptcha), o que também evita ter que simular
   chamada de rede externa nos testes. Como o desafio é autocontido (servidor cria,
   cliente resolve, servidor verifica — tudo local), os cenários de
   `request_competition_entry.feature` são testados *de verdade*, não com um fake:
   "passou o teste" = resolver o desafio certo (`Altcha.solveChallenge`), "falhou o
   teste" = submeter uma solução adulterada. Validado nesta sessão com
   `AltchaSmokeTest` (cria desafio → resolve → verifica; e o caso de solução
   adulterada falhando) — 2/2 passando, round-trip completo confirmado nesta stack.
7. **Estado compartilhado entre *steps* de um mesmo cenário.** Hoje cada classe de *step*
   é independente; para implementar de verdade (ex.: guardar a resposta HTTP do `POST
   /competitions` num *Given/When* e checar no *Then*) precisa de um "World" — um bean
   `@CucumberContextConfiguration`-escopado por cenário (`cucumber-spring` já cuida do
   ciclo de vida) guardando coisas como o builder de competição em construção, a última
   resposta HTTP, o usuário "logado" na sessão do RestAssured.
8. **Dados de teste.** O desenho de `CompetitionMother`/`ParticipationMother`/
   `LoginSessionMother` já está em `iteracao-2.md` ("Dados de teste") — falta implementar
   as classes.

## Ordem sugerida de implementação

1. Decidir geração de código a partir do OpenAPI ou não (decisão 2), e resolver a
   sub-decisão do `LOGIN_SESSION` vs. `SessionRegistry` (decisão 3) — são a base de tudo o
   resto.
2. `EmailSender`/`LogEmailSender` e `CaptchaValidator` fake (decisões 5–6, sem
   dependência das outras).
3. World/contexto compartilhado do Cucumber (decisão 7).
4. Test Data Builders / Object Mothers (decisão 8).
5. Implementar feature por feature, da mais simples pra mais dependente:
   `create_competition` → `request_competition_entry` → `login` →
   `manage_competition_players` (esta última depende de competições/participações já
   existirem, então fica por último).

## Ambientes: renomeados e H2 separado do Postgres

Antes de começar a implementar os cenários de verdade, dois ajustes de infraestrutura,
descobertos como necessários já nesta retomada da Iteração 3:

- **Perfis renomeados** pra deixar claro qual ambiente é qual: `dev` → `sandbox` (sem
  Docker/Postgres disponível — cobre tanto rodar a aplicação isolada quanto o ambiente do
  próprio agente), `integracao` → `docker` (Postgres real em containers, local via
  `docker-compose` ou CI — mesmo conteúdo de antes, só o nome), `homologacao` → `staging`,
  `producao` → `production` (tradução, sem mudança de conteúdo). `docker-compose.yml` agora
  sobe com `SPRING_PROFILES_ACTIVE=docker`; o perfil padrão do sistema
  (`spring.profiles.default`) é `sandbox`.
- **`sandbox` passou a usar H2 de verdade, com Flyway** (antes, `dev` também apontava pro
  Postgres — não havia nenhum perfil H2 nomeado). Como as migrations em `db/migration` têm
  comandos específicos do Postgres (`GRANT`/`REVOKE` de papéis que não existem no H2), foi
  criada uma segunda pasta `db/migration-h2` com o mesmo schema, sem esses comandos —
  `application-sandbox.yml` e `src/test/resources/application.yml` apontam pra ela via
  `spring.flyway.locations`.
- **A suíte de testes passou a rodar o Flyway de verdade** contra o H2 (antes, era
  `hibernate.ddl-auto: create-drop`, then Hibernate gerava o schema a partir das entidades
  JPA, sem tocar nas migrations — ou seja, as migrations nunca eram exercitadas nos testes
  automatizados). Validado nesta sessão: `LogRepositoryTest`, `StubEmailSenderTest` e
  `SpringSessionSmokeTest` passam com `db/migration-h2` aplicado de verdade (`Successfully
  applied 4 migrations`) — inclusive dentro de `@DataJpaTest`, que troca o `DataSource` por
  um H2 embutido próprio (Spring Boot faz isso automaticamente) mas ainda assim roda o
  Flyway configurado antes do Hibernate validar o schema.

## Status da implementação

- **`create_competition.feature`: implementado, 7/7 cenários passando de verdade** (sem
  `Pending`). Camada completa: `CompetitionService` (validação de negócio: nome, data
  futura, duração > 0, taxas não-negativas, lista de e-mails para competição privada) +
  `CompetitionsController` (implementa `CompetitionsApi` gerado) + `SecurityConfig`
  (`SecurityFilterChain` com `permitAll`/`hasRole("ADMINISTRATOR")` por rota,
  `AuthenticationEntryPoint`/`AccessDeniedHandler` próprios devolvendo JSON) +
  `LoginController` (só o caminho feliz de `consumeLoginLink` — link válido de um usuário já
  registrado; as regras de dispositivo de `login.feature` ficam pra quando essa feature for
  implementada de verdade) + `ScenarioWorld` (contexto do Cucumber) + `UserMother`/
  `CompetitionMother` (dados de teste).
- **Duas pegadinhas resolvidas nesta sessão, não óbvias de antemão:**
  1. `server.servlet.context-path: /api` só em `src/main/resources/application.yml` não
     bastava — `src/test/resources/application.yml` **substitui** o `application.yml`
     principal durante os testes (não mescla), então a config de contexto precisou ser
     duplicada lá também (mesmo padrão de `datasource`/`flyway`/`session` já registrado
     antes). Sem isso, os matchers do `SecurityFilterChain` e as rotas do
     `@RequestMapping` gerado ficavam desalinhados sobre o que é "path" vs "context path".
  2. O `SessionFilter` do RestAssured, por padrão, só reconhece cookie de sessão chamado
     `JSESSIONID` — o Spring Session usa `SESSION`. Sem configurar
     `SessionConfig.sessionIdName("SESSION")` explicitamente (feito em
     `ScenarioWorld.request()`), cada chamada HTTP de um cenário parecia deslogada mesmo
     logo depois de um login bem-sucedido (confirmado batendo direto no banco:
     `spring_session_attributes` continha `SPRING_SECURITY_CONTEXT` corretamente — o
     problema era só o cookie não ser reenviado pelo cliente de teste).
- **`request_competition_entry.feature`: implementado, 7/7 cenários passando de verdade.**
  `EntryRequestService` cobre os dois ramos do único endpoint (`POST
  /competitions/{id}/entry-requests`): jogador autenticado confirma entrada direto da sessão
  (200 + `Participation`, 404 pra competição privada sem convite — mesmo código que "não
  existe", de propósito); jogador deslogado manda e-mail + captcha e recebe um link por
  e-mail (202) — reconhece se o e-mail já pertence a um usuário registrado (template
  `LOGIN_LINK`) ou não (`REGISTRATION_LINK`), e reaproveita a `Participation` pendente em vez
  de duplicar se pedir de novo (reenvia o link).
  - **`CaptchaService`**: usa o ALTCHA de verdade (mesma lib/algoritmo do `AltchaSmokeTest`),
    não um fake. Como não existe frontend ainda pra ditar o formato exato do payload que o
    widget ALTCHA normalmente gera, o token usado por `captchaToken` é um envelope próprio
    (`base64(JSON com os parâmetros do desafio + assinatura HMAC + solução)`) — ainda assim
    autocontido/stateless como o ALTCHA de verdade: o servidor não precisa ter guardado o
    desafio, só recalcula a assinatura HMAC com o segredo (`altcha.secret`, configurável por
    ambiente). Cenário de captcha errado usa a mesma técnica do `AltchaSmokeTest` — uma
    `Solution` adulterada — verificação falha de verdade.
  - **Fixtures novas**: `CompetitionFixtures` (persiste uma `Competition` direto, pulando o
    HTTP — mesmo raciocínio de "tela" ser não-operação) e `LoginHelper` (o *round-trip* de
    login que já existia dentro de `CommonSteps`, extraído pra ser reaproveitado também por
    "registered and logged in" desta feature).
- **`login.feature`: implementado, todos os 12 blocos de cenário passando de verdade**
  (incluindo os 3 `Scenario Outline`, com todas as combinações de `Examples`). `LoginService`
  passou a existir (extraído do `LoginController`, que ficou fino) cobrindo:
  - **Registro de novo jogador**: `completeRegistration` cria o `User`, atribui `PLAYER`,
    finaliza a `Participation` (`IN_COMPETITION`), estabelece sessão.
  - **Confirmação de jogador já registrado**: `consumeLoginLink` só autentica e devolve pra
    onde ir — quem efetivamente adiciona à competição é o mesmo endpoint de
    `request_competition_entry.feature` (`POST .../entry-requests`, autenticado), reaproveitado
    sem mudança.
  - **Regras de dispositivo, sem comparar "qual dispositivo" nenhuma vez**: a pergunta que o
    código realmente faz é "este dispositivo (esta sessão HTTP) já está autenticado?" — não
    "isso bate com o dispositivo original do link?". Um link já usado, clicado por um
    dispositivo **sem** sessão prévia → 409; clicado por um dispositivo **já** autenticado →
    apenas redireciona, ignorando o próprio estado do link. As duas regras de
    `login.feature` sobre dispositivo saem dessa única distinção, sem precisar de um
    identificador de dispositivo indo e voltando no contrato HTTP (não existe frontend ainda
    pra definir como ele seria enviado).
  - **Limite de dispositivos**: implementado com contabilidade própria em `LOGIN_SESSION`
    (uma sessão ativa — `ended_at IS NULL` — por login bem-sucedido; ao exceder o limite
    configurado, `login.max-devices-per-user`, a mais antiga é encerrada) — **não** usa o
    controle de sessões concorrentes nativo do Spring Security (`SessionRegistry`/
    `maximumSessions`), porque esse mecanismo só dispara automaticamente quando a
    autenticação passa pelos filtros padrão do Spring Security, e aqui a autenticação é
    montada manualmente (não há login por senha). Simplificação sabida: a sessão HTTP real
    do dispositivo mais antigo não é invalidada de fato ainda (só o registro de domínio) —
    faria isso precisar guardar o id da sessão do Spring Session dentro de `LOGIN_SESSION`,
    o schema atual não tem essa coluna. Nenhum cenário testa o dispositivo antigo tentando
    usar a sessão depois de expulso, então essa lacuna é honesta, não escondida.
  - **Link novo invalida o anterior**: `requestLoginLink` invalida (`invalidated_at`) todo
    `LOGIN_LINK` não usado do mesmo usuário antes de criar o novo.
  - `ScenarioWorld` ganhou suporte a múltiplos "dispositivos" (uma `SessionFilter`
    independente por nome de dispositivo) e `LoginLinkFixtures` (link/participação
    pendente, link expirado) pra montar os cenários sem precisar recriar o fluxo de e-mail
    inteiro em cada `Given`.
- **`manage_competition_players.feature`: implementado, todos os cenários passando de
  verdade** — a última das quatro. `PlayerManagementService` + `PlayersController`
  (`PlayersApi`, todas as cinco rotas): listar/filtrar por status, convidar novos jogadores
  numa competição privada existente, editar e-mail (rejeita formato inválido e e-mail
  duplicado dentro da mesma competição — ambos via a mesma mensagem genérica de erro, como o
  próprio `.feature` pede), remover jogador/cancelar convite pendente (mesma ação de
  domínio, como já dizia a descrição desse endpoint no `openapi.yaml`), reenviar convite
  individual ou em grupo. `ParticipationMapper` extraído (usado agora por dois controllers)
  em vez de duplicar a conversão de entidade pra DTO.
  - Ajuste no contrato: o corpo do `PATCH .../players/{id}` reaproveitava sem querer o
    schema de `RequestLoginLinkRequest` (mesmo formato `{email}`, o gerador do OpenAPI
    deduplica schemas anônimos estruturalmente iguais) — nomeado `UpdatePlayerEmailRequest`
    em `openapi.yaml` pra não ficar um nome enganoso no código gerado.
  - Simplificação sabida: nem todo status de `Participation` tem uma data própria no modelo
    (`LINK_CLICKED` não tem coluna dedicada, só `LoginLink.used_at`, que a API de players não
    expõe) — o cenário de filtro/listagem usa `firstEmailSentDate` como aproximação pra esse
    caso, e pula a checagem de data pra `EMAIL_NOT_SENT` (não há nenhum campo de data
    aplicável ainda).

## Estado final desta sessão

**As quatro `.feature` da Iteração 3 estão implementadas e passando de verdade** —
`create_competition`, `request_competition_entry`, `login`, `manage_competition_players`.
`mvn clean test`: 56 testes, **0 falhas, 0 erros** (nenhum `Pending` restante).

## CI: cobertura de testes com JaCoCo

Adicionado depois do trabalho acima, já com as quatro features prontas pra servir de base
real de medição (ver convenção geral em `docs/context/desenvolvimento.md`):

- `jacoco-maven-plugin` (0.8.15) no `pom.xml`: `report` na fase `test`, `check` na fase
  `verify` com piso de **80% de cobertura de linha** (`mvn verify` quebra se ficar abaixo).
  Excluído da contagem: `io.deployo.jogoacoes.api.**` e `org.openapitools.**` (código
  gerado a partir do `docs/openapi.yaml`, nenhuma linha escrita à mão).
- **Cobertura atual, medida nesta sessão**: **90,0%** de linha (`mvn clean verify` local,
  perfil `sandbox`/H2) — bem acima do piso, então o gate de 80% já nasce satisfeito, não
  bloqueando o primeiro merge.
- `.github/workflows/ci.yml`: roda em `pull_request`/`push` pro `master`. Sobe o Postgres
  via `docker compose up -d --wait db` (perfil `docker`, não `sandbox` — Postgres de
  verdade, não H2) e roda `mvn verify`. `application-docker.yml` ganhou
  `spring.flyway.locations` explícito (antes dependia por omissão do valor herdado do
  `application.yml` de teste, que aponta pro H2 — mesma pegadinha de shadowing já
  documentada antes, corrigida aqui antes que desse problema de verdade em CI).
- Comentário de cobertura na PR via `madrapps/jacoco-report`, rodando mesmo se o `mvn
  verify` falhar (o relatório XML já existe da fase `test`, antes do `check` da fase
  `verify` rodar) — assim a PR mostra o número exato mesmo quando o build está vermelho por
  causa da cobertura.
- **Branch protection do `master`, resolvido em outra sessão**: check do GitHub Actions
  (`mvn verify` no perfil `docker`, Postgres real) marcado como obrigatório antes de
  mesclar. Não foi só configuração — **bloqueou de fato o merge inicial**: a primeira
  execução real do workflow (algo nunca exercitável no sandbox deste agente, que só tem H2)
  encontrou três erros que passavam limpo em `sandbox`/H2 e quebravam contra Postgres real,
  corrigidos em sequência (PRs #6–#8):
  1. `application-docker.yml` (e `staging`/`production`) não sobrescrevia
     `spring.datasource.driver-class-name` — herdava por omissão o `org.h2.Driver` fixado
     no `application.yml` de teste, e tentava abrir uma URL `jdbc:postgresql://` com o
     driver errado.
  2. `@DataJpaTest` (`LogRepositoryTest`, `StubEmailSenderTest`) troca o `DataSource` por um
     H2 embarcado por padrão, **mesmo com o perfil `docker` ativo** — rodava contra H2 em
     vez do Postgres do container, e o `flyway.locations` desse perfil aponta pras
     migrations com `GRANT`/`REVOKE` específicas do Postgres, que falham no H2. Corrigido com
     `@AutoConfigureTestDatabase(replace = Replace.NONE)`.
  3. `LogRepository.findFiltered`: o padrão de filtro opcional
     `(:from IS NULL OR l.createdAt >= :from)` não dá ao Postgres um tipo de coluna pra
     inferir o tipo de `:from` só pelo `IS NULL` — H2 tolera a ambiguidade, o protocolo
     estendido do Postgres não (`"could not determine data type of parameter"`). Corrigido
     com `CAST(:from AS timestamp)`/`CAST(:to AS timestamp)`.

  Confirma na prática a lacuna que a nota anterior já previa: `sandbox`/H2 local não
  substitui uma execução real contra Postgres — os três problemas só apareceram numa
  execução real do GitHub Actions, nunca reproduzíveis no ambiente deste agente (sem daemon
  Docker).

## Logs de auditoria

`docs/diagrams/der.md` já modelava a tabela `LOG` desde a Iteração 2, mas deixava o
catálogo de `LogType` como placeholder e a escrita real de entradas como pendência desta
iteração — as quatro *features* passavam sem nenhum código gravando nela.

- **Decisão**: manter os três valores já esboçados em `LogType` (`COMPETITION_CREATED`,
  `PARTICIPATION_STATUS_CHANGED`, `LOGIN_LINK_ISSUED`) como o catálogo definitivo, em vez de
  criar um tipo por ação de negócio. Os três cobrem todo evento auditável das quatro
  *features* sem precisar crescer: qualquer mudança na lista de jogadores de uma competição
  (criação de convite, confirmação de entrada, conclusão de registro, e também a **remoção**
  de um jogador — não existe um `ParticipationStatus` de "removido", é uma exclusão física,
  então a mensagem do log é que deixa isso explícito) é `PARTICIPATION_STATUS_CHANGED`; todo
  e qualquer `LOGIN_LINK` novo (convite, pedido de entrada, reenvio, login) é
  `LOGIN_LINK_ISSUED`.
- **`AuditLogService`** (novo, em `service/`): um wrapper fino sobre `LogRepository.save`,
  usado pelos quatro serviços de negócio (`CompetitionService`, `EntryRequestService`,
  `LoginService`, `PlayerManagementService`) em cada ponto onde um desses três eventos
  acontece. O ator (`Log.user`) é o usuário autenticado quando existe uma sessão (admin
  criando/convidando, jogador confirmando entrada) e `null` nos fluxos anônimos (pedido de
  entrada por e-mail antes de qualquer login).
- **Bug real encontrado pelos testes desta seção, não coberto antes**: `removePlayer`
  apagava a `Participation` sem apagar o `LoginLink` que a referencia — `LOGIN_LINK.
  participation_id` é uma FK de verdade (diferente de `LOG.related_object_id`, que é
  deliberadamente polimórfica/sem FK). O cenário "Administrator cancels a pending invite"
  já cobria esse caminho no `.feature`, mas o *step* `Given` de fixture criava a
  `Participation` direto no banco com status `EMAIL_SENT`, sem nunca criar o `LoginLink`
  correspondente — então a suíte nunca exercitava a remoção com uma FK de fato presente.
  `AuditLoggingIntegrationTest` chama o serviço real de convite (que cria o `LoginLink`) e
  então remove o jogador, reproduzindo o `DataIntegrityViolation` que aconteceria em
  produção. Corrigido apagando os `LoginLink`s da participação antes de apagar a
  participação (`LoginLinkRepository.deleteByParticipation_Id`).
- **Testes**: `AuditLogServiceTest` (`@DataJpaTest`, mesmo padrão de `StubEmailSenderTest`)
  cobre o componente isolado — grava com e sem ator. `AuditLoggingIntegrationTest`
  (`@SpringBootTest`, `@Transactional` para não vazar dados entre testes no H2 em memória
  compartilhado do processo de teste) chama os quatro serviços de negócio direto e confere
  as entradas de `LOG` resultantes — inclusive que a entrada de remoção sobrevive à exclusão
  da `Participation` que ela referencia, a razão de `related_object_id` não ter FK.
