# Plan: Remover o perfil `sandbox` — testes sempre contra a infraestrutura real

Traduz `spec.md` em decisões técnicas. Valida contra `memory/constitution.md` ("Nomenclatura de
ambientes" e "Testes exigem a infraestrutura de pé").

## Contexto técnico

Testes que hoje decidem em tempo de execução (conferido no `master` em 2026-10-05):

| Módulo | Teste | Depende de | Como decide hoje |
|---|---|---|---|
| `app` | `email/SqsEmailSenderDockerIntegrationTest` | LocalStack (SQS) | `@BeforeAll` + `assumeTrue(reachable(5432) && reachable(4566))` |
| `app` | `common/logging/QueueLoggingAspectIntegrationTest` | LocalStack (SQS) | igual ao de cima |
| `email-lambda` | `EmailSendHandlerTest.sendsAWellFormedMessageWithoutError` | LocalStack do Dev Services (SES) | `catch (SdkClientException)` + `assumeTrue(false, ...)` |

Conferido de novo em 2026-10-08: nenhum dos três decide mais em tempo de execução. Os dois do
`app` foram apagados no commit `8818c94` (spec 05-034, T015) e o `catch` do `email-lambda` saiu no
commit `ffa11c6` (spec 05-031). A regra ArchUnit continua, para impedir a volta do padrão.

Menções ao perfil `sandbox` na documentação e configuração ativas (as do "modo sandbox" do SES
ficam):

| Arquivo | O quê |
|---|---|
| `app/src/main/resources/application-sandbox.yml`, `email-service/src/main/resources/application-sandbox.yml` | os perfis em si |
| `app/src/main/resources/application.yml` | `profiles.default: sandbox`; comentário do `altcha.secret` ("outside sandbox/docker") |
| `app/src/main/resources/application-staging.yml`, `application-production.yml` | comentário "unlike sandbox" |
| `app/.../email/StubEmailSender.java` | Javadoc: "active in `sandbox` and in tests" |
| `app/.../bootstrap/AdministratorBootstrap.java` | Javadoc cita `application-sandbox.yml` |
| `email-lambda/.../EmailSendHandlerTest.java` | Javadoc sobre rodar "in the sandbox" sem Docker |
| `docker-compose.yml`, `scripts/test-api-key.sh`, `docker/postgres-email-service/test-data/README.md` | "docker/sandbox", "Postgres do sandbox" |
| `README.md` | tabela "Ambientes" (`sandbox` como padrão) e trecho da chave de teste |
| `docs/diagrams/modulos.md`, `docs/diagrams/classes.md` | "`stub` é o padrão (`sandbox`/testes)" |
| `docs/disciplina/*` | lista de perfis e caderno de testes; seguem `docs/disciplina/CLAUDE.md` |

Outros fatos que pesam nas decisões:

- `src/test/resources/application.yml` substitui o `application.yml` principal no classpath dos
  testes, então `spring.profiles.default` precisa estar nos dois.
- Com `docker` como padrão nos testes, `application-docker.yml` passa a valer em toda execução,
  como já acontece no CI (`SPRING_PROFILES_ACTIVE=docker`). Ele liga `email.sender: sqs`.
- O `StubEmailSender` é o `EmailSender` quando `email.sender` não está definido ou vale `stub`
  (`matchIfMissing`). A spec 05-034 (PR #120) decidiu que as suítes Cucumber do `app` continuam
  com `email.sender=stub`, gravando em `sent_email`, e que tirá-lo do código de produção é uma
  pendência separada.
- A spec 05-034 também mexe em pontos que esta toca: define `email-service.base-url` por perfil,
  incluindo `http://localhost:8082` no `sandbox`, e a T015 dela remove o `SqsEmailSender` e a
  configuração de fila do `app`.

## Decisões de arquitetura

| Pergunta | Decisão | Status | Raciocínio |
|---|---|---|---|
| O que substitui o perfil `sandbox`? | Nada. Os ambientes ficam `docker` (padrão), `staging` e `production` | resolvida (2026-10-05) | Nenhum lugar onde o projeto roda deixa de ter Docker (decisão da Leila em 2026-10-05). Constitution, "Nomenclatura de ambientes". |
| Como os testes que dependem do LocalStack se comportam sem ele? | Falham. Nenhuma marcação, nenhum skip | resolvida (2026-10-05) | Constitution, "Testes exigem a infraestrutura de pé". Sem ambiente legítimo sem Docker, pular só esconderia erro. |
| Perfil padrão | `spring.profiles.default: docker` no `application.yml` principal do `app` e no de teste de `app` e de `email-service`, com comentário explicando a repetição | resolvida | Os testes do `app` já rodam assim no CI; o padrão novo só alinha a execução à mão. |
| Como impedir a volta da checagem em tempo de execução | Regra ArchUnit "nenhuma classe de teste usa `org.junit.jupiter.api.Assumptions`" no `ArchitectureTest` de `app` e `email-service`; no `email-lambda`, `archunit-junit5` em escopo `test` e um `ArchitectureTest` novo | proposta | Proibir `Assumptions` inteiro é mais simples que reconhecer "checagem de rede". Os únicos usos hoje são os três a remover. |
| O que fazer com as specs implementadas e os diários que citam o `sandbox` | Ficam como estão | resolvida | São registro do que foi decidido na época. A spec 05-028 ganha só uma nota apontando para esta. |
| `docs/disciplina/*` | Revisar seguindo `docs/disciplina/CLAUDE.md`; a lista de perfis continua atendendo "ao menos dois ambientes" (`docker`, `staging`, `production`) | proposta | Documento de entrega da disciplina tem regras próprias de edição. |
| `StubEmailSender` | Fica. Só o Javadoc muda: deixa de citar o `sandbox` e passa a citar as suítes Cucumber do `app` (`email.sender=stub`) | resolvida | Alinhado com a spec 05-034, que mantém o stub nas suítes Cucumber do `app`. Tirá-lo do código de produção continua pendência separada, registrada lá. |
| Convivência com a spec 05-034 (PR #120) | Quem for mesclado depois ajusta o outro: se a 05-034 entrar antes, a T003 desta spec só mexe no que sobrar dos testes com `assumeTrue` (a T015 dela apaga o `SqsEmailSenderDockerIntegrationTest`) e a T006 remove o `email-service.base-url` do `application-sandbox.yml` junto com o arquivo; se esta entrar antes, a 05-034 deixa de definir a URL para o `sandbox` | proposta | As duas specs tocam os mesmos arquivos de configuração e testes do `app`. |

## Estrutura de módulos/pacotes

```
app/src/main/resources/application-sandbox.yml          (apagado)
app/src/main/resources/application.yml                  profiles.default: sandbox -> docker; comentário
app/src/main/resources/application-staging.yml           comentário
app/src/main/resources/application-production.yml        comentário
app/src/test/resources/application.yml                   + profiles.default: docker
app/src/main/java/.../email/StubEmailSender.java         Javadoc
app/src/main/java/.../bootstrap/AdministratorBootstrap.java  Javadoc
app/src/test/java/.../common/ArchitectureTest.java       + regra "sem Assumptions"
email-service/src/main/resources/application-sandbox.yml (apagado)
email-service/src/test/resources/application.yml         + profiles.default: docker
email-service/src/test/java/.../common/ArchitectureTest.java  + regra "sem Assumptions"
email-lambda/pom.xml                                     + archunit-junit5 (test)
email-lambda/src/test/java/.../ArchitectureTest.java     (novo, regra "sem Assumptions")
docker-compose.yml, scripts/test-api-key.sh, docker/postgres-email-service/test-data/README.md  textos
README.md                                                "Como rodar os testes"; tabela "Ambientes"
docs/diagrams/modulos.md, docs/diagrams/classes.md       textos
docs/disciplina/*                                        revisão (ver decisão acima)
```

## Riscos e trade-offs

- **Rodar os testes à mão sem os containers passa a falhar** nos testes que usam o LocalStack.
  É o comportamento pedido (C3); o README diz o que subir antes.
- **Volta a existir um ambiente sem Docker?** Seria preciso reintroduzir um perfil e decidir de
  novo como os testes se comportam ali. O constitution registra que isso só se faz quando houver
  um lugar real que precise.
