# Plan: Testes que dependem de container — marcação explícita e perfil padrão `docker`

Traduz `spec.md` em decisões técnicas. Valida contra `memory/constitution.md` ("Nomenclatura de
ambientes" e "Testes que dependem de infraestrutura em container").

## Contexto técnico

Testes que dependem de container hoje (conferido no `master` em 2026-10-05):

| Módulo | Teste | Depende de | Como decide hoje |
|---|---|---|---|
| `app` | `email/SqsEmailSenderDockerIntegrationTest` | LocalStack (SQS) | `@BeforeAll` + `assumeTrue(reachable(5432) && reachable(4566))` |
| `app` | `common/logging/QueueLoggingAspectIntegrationTest` | LocalStack (SQS) | igual ao de cima |
| `email-service` | `RunCucumberTest` → `register_templates.feature` | LocalStack (SES) | não decide: quebra sem LocalStack |
| `email-lambda` | `EmailSendHandlerTest.sendsAWellFormedMessageWithoutError` | LocalStack do Dev Services (SES) | `catch (SdkClientException)` + `assumeTrue(false, ...)` |

Os demais testes de `app` e `email-service` com contexto Spring (`SchemaLayoutTest`,
`ApiKeyIntegrationVerificationTest` etc.) só precisam do Postgres, que existe nos dois perfis.
O segundo teste de `EmailSendHandlerTest` (mensagem malformada) não chama o SES.

Outros fatos que pesam nas decisões:

- `src/test/resources/application.yml` substitui o `application.yml` principal no classpath dos
  testes (os comentários desses arquivos registram isso). Por isso `spring.profiles.default`
  precisa estar nos dois.
- `@EnabledIfEnvironmentVariable` (e as outras condições do Jupiter) não têm efeito sobre os
  cenários Cucumber, que rodam em outro motor do JUnit Platform. A spec 05-028 confirmou isso
  empiricamente (`plan.md`, linha "E quando o teste depende de LocalStack").
- `RunCucumberTest` fixa `cucumber.filter.tags = "not @requires-real-ses"` num
  `@ConfigurationParameter`, que tem precedência sobre system properties e sobre
  `junit-platform.properties` (ordem de precedência do JUnit Platform; não testado aqui).
- O `email-lambda` é Quarkus: não tem perfil `sandbox`/`docker`, e o Dev Services sobe o próprio
  LocalStack via Testcontainers quando há Docker.

## Decisões de arquitetura

| Pergunta | Decisão | Status | Raciocínio |
|---|---|---|---|
| Qual sinal decide se um teste marcado roda? | A variável `SPRING_PROFILES_ACTIVE`: contendo `sandbox`, pula; qualquer outro valor ou ausente, roda | resolvida | É o mesmo sinal que escolhe o perfil da aplicação. Ausente = perfil padrão `docker` (C1), então roda. |
| Marcação nos testes Jupiter (`app`, `email-service`, `email-lambda`) | Anotação composta `@RequiresContainers` em `src/test` de cada módulo, combinando `@Tag("requires-containers")` e `@DisabledIfEnvironmentVariable(named = "SPRING_PROFILES_ACTIVE", matches = ".*\\bsandbox\\b.*", disabledReason = "...")`. Vale em classe ou método | proposta | Visível na leitura, aparece como pulado com motivo no `sandbox`, não olha o ambiente. A `@Tag` deixa o grupo filtrável no Surefire se precisar. Uma cópia por módulo porque os módulos não compartilham código de teste. |
| Marcação na suíte Cucumber do `email-service` | Tag `@requires-containers` no topo da `register_templates.feature` (propaga para todos os cenários) | proposta | Mesmo nome da `@Tag` do Jupiter. A Cucumber expõe as tags dos cenários como tags do JUnit Platform. |
| Como o Cucumber pula a tag no `sandbox` | **Em aberto**, decidida pela T003. Candidatas: (a) hook `@Before("@requires-containers")` que aborta o cenário (`TestAbortedException`) quando o perfil é `sandbox`; (b) a `@RequiresContainers` na classe `RunCucumberTest`, se o motor de suíte respeitar condições do Jupiter; (c) perfil Maven ativado por `env.SPRING_PROFILES_ACTIVE=sandbox` com `excludedGroups=requires-containers` no Surefire | em aberto | Requisito: aparecer como pulado com motivo (C4) e sem subir nada que precise do LocalStack. (a) mostra os cenários como pulados, mas o `@After` de `TemplateCleanupHooks` chama o SES e precisa respeitar a mesma condição. (b) é a mais simples, se funcionar. (c) exclui em vez de pular: os cenários somem do relatório, o que contraria o constitution. |
| Perfil no `email-lambda` (Quarkus) | Usar a mesma `SPRING_PROFILES_ACTIVE` | proposta | Quem escolhe o ambiente escolhe uma vez para o projeto todo. Criar outra variável só para o Quarkus abriria espaço para os dois módulos discordarem. Alternativa: `QUARKUS_PROFILE=sandbox`. |
| Perfil padrão | `spring.profiles.default: docker` em `app/src/main/resources/application.yml` (hoje `sandbox`), em `app/src/test/resources/application.yml` e em `email-service/src/test/resources/application.yml` (o principal do `email-service` já é `docker`) | resolvida | Constitution, "Nomenclatura de ambientes". Os testes do `app` já rodam com `docker` no CI, então o padrão novo só alinha a execução à mão com o CI. |
| Como impedir a volta da checagem em tempo de execução | Regra ArchUnit em `app` e `email-service` (já têm ArchUnit): nenhuma classe de teste chama `org.junit.jupiter.api.Assumptions`. No `email-lambda`, que não tem ArchUnit, a mesma regra com ArchUnit adicionado em escopo `test` | proposta | Proibir `Assumptions` inteiro é mais simples que tentar reconhecer "checagem de rede". Hoje os únicos usos são os três a remover. Se um dia houver uso legítimo, a regra ganha exceção nomeada. |
| Porta do Postgres do `email-service` no sandbox | **Em aberto**, decidida pela T012 | em aberto | `application-sandbox.yml` aponta para 5433; a T011 da 05-032 não achou Postgres nativo na 5433 e usou a 5432. Precisa ser confirmado no próprio ambiente sandbox. |

## Estrutura de módulos/pacotes

```
app/src/main/resources/application.yml           profiles.default: sandbox -> docker
app/src/test/resources/application.yml           + profiles.default: docker
app/src/test/java/.../common/testsupport/RequiresContainers.java   (nova)
app/src/test/java/.../email/SqsEmailSenderDockerIntegrationTest.java    assumeTrue -> @RequiresContainers
app/src/test/java/.../common/logging/QueueLoggingAspectIntegrationTest.java  idem
app/src/test/java/.../common/ArchitectureTest.java  + regra "sem Assumptions"
email-service/src/test/resources/application.yml + profiles.default: docker
email-service/src/test/resources/features/register_templates.feature  + @requires-containers
email-service/src/test/java/.../common/testsupport/RequiresContainers.java  (nova)
email-service/src/test/java/...                  mecanismo de skip do Cucumber (T003)
email-service/src/test/java/.../common/ArchitectureTest.java  + regra "sem Assumptions"
email-lambda/pom.xml                             + archunit-junit5 (test)
email-lambda/src/test/java/.../RequiresContainers.java      (nova)
email-lambda/src/test/java/.../EmailSendHandlerTest.java    catch+assumeTrue -> @RequiresContainers no método
email-lambda/src/test/java/.../ArchitectureTest.java        (nova, regra "sem Assumptions")
README.md                                        "Como rodar os testes" + perfil padrão por serviço
```

## Riscos e trade-offs

- **Rodar à mão sem os containers passa a falhar em vez de pular** os testes que usam LocalStack.
  É o comportamento pedido (C3): quem não tem Docker escolhe `sandbox` explicitamente.
- **`SPRING_PROFILES_ACTIVE` lida direto pelo JUnit**, não pelo Spring. Se alguém escolher o
  perfil por outro meio (ex.: `-Dspring.profiles.active=sandbox`), a marcação não percebe. Aceito:
  o README e o constitution usam a variável de ambiente, e o CI também.
- **Três cópias da anotação**, uma por módulo. Aceito para não criar um módulo de suporte de teste
  compartilhado só por isso.
