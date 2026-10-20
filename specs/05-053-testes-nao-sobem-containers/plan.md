# Plan: Os testes não sobem containers

Traduz `spec.md` em decisões técnicas. Valida contra `memory/constitution.md`.

## Contexto técnico

- `email-lambda/pom.xml`: `quarkus-amazon-ses` (e, com a spec 05-046, o cliente SQS para a DLQ).
  O comentário do `pom.xml` registra que o Dev Services "auto-starts LocalStack via
  Testcontainers for tests when no endpoint-override is configured".
- `email-lambda/src/test/resources/application.properties` não configura endpoint de SES nem de
  SQS; `EmailSendHandlerTest` lê o `quarkus.ses.endpoint-override` que o Dev Services preenche.
- `docker-compose.yml`: o `localstack` publica `4566`, com SES, SQS, S3 e Lambda, e verifica o
  remetente `no-reply@jogo-acoes.example` (`02-verify-ses-sender.sh`).
- `.github/workflows/ci.yml`: "Build and test email-lambda (JVM mode)" roda depois de "Stop
  Postgres, LocalStack, config-server and email-service".
- `email-service/src/main/resources/db/migration-email-service/V6__create_disposable_domain_tables.sql`
  termina com o `INSERT` da lista do GitHub; `DisposableDomainCleanupHooks` apaga todos os
  repositórios antes dos cenários.

## Decisões de arquitetura

| Pergunta | Decisão | Status | Raciocínio |
|---|---|---|---|
| Como a `email-lambda` acha o LocalStack nos testes | Em `src/test/resources/application.properties`: `quarkus.devservices.enabled=false`, `quarkus.ses.endpoint-override=http://localhost:4566`, região e credenciais estáticas de teste (as mesmas do `03-deploy-email-lambda.sh`); idem para o SQS quando a 05-046 entrar | adotada | O mesmo LocalStack que `app` e `email-service` usam. Sem Dev Services, sem LocalStack, a suíte falha com erro de conexão. |
| Remetente nos testes | O já verificado pelo init do LocalStack (`no-reply@jogo-acoes.example`), ou o teste verifica o próprio remetente no SES do LocalStack antes de enviar | adotada | O LocalStack do Compose só aceita remetente verificado, como o SES de verdade. Na implementação: cada teste já verificava o próprio remetente e criava o próprio template, e a DLQ dos testes já era própria (`email-lambda-test-dlq`); nada mudou. |
| Regra contra Testcontainers | ArchUnit em cada módulo (`app`, `email-service`, `email-lambda`, `config-server`): nenhuma classe de teste depende de `org.testcontainers`. Na `email-lambda`, um teste confere que `quarkus.devservices.enabled` é `false` no perfil de teste | adotada | O Dev Services não aparece nas classes de teste (é configuração), por isso a segunda conferência. |
| CI | "Build and test email-lambda" passa para antes do passo que derruba os containers, com o LocalStack do Compose de pé | adotada | A suíte deixa de trazer o próprio LocalStack. |
| Repositório inicial das listas | O `INSERT` sai da V6 e vai para uma migration de dados em `db/seed-email-service/`, que só `config-repo/email-service-staging.yml` e `-production.yml` acrescentam a `spring.flyway.locations`; mesma tabela de histórico do Flyway. `DisposableDomainCleanupHooks` deixa de precisar apagar o repositório do GitHub | adotada (a decisão de não criar é da Leila, 2026-10-20) | O `docker` e os testes nunca têm o repositório, sem precisar apagar; configuração por ambiente pelo `config-repo`, como o resto. Versão `V6_1`, logo depois da tabela, sem colidir com as migrations inteiras. Em `staging`/`production` o Flyway está desligado (`spring.flyway.enabled: false`, a equipe do banco aplica as migrations à mão): `spring.flyway.locations` fica no `config-repo` como a lista de pastas que essa equipe aplica, e o comentário do arquivo diz isso. |

## Estrutura de módulos/pacotes

- `email-lambda/src/test/resources/application.properties`, `EmailSendHandlerTest` (comentários
  e, se preciso, o remetente).
- `email-lambda/pom.xml`: comentário sobre o Dev Services.
- `*/src/test/java/.../ArchitectureTest.java`: regra contra Testcontainers.
- `.github/workflows/ci.yml`.
- `email-service/src/main/resources/db/migration-email-service/V6__...sql`,
  `email-service/src/main/resources/db/seed-email-service/` (nova),
  `config-repo/email-service-staging.yml`, `-production.yml`.
- `memory/constitution.md`; notas nas specs 05-028 e 05-048 e no caderno de testes do Serviço de
  E-mail.

## Riscos e trade-offs

- **LocalStack compartilhado:** a suíte da `email-lambda` passa a usar o mesmo LocalStack dos
  outros testes e do ambiente manual. Filas, remetentes e templates próprios dos testes evitam
  colisão.
- **V6 alterada:** quem tem banco antigo precisa de `docker compose down -v`. Aceito em
  pré-produção.
- **Mudança da migration seed em `staging`/`production`:** a primeira subida depois da mudança
  aplica a migration de dados nova; num banco que já tem o repositório (criado pela V6 antiga), o
  `INSERT` precisa não duplicar (`ON CONFLICT (url) DO NOTHING`).
