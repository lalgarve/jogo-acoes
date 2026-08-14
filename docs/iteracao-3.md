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

- Sete entidades JPA + repositórios (`br.com.jogoacoes.domain`/`repository`): `User`,
  `Role`, `UserRole`, `Competition`, `Participation`, `LoginLink`, `LoginSession`, `Log`.
- Migrations Flyway (schema completo, incluindo a tabela `log`).
- Runner do Cucumber (`RunCucumberTest`) e um método Java "pending" por texto de step único
  dos quatro `.feature` (`src/test/java/br/com/jogoacoes/steps/`).
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
   `login.feature`. Ainda precisa decidir se `LOGIN_SESSION` (nossa tabela) vira só um
   log de auditoria alimentado pelos eventos do `SessionRegistry`, ou se substituímos o
   `SessionRegistry` em memória por uma implementação própria apoiada em `LOGIN_SESSION`
   (útil se a aplicação rodar em mais de uma instância no futuro — o registro em memória
   padrão não é compartilhado entre instâncias). Fica como *sub-decisão* pra quando
   chegar em `login.feature`.
4. ~~Autorização (`USER_ROLE` = administrador)~~ — **resolvido, decorre da #3**: com
   Spring Security no lugar, autorização vira `@PreAuthorize("hasRole('ADMINISTRATOR')")`
   nos métodos de *service*/*controller* (ou `SecurityFilterChain` com
   `.requestMatchers(...).hasRole(...)` pras rotas), em vez de checagem manual.
5. **Stub de e-mail.** Interface `EmailSender` (`send(to, template, data)` ou similar) com
   implementação `LogEmailSender` (loga em vez de enviar) — decisão de baixo risco, sem
   pergunta em aberto, só falta desenhar a interface.
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
