# Tasks: Bootstrap do primeiro administrador e do remetente de e-mail via variável de ambiente

Quebra `plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`). Decisões técnicas (nome do
serviço, condição de idempotência, tratamento de e-mail malformado, nomes das variáveis) já
resolvidas em `plan.md` — esta lista só quebra a implementação em passos.

Issue-épico: [#84](https://github.com/lalgarve/jogo-acoes/issues/84) — cada linha abaixo é um
item de checklist nela, ou vira Issue própria quando grande o bastante para PR isolada (mesmo
critério de sempre). Lembrete do `plan.md`: a spec 05-023 ainda não foi implementada, então
`BlackboxController`/`BlackboxSecurityConfigContributor` e os testes de `GET /blackbox/last-email`
não são tocados por nenhuma tarefa abaixo.

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| T001 | `login/UserRoleRepository.java` — adicionar `boolean existsByRole_Name(String roleName)` | — | [P] | #84 |
| T002 | `login/UserProvisioningService.java` (novo) — `createUser(String email, String name, List<String> roleNames)` e `existsAnyWithRole(String roleName)`, consolidando a lógica hoje duplicada em `BlackboxDataSeeder`/`CompetitionLinkHandler.complete()` (ver código completo em `plan.md`) | T001 | | #84 |
| T003 | `login/UserProvisioningServiceTest.java` (novo, unitário) — casos: cria com um papel, cria com dois papéis, `existsAnyWithRole` delega pro repositório, papel não semeado lança `IllegalStateException` | T002 | [P] | #84 |
| T004 | `bootstrap/AdministratorBootstrap.java` (novo, `ApplicationRunner`) — lê `ADMIN_EMAIL`/`ADMIN_NAME`, valida e-mail via `jakarta.validation.constraints.Email`, chama `UserProvisioningService` (ver código completo em `plan.md`); confirmar antes que `Validator` (Jakarta Bean Validation) já é bean Spring injetável sem configuração extra | T002 | | #84 |
| T005 | `bootstrap/AdministratorBootstrapTest.java` (novo, unitário, mocks de `UserProvisioningService`/`Validator`) — casos: `ADMIN_EMAIL` vazio não age, admin já existe não age, `ADMIN_EMAIL` válido cria com nome default "Administrator", `ADMIN_NAME` setado usa esse nome, `ADMIN_EMAIL` malformado não age | T004 | [P] | #84 |
| T006 | Apagar `blackbox/BlackboxDataSeeder.java` | T004 | | #84 |
| T007 | `BlackboxProfileIntegrationTest.java` — remover só `administratorIsSeededIdempotently()` e o que só ele usava (`BlackboxDataSeeder`, `UserRepository`, `UserRoleRepository` injetados); manter `captchaIsAlwaysAccepted()` e os dois testes de `lastEmail...` exatamente como estão; ajustar o Javadoc da classe ("three mechanisms" → dois) | T006 | | #84 |
| T008 | `blackbox/BlackboxController.java` — trocar `{@link BlackboxDataSeeder}` por `{@code BlackboxDataSeeder}` no Javadoc (referência ficaria pendurada depois de T006); nenhuma outra mudança nesse arquivo | T006 | [P] | #84 |
| T009 | `email-lambda/src/main/resources/application.properties` — `email.sender-address=${EMAIL_SENDER_ADDRESS:no-reply@jogo-acoes.example}` | — | [P] | #84 |
| T010 | `docker/localstack/init/02-verify-ses-sender.sh` — ler `EMAIL_SENDER_ADDRESS` em vez do literal hardcoded (`${EMAIL_SENDER_ADDRESS:?EMAIL_SENDER_ADDRESS not set}`) | — | [P] | #84 |
| T011 | `docker/localstack/init/03-deploy-email-lambda.sh` — acrescentar `EMAIL_SENDER_ADDRESS` ao bloco `--environment Variables={...}` já existente, lendo a mesma variável; `sh -n` pra validar a sintaxe depois de escapar as aspas corretamente | — | [P] | #84 |
| T012 | `docker-compose.yml` — `EMAIL_SENDER_ADDRESS: no-reply@jogo-acoes.example` no `environment:` do serviço `localstack` | T009, T010, T011 | | #84 |
| T013 | `docker-compose.blackbox.yml` — `ADMIN_EMAIL: success+admin@simulator.amazonses.com` no `environment:` do serviço `app` | T004 | [P] | #84 |
| T014 | `memory/constitution.md` (seção "Débito reconhecido, correção planejada") — remover `BlackboxDataSeeder` (resolvido aqui) e `EmailQueuePoller` (já removido pela Issue #87, nota desatualizada) da lista; só `BlackboxController`/`BlackboxSecurityConfigContributor` continuam pendentes. Commit `decision` próprio, separado do resto desta spec | T006 | [P] | #84 |
| T015 | `mvn -pl app -am test` e `mvn -pl email-lambda -am test` — confirmar tudo verde, incluindo os testes novos (T003, T005) e o `BlackboxProfileIntegrationTest` revisado (T007) | T003, T005, T007, T009 | | #84 |
| T016 | Validação manual, com Docker: `docker compose up` (sem sobreposição) confirma nenhum admin criado; `docker compose -f docker-compose.yml -f docker-compose.blackbox.yml up` confirma o admin semeado conseguindo logar; reiniciar com `ADMIN_EMAIL` diferente confirma que não cria um segundo admin | T012, T013, T015 | | #84 |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em qualquer
  ordem/em paralelo.
- T016 depende de Docker disponível no ambiente de implementação (mesma limitação já registrada
  em specs anteriores) — se não estiver disponível, registrar explicitamente o que não pôde ser
  verificado e validar o que der (T001–T015, que são só código/config, sem precisar de
  `docker compose up`).
- Marcar o ID como concluído (`~~T001~~` ou checkbox `[x]`) quando o commit que a resolve for
  mesclado — não deixar a tabela dessincronizada do estado real.
