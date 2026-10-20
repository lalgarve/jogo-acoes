# Tasks: Os testes não sobem containers

Quebra `plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`).

Issue-épico: [#193](https://github.com/lalgarve/jogo-acoes/issues/193).

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| T001 | Regra contra Testcontainers (ArchUnit) nos quatro módulos e conferência de `quarkus.devservices.enabled=false` na `email-lambda`. Confirmar que a conferência da `email-lambda` falha antes da T002 | — | [P] | — |
| T002 | `email-lambda`: Dev Services desligado e SES (e SQS, com a 05-046) apontando para `http://localhost:4566` nas propriedades de teste; ajustes no `EmailSendHandlerTest` (remetente, comentários) e no comentário do `pom.xml`. Confirmar a suíte verde com o LocalStack do Compose e vermelha, com erro de conexão, sem ele | T001 | | — |
| T003 | CI: "Build and test email-lambda" antes de derrubar os containers | T002 | | — |
| T004 | Repositório inicial das listas só em `staging`/`production` (`plan.md`): `INSERT` fora da V6, migration de dados em `db/seed-email-service/` (sem duplicar), `spring.flyway.locations` no `config-repo`; `DisposableDomainCleanupHooks` sem o caso do GitHub. Confirmar a suíte do `email-service` verde | — | [P] | — |
| T005 | Documentação: constitution ("Testes exigem a infraestrutura de pé"), notas nas specs 05-028 e 05-048, nota no caderno de testes do Serviço de E-mail, README ("Como rodar os testes": a `email-lambda` também precisa do LocalStack de pé) | T002–T004 | | — |
| T006 | Critério de aceite de exceções (`memory/constitution.md`): nenhuma classe de exceção nova; registrar aqui | T002, T004 | | — |
| T007 | Verificação manual, com comandos prontos: suíte da `email-lambda` com e sem LocalStack; `docker ps` sem container novo durante o `mvn test`; `GET /disposable-domain-sources` vazio num banco novo do `docker` | T001–T005 | | — |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em qualquer
  ordem/em paralelo.
- Marcar o ID como concluído (`~~T001~~` ou checkbox `[x]`) na mesma PR que resolve a task,
  junto com o registro da verificação.
