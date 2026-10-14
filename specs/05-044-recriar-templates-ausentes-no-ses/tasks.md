# Tasks: Recriar no SES os templates que só existem no banco do email-service

Quebra `plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`).

**Ordem:** testes que reproduzem o bug primeiro, falhando pelo motivo esperado; depois as
correções.

Todas as tarefas abaixo são acompanhadas como checklist na Issue
[#144](https://github.com/lalgarve/jogo-acoes/issues/144).

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| T001 | `register_templates.feature`: `Rule` "email-service recreates on SES the templates SES lost" com o cenário de atualizar um template que o SES perdeu (passo `Given SES lost the template named "welcome"`, com `DeleteTemplate` direto no LocalStack). Rodar e confirmar que falha com 404 | — | [P] | #144 |
| T002 | Mesma `Rule`: cenário de preview de um template que o SES perdeu. Confirmar que falha com 404 | T001 (passo compartilhado) | | #144 |
| T003 | Mesma `Rule`: cenário de cadastrar `welcome` com `client-a__welcome` já no SES e sem linha no banco (passo `Given SES already has a template named "welcome" with no record`, com `CreateTemplate` direto). Confirmar que falha com 422 | T001 (passos compartilhados) | | #144 |
| T004 | `SesTemplateReconcilerIntegrationTest` (ver `plan.md`, "Verificações", item 2): template ausente; 11 templates ausentes (mais de uma página de `ListTemplates`; registrar se o LocalStack pagina); template só no SES fica e gera `WARN`; SES inacessível impede a subida com `TemplateReconciliationException`. Confirmar que os três primeiros falham por o SES continuar sem os templates e que o último falha por o contexto subir (ou não compilar, se a exceção ainda não existir; registrar qual) | — | [P] | #144 |
| T005 | `SesTemplateClient.putTemplate` (create; `AlreadyExistsException` → update) e `updateTemplate` com recriação (`TemplateDoesNotExistException` → create). `TemplateService.create` passa a usar `putTemplate`. Confirmar T001 e T003 verdes | T001, T003 | | #144 |
| T006 | `TemplateService.preview`: recria a partir da linha e renderiza de novo quando o SES não tem o template. Confirmar T002 verde | T002, T005 | | #144 |
| T007 | `TemplateReconciliationException` e `SesTemplateReconciler` (`SmartInitializingSingleton`, `findAll()` × `ListTemplates` paginado, `putTemplate` para os ausentes, `WARN` para os órfãos, falhas juntas numa exceção só). Confirmar T004 verde | T004, T005 | | #144 |
| T008 | Documentação: descrições de `PUT /templates/{name}`, `POST /templates/{name}/preview` e `POST /templates` em `docs/openapi-email-service.yaml` (sem mudar status); invariante novo em `specs/05-025-servico-email-templates/data-model.md` | T005, T006, T007 | [P] | #144 |
| T009 | Critério de aceite de exceções (`memory/constitution.md`, "Critério de aceite: exceções e caminhos de erro exercitados por teste"): listar as classes de exceção criadas (esperado: `TemplateReconciliationException`) e os branches de exceção adicionados ou alterados (`AlreadyExistsException` em `putTemplate`, `TemplateDoesNotExistException` em `updateTemplate`, a nova tentativa do preview, falha de `ListTemplates` e de `putTemplate` na reconciliação); para cada um, apontar o teste que o exercita. O que não tiver teste é justificado em `plan.md` (já previsto: `SesException` genérica na recriação com o SES no ar) e avisado no chat ao abrir a PR. Registrar a lista aqui | T005, T006, T007 | | #144 |
| T010 | Rodar a suíte completa do `email-service` e `mvn -pl app -am verify` — confirmar verde | T008, T009 | | #144 |
| T011 | Verificação manual (Leila): passos 1–4 da Issue #144 (`docker compose restart localstack` entre duas execuções da suíte do `app`); o `app` sobe na segunda sem `down -v` | T010 | | #144 |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em qualquer
  ordem/em paralelo.
- Cada linha vira um item de checklist na Issue #144, ou uma Issue própria quando grande o
  suficiente para PR isolada — a Issue leva o label `iteration-5`, além do label de tipo (`bug`).
- Marcar o ID como concluído (`~~T001~~` ou checkbox `[x]`) na mesma PR que resolve a task,
  junto com o registro da verificação (`memory/constitution.md`, "Rastreamento de trabalho via
  Issues") — não deixar a tabela dessincronizada do estado real.
