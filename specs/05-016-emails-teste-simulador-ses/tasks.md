# Tasks: Padronizar e-mails de teste para o simulador de caixa de entrada do Amazon SES

Quebra `plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`). Todas as decisões técnicas
referenciadas abaixo (formato `success+<qualificador>@simulator.amazonses.com`, o helper
`TestEmails`, a exceção do `BlackboxDataSeeder.ADMIN_EMAIL`) já estão resolvidas em `plan.md` —
esta lista só quebra a implementação em passos.

**Sem Cucumber/contrato OpenAPI novo** — troca de literais em código de teste existente (mais
uma constante em `src/main`, `BlackboxDataSeeder.ADMIN_EMAIL`), sem `.feature` novo nem rota
nova (mesmo padrão das specs 05-006/05-008).

Todas as tarefas abaixo são acompanhadas como checklist na Issue-épico
[#70](https://github.com/lalgarve/jogo-acoes/issues/70) — nenhuma virou Issue própria (todas
pequenas o bastante para não precisar de PR isolada).

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| T001 | Criar `app/src/test/java/dev/leilaalgarve/jogoacoes/common/testsupport/TestEmails.java` — `unique(String qualifier)` (`"success+" + qualifier + "-" + UUID.randomUUID() + "@simulator.amazonses.com"`) e `fixed(String qualifier)` (`"success+" + qualifier + "@simulator.amazonses.com"`) | — | [P] | #70 |
| T002 | Migrar `common/testsupport/UserMother.java` e `common/testsupport/CompetitionMother.java` para `TestEmails.unique(...)` | T001 | [P] | #70 |
| T003 | Migrar `competition/steps/RequestCompetitionEntrySteps.java`, `competition/steps/CreateCompetitionSteps.java` e `competition/steps/ManageCompetitionPlayersSteps.java` para `TestEmails.unique(...)`/`TestEmails.fixed(...)` (`"not-an-email"` não muda — é o próprio objeto do teste de validação) | T001 | [P] | #70 |
| T004 | Migrar `link/LoginSteps.java` e `link/LinkServiceTest.java` para `TestEmails.unique(...)`/`TestEmails.fixed(...)` | T001 | [P] | #70 |
| T005 | Migrar `login/LoginLinkHandlerTest.java` e `competition/CompetitionLinkHandlerTest.java` para `TestEmails.fixed(...)`/`TestEmails.unique(...)` (endereços como `"player@example.com"`, `"admin@example.com"`, `"someone@example.com"`) | T001 | [P] | #70 |
| T006 | Migrar `email/EmailContentRendererTest.java`, `email/StubEmailSenderTest.java`, `email/SqsEmailSenderTest.java` e `email/SqsEmailSenderDockerIntegrationTest.java` para `TestEmails.fixed(...)`/`TestEmails.unique(...)` | T001 | [P] | #70 |
| T007 | Migrar `log/LogRepositoryTest.java`, `log/AuditLogServiceTest.java`, `log/AuditLoggingIntegrationTest.java` e `common/logging/QueueLoggingAspectIntegrationTest.java` para `TestEmails.fixed(...)`/`TestEmails.unique(...)` | T001 | [P] | #70 |
| T008 | Migrar os e-mails dinâmicos de `blackbox/BlackboxProfileIntegrationTest.java` (`"blackbox-test-...@example.com"`, `"never-sent-...@example.com"`) para `TestEmails.unique(...)` — a referência a `BlackboxDataSeeder.ADMIN_EMAIL` não muda aqui, só o valor da constante (T009) | T001 | [P] | #70 |
| T009 | Atualizar `BlackboxDataSeeder.ADMIN_EMAIL` (`app/src/main`, spec 05-014) de `admin@blackbox.local` para `success+admin@simulator.amazonses.com` | — | [P] | #70 |
| T010 | Atualizar o literal de `email-lambda/src/test/java/dev/leilaalgarve/jogoacoes/email/lambda/EmailSendHandlerTest.java` (`"player@example.com"` → `"success+player@simulator.amazonses.com"`) | — | [P] | #70 |
| T011 | Atualizar `blackbox-tests/features/steps/public_competition_entry_steps.py`: constante `ADMIN_EMAIL` (mesmo valor de T009) e o e-mail dinâmico do jogador (`context.player_email`, formato equivalente ao `TestEmails.unique`) | T009 | | #70 |
| T012 | Atualizar `README.md` (seção "Ambiente de testes blackbox") — a menção a `admin@blackbox.local` passa a citar o novo endereço | T009 | | #70 |
| T013 | Rodar a suíte completa do módulo `app` (`mvn -pl app -am test`) — confirmar verde, incluindo `BlackboxProfileIntegrationTest` com o novo `ADMIN_EMAIL` | T002, T003, T004, T005, T006, T007, T008, T009 | | #70 |
| T014 | Rodar a suíte de `email-lambda` (`mvn -pl email-lambda -am test`) — confirmar verde (ou *skip* gracioso sem Docker, mesmo comportamento já documentado em `EmailSendHandlerTest`) | T010 | [P] | #70 |
| T015 | Rodar `behave`/`pytest` de `blackbox-tests/` contra o ambiente `blackbox` (spec 05-014/05-015) — confirmar verde, em particular o login do administrador semeado com o novo e-mail | T011, T012, T013 | | #70 |
| T016 | `grep -rn "@example\.com" app/src/test email-lambda/src/test blackbox-tests/` — confirmar que só sobram os endereços propositalmente inválidos listados em "Fora de escopo" de `spec.md` (`"not-an-email"`) | T013, T014, T015 | | #70 |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em qualquer
  ordem/em paralelo.
- Cada linha vira um item de checklist na Issue-épico da feature, ou uma Issue própria
  quando grande o suficiente para PR isolada — a Issue leva o label `iteration-5`, além do
  label de tipo (`test`).
- T015 depende de um ambiente `blackbox` (spec 05-014) acessível no ambiente de implementação
  — se `docker compose up` completo não estiver disponível (mesma limitação já registrada em
  `specs/05-014-ambiente-testes-blackbox/tasks.md`), validar contra o jar empacotado + Postgres
  real (mesmo caminho já usado para verificar as specs 05-014/05-015), e registrar
  explicitamente qual dos dois foi usado.
- Marcar o ID como concluído (`~~T001~~` ou checkbox `[x]`) quando o commit que a resolve for
  mesclado — não deixar a tabela dessincronizada do estado real.
