# Tasks: Fronteira síncrona entre `app` e `email-service` (Etapa 2)

Quebra `plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`).

**Testes de verificação primeiro.** T001 a T005 são escritos e rodados antes de qualquer
mudança de código de produção, e o resultado de cada um fica registrado aqui:

- Regras que hoje têm violação nascem vermelhas listando **exatamente** as violações
  inventariadas (`SqsEmailSender` para SQS, `EmailContentRenderer` para Thymeleaf).
- Regras que hoje não têm violação nascem verdes; a prova de que pegam algo é introduzir uma
  violação temporária, ver o build falhar pelo motivo certo e desfazer (sem commitar a violação).
- Testes de integração do cliente nascem vermelhos porque o cliente ainda não existe.

As tarefas seguintes os deixam verdes. Tudo vai na mesma PR (commits intermediários vermelhos de
propósito), mesclada só com o build verde.

**Pré-requisitos:** spec 05-031 mesclada (`POST /emails` e Lambda com template, PR #110). As
decisões de requisito da `spec.md` estão resolvidas (2026-10-05). A implementação começa só quando for pedida
explicitamente.

Issue: [#119](https://github.com/lalgarve/jogo-acoes/issues/119) — cada linha abaixo é um item de
checklist nela.

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| T001 | `app`, `common/ArchitectureTest`: regras (b) nenhuma dependência de SQS e (c) nenhuma dependência de `org.thymeleaf..` (ver `plan.md`). Rodar e confirmar que falham com **exatamente** 1 violação cada (`SqsEmailSender`; `EmailContentRenderer`). Ao contar, filtrar `getAccessesFromSelf()` de cada origem por `getTargetOwner()` (lição da 05-029). Registrar aqui | — | [P] | #119 |
| T002 | `app`, `common/ArchitectureTest`: regras (d) a API Feign gerada (`..email.client.api..`) só é acessada pelo `EmailServiceGateway` e (e) nada fora de `..email..` depende de `..email.client..`. Rodar e registrar: (d) vermelha porque o pacote ainda não existe; (e) verde | — | [P] | #119 |
| T003 | `app`, `common/ArchitectureTest`: regra (a) nenhuma classe do `app` reside em nem depende de `..jogoacoes.emailservice..`. Nasce verde: provar com uma violação temporária (uma classe de teste em `dev.leilaalgarve.jogoacoes.emailservice` usada por uma classe do `app`), ver falhar, desfazer. Registrar | — | [P] | #119 |
| T004 | `app/pom.xml` e `email-service/pom.xml`: `maven-enforcer-plugin` com `bannedDependencies` (`app` proíbe `dev.leilaalgarve.jogoacoes:email-service`; `email-service` proíbe `dev.leilaalgarve.jogoacoes:jogo-acoes`, inclusive transitivas). Nasce verde: provar adicionando a dependência proibida temporariamente, ver `mvn validate` falhar, desfazer. Registrar | — | [P] | #119 |
| T005 | `email-service`: `archunit-junit5` no `pom.xml` e `common/ArchitectureTest` novo — nenhuma classe depende de `dev.leilaalgarve.jogoacoes..` fora de `..emailservice..`. Nasce verde: provar com violação temporária, desfazer. Registrar | — | [P] | #119 |
| T006 | `app`: `email/client/EmailServiceClientIntegrationTest` contra o `email-service` real do Docker Compose (chave de teste do cliente `jogo-acoes`): (1) sincronizar os templates cria os 5 no `email-service` (`GET /templates` lista `invite`, `registration-link`, `login-link`, `login-link-invite`, `login-link-request`); (2) sincronizar de novo não falha e não muda nada; (3) `PUT` repetido com o mesmo conteúdo devolve o mesmo resultado; (4) `preview` repetido devolve o mesmo resultado; (5) enviar um `EmailRequest` de cada tipo grava `sent_email` com `email_service_id` igual ao `id` do `202` e a mensagem aparece na fila do LocalStack com o template `jogo-acoes__<nome>`; (6) chave inválida → exceção de configuração, sem retry; (7) `email-service` parado → `EmailServiceUnavailableException` dentro do tempo limite. Rodar e ver falhar (cliente inexistente) | — | | #119 |
| T007 | `memory/constitution.md`: seção nova "Fronteira entre módulos e serviços" (ver `plan.md`), com o caso `app`/`email-service` como exemplo; resumo curto no `CLAUDE.md` se couber | — | [P] | #119 |
| T008 | `specs/05-034-.../contracts/feign-client.md` (a partir de `templates/contracts-template.md`): operações do `email-service` que o `app` usa, headers, tempo limite, quais são repetidas e quais não, tradução de cada status HTTP para exceção do `app` | — | [P] | #119 |
| T009 | `app/pom.xml`: `spring-cloud-starter-openfeign` (BOM do Spring Cloud alinhado ao Spring Boot — se não houver versão compatível, parar e reportar); segunda execução do `openapi-generator-maven-plugin` para `docs/openapi-email-service.yaml` com `library: spring-cloud`, pacotes `email.client.api`/`.model`; `@EnableFeignClients` restrito a esse pacote | T006 | | #119 |
| T010 | `app`: configuração `email-service.base-url`/`email-service.api-key`, tempo limite em `spring.cloud.openfeign.client.config.email-service`, `EmailServiceApiKeyInterceptor`; `base-url` por perfil (`docker`, `sandbox`; `staging`/`production` de fora) | T009 | | #119 |
| T011 | `app`: `EmailServiceGateway` (única classe que usa a API gerada; traduz `FeignException` por status; retry só em `GET`/`PUT`/`preview` e só para erro de conexão/`503`) e exceções do `app` | T010 | | #119 |
| T012 | `app`: templates Handlebars em `src/main/resources/email-templates/` (cabeçalho, rodapé e 5 × assunto/corpo), convertidos dos Thymeleaf atuais sem mudar o texto; `EmailTemplateSynchronizer` (upsert `PUT` → `404` → `POST` → `409` → `PUT`) em `ApplicationReadyEvent` | T011 | | #119 |
| T013 | `app`: migration `V8__add_email_service_id_to_sent_email.sql` em `db/migration-jogo-acoes`; `SentEmail`/`SentEmailRecorder` gravam o `id` do `202` | — | [P] | #119 |
| T014 | `app`: `EmailServiceEmailSender` (`email.sender=email-service`) mapeando `EmailRequest` → nome do template + `templateData`; `GlobalExceptionHandler` mapeia `EmailServiceUnavailableException` → `503`; conferir se o envio ocorre dentro da transação de negócio e registrar em `plan.md` | T011, T012, T013 | | #119 |
| T015 | `app`: remover `SqsEmailSender`, `EmailMessage`, `EmailContentRenderer`, `RenderedEmail`, `templates/email/`, `spring-cloud-aws-starter-sqs` e a configuração de fila dos perfis; `email.sender` = `email-service` em `docker`/`staging`/`production`; `QueueLoggingAspect` passa a interceptar o `EmailServiceGateway` (sem logar a chave nem o corpo); remover `SqsEmailSenderTest`/`SqsEmailSenderDockerIntegrationTest` e ajustar `QueueLoggingAspectIntegrationTest`. T001 fica verde | T014 | | #119 |
| T016 | `docker-compose.yml`: healthcheck no `email-service`; `app` com `depends_on: email-service: condition: service_healthy` e `EMAIL_SERVICE_URL`; atualizar o comentário do `EMAIL_SERVICE_API_KEY` ("Nothing in app reads it yet"); `docker compose config -q` | T010 | [P] | #119 |
| T017 | `docker compose down -v` + subir `db`, `db-email-service`, `localstack`, `email-service`; `SPRING_PROFILES_ACTIVE=docker mvn -pl app -am verify` e `mvn -pl email-service -am verify` — T001 a T006 verdes, suítes Cucumber verdes | T015, T016, T003, T004, T005 | | #119 |
| T018 | Conferir os 5 e-mails no SES Viewer do LocalStack com o fluxo real (convite, link de cadastro, 3 variações de login) e comparar com os renderizados pelo Thymeleaf antes da mudança. Registrar aqui | T017 | | #119 |
| T019 | Documentação: `README.md` (o `app` depende do `email-service` para enviar e-mail; ordem de subida); `docs/context/iteracao-5.md` (seção 5 e "Decisões em aberto": `SqsEmailSender` substituído, decidido); diagrama de componentes/sequência afetado em `docs/diagrams/`, validando a renderização do Mermaid (constitution, "Diagramas Mermaid") | T017 | [P] | #119 |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em qualquer
  ordem/em paralelo.
- Cada linha vira um item de checklist na Issue #119 — label `iteration-5`, além do label de
  tipo (`feat`).
- T006, T017 e T018 dependem de Docker. Se não estiver disponível, registrar explicitamente o que
  não pôde ser verificado (mesmo padrão de `specs/05-028-testes-exigem-docker-real/tasks.md`).
- Marcar o ID como concluído (`~~T001~~` ou checkbox `[x]`) quando o commit que a resolve for
  mesclado — não deixar a tabela dessincronizada do estado real.

## Resultado da verificação

A preencher durante a implementação (T001–T005 antes das correções; T017, T018 no fim).
