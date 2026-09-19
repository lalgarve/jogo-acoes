# Tasks: Ambiente de testes blackbox (captcha sempre válido + cobertura JaCoCo da aplicação)

Quebra `plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`). Todas as decisões técnicas
referenciadas abaixo (interface `CaptchaVerifier`, nome do perfil, arquivo de sobreposição do
`docker-compose`, onde o agente/CLI do JaCoCo vivem, como o relatório é gerado, como o
administrador é semeado, como o link de e-mail é lido de fora) já estão resolvidas em
`plan.md` — esta lista só quebra a implementação em passos.

**Três frentes independentes até quase o fim**: T001–T004 (captcha), T005–T009 (JaCoCo) e
T010–T013 (administrador semeado + leitura de e-mail) não dependem umas das outras — só se
encontram em T014/T015, que sobem o ambiente combinado e verificam tudo junto.

**Sem Cucumber/contrato OpenAPI novo** — infraestrutura/configuração de ambiente, sem `.feature`
novo (mesmo padrão das specs 05-006/05-007/05-011/05-013); `GET /blackbox/last-email` não entra
em `docs/openapi.yaml` (andaime de teste, não contrato de produto — ver `plan.md`), então também
não há mudança de contrato pra validar contra `OpenApiRoutesConsistencyTest`/
`OpenApiRolesConsistencyTest`.

