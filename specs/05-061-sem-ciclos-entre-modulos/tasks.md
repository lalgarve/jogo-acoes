# Tasks: Nenhum ciclo de dependência entre módulos

Quebra `plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`).

Issue-épico: —

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| T001 | `ModuleDependencyGraph` no `app` (montagem do grafo + Tarjan + ciclo concreto) e teste unitário com grafos à mão: sem ciclo, ciclo de 2, ciclo de 3, dois ciclos, auto-referência | — | [P] | — |
| T002 | Teste no `ArchitectureTest` do `app`. Confirmar que falha mostrando `competition → email → competition` e a referência `EmailRequest → RequestType` | T001 | | — |
| T003 | `email`: interfaces `OutgoingEmail` e `EmailTemplateSource`; `EmailSender`, `EmailServiceEmailSender`, `StubEmailSender`, `SentEmailRecorder` e `EmailTemplateSynchronizer` usando as duas; migration de `sent_email.template`; saem `EmailRequest` e `EmailTemplate`. Teste do nome de template duplicado | T002 | | — |
| T004 | `competition` e `loginsession`: e-mails próprios implementando `OutgoingEmail`, escolha do template (com `RequestType` só na `competition`) e `EmailTemplateSource` de cada um; templates movidos para `email-templates/<cliente>/`. Confirmar o teste da T002 verde, a suíte do `app` verde e a suíte de caixa-preta de e-mail verde | T003 | | — |
| T005 | Mesmo teste no `email-service` (cópia de `ModuleDependencyGraph` e do teste unitário). Confirmar verde | T001 | [P] | — |
| T006 | Constitution, "Fronteira entre módulos e serviços": sem ciclo entre módulos de um serviço, garantido por teste de arquitetura | T004, T005 | | — |
| T007 | Critério de aceite de exceções: nome de template duplicado exercitado por teste; registrar aqui | T003 | | — |
| T008 | Verificação manual, com comandos prontos: suíte do `app` e do `email-service` verdes; plantar um `user → log` temporário e ver a mensagem do ciclo; os cinco e-mails chegam iguais aos de antes no ambiente com o overlay `blackbox` | T004, T005 | | — |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em qualquer
  ordem/em paralelo.
- Marcar o ID como concluído (`~~T001~~` ou checkbox `[x]`) na mesma PR que resolve a task,
  junto com o registro da verificação.
