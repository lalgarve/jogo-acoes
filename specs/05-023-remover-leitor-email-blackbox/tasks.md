# Tasks: Remover a leitura de e-mail de `app/` — Python lê o LocalStack direto

Quebra `plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`). Decisões técnicas (filtro por
destinatário no cliente, regex do link, consumo por `id` ao ler) já resolvidas em `plan.md` —
esta lista só quebra a implementação em passos.

Issue-épico: [#84](https://github.com/lalgarve/jogo-acoes/issues/84) — cada linha abaixo é um
item de checklist nela, ou vira Issue própria quando grande o bastante para PR isolada (mesmo
critério de sempre).

**Dependência de ordem com a spec 05-026**: se a 05-026 (bootstrap do administrador) já tiver
sido implementada quando esta rodar, `BlackboxDataSeeder` já não existe mais e
`administratorIsSeededIdempotently` já foi removido de `BlackboxProfileIntegrationTest.java` —
a T004 abaixo ajusta só o que ainda estiver lá (os dois testes de `lastEmail...`), não assume que
o teste do seeder ainda existe. Checar o estado real do arquivo antes de editar, não o estado
descrito em `plan.md` (que assumiu a 05-023 rodando primeiro).

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| ~~T001~~ | Apagar `app/src/main/java/.../blackbox/BlackboxController.java` | — | [P] | #84 |
| ~~T002~~ | Apagar `app/src/main/java/.../blackbox/BlackboxSecurityConfigContributor.java` | — | [P] | #84 |
| ~~T003~~ | `app/src/main/java/.../blackbox/BlackboxDataSeeder.java` — remover `{@link ...BlackboxController}` do Javadoc (só se o arquivo ainda existir — ver nota de dependência com a 05-026 acima) | T001 | [P] | #84 |
| ~~T004~~ | `app/src/test/java/.../blackbox/BlackboxProfileIntegrationTest.java` — remover `lastEmailReturnsTheMostRecentLinkSentToAnAddress`/`lastEmailReturnsNotFoundWhenNothingWasSentToTheAddress`, o helper `saveSentEmail`, `@Autowired SentEmailRepository`, `@Value port`, e os imports órfãos (`EmailTemplate`, `SentEmail`, `SentEmailRepository`, `RestAssured`, `LocalDateTime` — conferir `TestEmails.unique` um por um); ajustar o Javadoc da classe | T001, T002 | | #84 |
| ~~T005~~ | Reescrever `blackbox-tests/common/blackbox_fixtures.py` — `last_email`/`last_email_link` passam a ler `GET {LOCALSTACK_URL}/_aws/ses`, filtrar por destinatário no cliente, extrair o link via regex, apagar a mensagem por `Id` antes de devolver; `LastEmail` perde o campo `template`; `LOCALSTACK_URL` vira variável de módulo (env var, padrão `http://localhost:4566`) — código completo em `plan.md` | — | [P] | #84 |
| ~~T006~~ | `blackbox-tests/seed/flows.py` — remover o argumento `base_url` das 4 chamadas a `last_email` (linhas 38, 136, 147, 158 no arquivo atual) | T005 | | #84 |
| ~~T007~~ | `blackbox-tests/seed/__main__.py` — remover o argumento `base_url` da chamada a `last_email` em `verify_clock` | T005 | [P] | #84 |
| ~~T008~~ | `blackbox-tests/features/steps/manage_active_sessions_steps.py` — remover `context.api_base_url` das 3 chamadas a `last_email_link` | T005 | [P] | #84 |
| ~~T009~~ | `blackbox-tests/features/steps/public_competition_entry_steps.py` — remover `context.api_base_url` das 2 chamadas a `last_email_link` | T005 | [P] | #84 |
| ~~T010~~ | `blackbox-tests/tests/test_mailbox.py` — remover `API_BASE_URL` da chamada a `last_email_link`; apagar a constante `API_BASE_URL` e o `import os` (nada mais no arquivo os usa) | T005 | [P] | #84 |
| ~~T011~~ | Teste novo em `blackbox-tests/tests/test_mailbox.py` (ou arquivo próprio) — envia dois e-mails pro mesmo endereço único, chama `last_email` uma vez (devolve o mais recente, apaga), chama de novo (devolve o outro, não `NoEmailSentError`) — confirma que só a mensagem lida é consumida, não a caixa inteira | T005 | [P] | #84 |
| ~~T012~~ | `README.md` — conferir se há menção a `GET /blackbox/last-email` e atualizar pra refletir a leitura direta do LocalStack (não localizada uma referência clara na investigação desta spec, além do que já documenta o pipeline de e-mail — confirmar na implementação) | T005 | [P] | #84 |
| ~~T013~~ | `blackbox-tests/README.md` — atualizar as linhas que hoje explicam a dependência de `GET /blackbox/last-email` (achadas por volta das linhas 25 e 75 nesta sessão — conferir texto exato na implementação) | T005 | [P] | #84 |
| ~~T014~~ | `mvn -pl app -am test` — confirmar verde depois de T001–T004 | T004 | | #84 |
| ~~T015~~ | Validação manual, com Docker: rodar `blackbox-tests` (`behave`/`pytest`) contra um `docker compose up` real e confirmar que todo cenário que depende de `last_email_link` continua passando | T006, T007, T008, T009, T010, T011, T014 | | #84 |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em qualquer
  ordem/em paralelo.
- T015 depende de Docker disponível no ambiente de implementação (mesma limitação já registrada
  em specs anteriores) — se não estiver disponível, registrar explicitamente o que não pôde ser
  verificado e validar o que der (T001–T014, que não precisam de `docker compose up`).
- Marcar o ID como concluído (`~~T001~~` ou checkbox `[x]`) na mesma PR que resolve a task,
  junto com o registro da verificação (`memory/constitution.md`, "Rastreamento de trabalho via
  Issues") — não deixar a tabela dessincronizada do estado real.

## Conferência contra o `master` (2026-10-06)

Todas as tasks foram implementadas no commit `f29b7f5` (PR #100), mas a tabela nunca foi
marcada. Marcadas agora, depois de conferir o código; nenhuma delas foi feita nesta revisão.

- **T001–T004**: `BlackboxController` e `BlackboxSecurityConfigContributor` não existem mais; o
  `BlackboxProfileIntegrationTest` só tem `captchaIsAlwaysAccepted`. O Javadoc da T003 sumiu de
  vez quando a spec 05-026 apagou o `BlackboxDataSeeder`.
- **T005–T011**: `blackbox-tests/common/blackbox_fixtures.py` lê `GET /_aws/ses` do LocalStack e
  apaga a mensagem lida; nenhum chamador passa mais `base_url`. Em `test_mailbox.py`, o
  `API_BASE_URL` ficou porque o teste novo da T011 usa.
- **T012/T013**: `README.md` e `blackbox-tests/README.md` descrevem a leitura direta do
  LocalStack.
- **T014/T015**: verificação registrada na mensagem do `f29b7f5` (`app` 155/155; `pytest`
  23/23 e `behave` 2/2 contra `docker compose up`) e na seção "(T015)" do `plan.md`.

Sobrou um comentário desatualizado fora do escopo das tasks:
`app/.../email/SentEmailRepository.java` ainda cita o `BlackboxController`.