Todas as tarefas abaixo são acompanhadas como checklist na Issue-épico
[#68](https://github.com/lalgarve/jogo-acoes/issues/68) — nenhuma virou Issue própria (mesmo com
três frentes independentes, cada uma pequena o bastante para não precisar de PR isolada).

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| T001 | Extrair a interface `captcha/CaptchaVerifier.java` (só `boolean verify(String token)`) a partir de `CaptchaService`; renomear a classe atual para `AltchaCaptchaVerifier` (implementa a interface, mantém `createChallenge`/`encodeToken` intactos — comportamento idêntico ao de hoje) | — | [P] | #68 |
| T002 | Criar `captcha/AlwaysPassCaptchaVerifier.java` (`verify` sempre retorna `true`, nenhuma dependência); anotar as duas implementações com `@ConditionalOnProperty(name = "captcha.verifier", ...)` (`altcha`, `matchIfMissing = true`, e `always-pass`) | T001 | | #68 |
| T003 | Atualizar `EntryRequestService` para depender de `CaptchaVerifier` (interface), não da classe concreta; atualizar `RequestCompetitionEntrySteps` (Cucumber) para referenciar `AltchaCaptchaVerifier` no lugar de `CaptchaService` | T001 | | #68 |
| T004 | Rodar a suíte completa (`mvn test`) — confirmar verde, nenhum cenário/teste existente alterado além dos ajustados em T003; o perfil de teste padrão continua exigindo o captcha real (`AltchaCaptchaVerifier` via `matchIfMissing`) | T002, T003 | | #68 |
| T005 | Criar `app/src/main/resources/application-blackbox.yml` (só `captcha.verifier: always-pass`) | T002 | [P] | #68 |
| T006 | Adicionar duas execuções de `maven-dependency-plugin` (`copy`, fase `package`) em `app/pom.xml`: `org.jacoco:org.jacoco.agent:0.8.15:jar:runtime` → `target/jacoco/jacocoagent.jar`, `org.jacoco:org.jacoco.cli:0.8.15:jar:nodeps` → `target/jacoco/jacococli.jar` (mesma versão do `jacoco-maven-plugin` já usado) | — | [P] | #68 |
| T007 | Atualizar `Dockerfile`: estágio final copia `target/jacoco/jacocoagent.jar`/`jacococli.jar` (gerados por T006 no estágio de build) para `/app/`, sempre — nenhum `ENTRYPOINT`/`CMD` novo | T006 | | #68 |
| T008 | Criar `docker-compose.blackbox.yml` (sobreposição): serviço `app` ganha `SPRING_PROFILES_ACTIVE: docker,blackbox`, `JAVA_TOOL_OPTIONS: -javaagent:/app/jacocoagent.jar=output=tcpserver,address=*,port=6300,includes=dev.leilaalgarve.jogoacoes.*` e a porta `"6300:6300"` publicada; `docker-compose.yml` original fica intocado | T005, T007 | | #68 |
| T009 | Criar `scripts/blackbox-coverage.sh`: `jacococli dump` (contra `localhost:6300`) seguido de `jacococli report` (`--classfiles app/target/classes --sourcefiles app/src/main/java --html target/site/jacoco-blackbox`) | T007 | [P] | #68 |
| T010 | Criar `blackbox/BlackboxDataSeeder.java` (`ApplicationRunner`, `@Component @Profile("blackbox")`): garante, de forma idempotente, um `User` com e-mail fixo (`admin@blackbox.local`) + `UserRole` `ADMINISTRATOR` — mesma lógica de `UserMother.administrator()` (teste), agora em código principal | — | [P] | #68 |
| T011 | Adicionar `SentEmailRepository.findTopByEmailOrderBySentAtDesc(String email)` | — | [P] | #68 |
| T012 | Criar `blackbox/BlackboxController.java` (`@RestController @Profile("blackbox")`, `GET /blackbox/last-email?email={endereço}` — devolve `{ link, template, sentAt }` do envio mais recente ou `404`) e `blackbox/BlackboxSecurityConfigContributor.java` (`permitAll` na rota acima) | T011 | | #68 |
| T013 | Rodar a suíte completa (`mvn test`) — confirmar verde; em particular, `ArchitectureTest` continua passando (o `BlackboxSecurityConfigContributor` no mesmo pacote satisfaz a regra de todo `@RestController` ter um) e `OpenApiRoutesConsistencyTest`/`OpenApiRolesConsistencyTest` continuam passando (a rota nova nunca aparece no perfil em que os testes rodam) | T010, T012 | | #68 |
| T014 | Build da imagem e `docker compose -f docker-compose.yml -f docker-compose.blackbox.yml up` — confirmar manualmente, de ponta a ponta: (a) `GET /blackbox/last-email` devolve o link depois de um `POST /login-requests` pro e-mail do administrador semeado; (b) esse link autentica via `GET /login-links/{token}`; (c) autenticado como administrador, `POST /competitions` com `captchaToken` vazio/qualquer é aceito; (d) nenhum outro perfil ativa qualquer um dos três mecanismos (`docker-compose up` normal continua exigindo captcha real e não tem o endpoint/semeadura); (e) `scripts/blackbox-coverage.sh` gera `target/site/jacoco-blackbox/index.html` com dados de cobertura reais depois desse fluxo | T008, T009, T013 | | #68 |
| T015 | Atualizar `README.md`: seção sobre o ambiente `blackbox` — como subir, e-mail do administrador semeado (sem senha — login por link), como ler o link de um e-mail enviado, escopo do bypass de captcha (só aqui), como gerar o relatório de cobertura da aplicação exercitada externamente, e que isso é independente do relatório de sempre da suíte Java (`target/site/jacoco/`, `mvn test`/`mvn verify`) | T014 | | #68 |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em qualquer
  ordem/em paralelo.
- Cada linha vira um item de checklist na Issue-épico da feature, ou uma Issue própria
  quando grande o suficiente para PR isolada — a Issue leva o label `iteration-5`, além do
  label de tipo (`feat`).
- T014 depende de um daemon Docker disponível no ambiente de implementação — se não houver
  (mesma limitação já registrada em `SqsEmailSenderDockerIntegrationTest`/spec 05-013), validar
  o que der por análise estática (`docker compose config`, revisão do `Dockerfile`/script) e
  registrar explicitamente a limitação, em vez de marcar como concluído sem execução real.
- Marcar o ID como concluído (`~~T001~~` ou checkbox `[x]`) quando o commit que a resolve for
  mesclado — não deixar a tabela dessincronizada do estado real.
