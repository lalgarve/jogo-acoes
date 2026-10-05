# Spec: Testes que dependem de container — marcação explícita e perfil padrão `docker`

**Status:** rascunho
**Issue:** #<a criar>
**Iteração:** iteration-5

## Resumo

Aplicar em `app`, `email-service` e `email-lambda` a regra de `memory/constitution.md`, seção
"Testes que dependem de infraestrutura em container". Um teste que precisa de infraestrutura que
só existe em container (hoje, o LocalStack) passa a ser marcado explicitamente no código. Quem
decide se ele roda é o perfil, nunca o próprio teste olhando o ambiente: no perfil `docker` ele
roda sempre e falha se o serviço não responder; no perfil `sandbox` ele aparece como pulado, com
o motivo. Junto disso, `docker` passa a ser o perfil padrão de todos os serviços, inclusive nos
testes rodados à mão, como a "Nomenclatura de ambientes" do constitution já define.

## Motivação

Uma conferência do `master` em 2026-10-05 achou três comportamentos diferentes, nenhum de
acordo com a regra:

- **`app`**: `SqsEmailSenderDockerIntegrationTest` e `QueueLoggingAspectIntegrationTest` usam
  `assumeTrue(reachable("localhost", 5432) && reachable("localhost", 4566))` num `@BeforeAll`.
  Se o LocalStack estiver fora do ar no perfil `docker`, inclusive no CI, os testes aparecem
  como pulados em vez de falhar.
- **`email-service`**: a suíte Cucumber (`register_templates.feature`) precisa do LocalStack
  (SES) e não tem marcação nenhuma. No sandbox ela roda e quebra com erro de conexão.
- **`email-lambda`**: `EmailSendHandlerTest.sendsAWellFormedMessageWithoutError` captura
  `SdkClientException` e chama `assumeTrue(false, ...)` quando o Dev Services não consegue
  subir o LocalStack. É a mesma decisão em tempo de execução, escondida num `catch`.

O perfil padrão também diverge: `app` usa `sandbox` e `email-service` usa `docker`. Nos testes,
o `src/test/resources/application.yml` substitui o principal no classpath, então nenhum perfil
fica ativo sem `SPRING_PROFILES_ACTIVE`. O CI define `SPRING_PROFILES_ACTIVE=docker`, mas uma
execução à mão sem essa variável roda num estado que o CI nunca testa.

A spec 05-028 tentou resolver parte disso (T005/T006/T011) com um filtro de tags ativado por
variável de ambiente. Essas tasks foram substituídas por esta spec.

## Cenários (comportamento esperado)

Esta spec é infraestrutura de teste, não comportamento de produto. Não há `.feature` novo.

### C1 — Perfil padrão é `docker`

**Dado** que nenhum perfil foi escolhido (`SPRING_PROFILES_ACTIVE` não definido)
**Quando** `mvn -pl app -am verify` ou `mvn -pl email-service -am verify` roda
**Então** o perfil ativo, na aplicação e nos testes, é `docker`
**E** o resultado é o mesmo do CI.

### C2 — Perfil `docker` com a infraestrutura de pé

**Dado** que `docker compose up -d --wait db db-email-service localstack` rodou antes
**Quando** as suítes de `app`, `email-service` e `email-lambda` rodam no perfil `docker`
**Então** todos os testes marcados como dependentes de container rodam
**E** nenhum aparece como pulado por falta de infraestrutura.

### C3 — Perfil `docker` sem o LocalStack

**Dado** que o perfil é `docker` e o LocalStack não está de pé
**Quando** um teste marcado como dependente de container roda
**Então** ele falha com erro de conexão
**E** não aparece como pulado.

### C4 — Perfil `sandbox`

**Dado** que o perfil é `sandbox` (`SPRING_PROFILES_ACTIVE=sandbox`) e não há Docker
**Quando** as suítes rodam
**Então** os testes marcados como dependentes de container aparecem como pulados no
relatório, com o motivo
**E** os testes que só precisam do banco (Postgres nativo) rodam normalmente
**E** o build termina com sucesso se nada mais falhar.

### C5 — Nenhuma decisão em tempo de execução

**Dado** o código de teste de qualquer módulo
**Quando** um teste de arquitetura verifica o uso de `Assumptions` do JUnit
**Então** nenhum teste decide se roda checando se um serviço responde
**E** o teste de arquitetura falha se alguém reintroduzir esse padrão.

## Requisitos funcionais

- **Marcação explícita**: cada teste (classe, método ou cenário) que depende do LocalStack é
  marcado de forma visível no próprio código. Uma suíte que depende inteira dele é marcada
  inteira.
- **Decisão pelo perfil**: o único sinal que decide se um teste marcado roda é o perfil ativo.
  `sandbox` pula; qualquer outro valor, inclusive nenhum (padrão `docker`), roda.
- **Pulado com motivo**: no `sandbox`, o teste marcado aparece como pulado (não some do
  relatório) e o motivo diz que ele precisa de container.
- **Sem checagem em tempo de execução**: os `assumeTrue(reachable(...))` de `app` e o
  `assumeTrue(false, ...)` de `email-lambda` saem. Um teste de arquitetura impede que voltem.
- **Perfil padrão `docker`**: `spring.profiles.default: docker` no `application.yml` principal e
  no de teste de `app` e de `email-service`.
- **Testes que só usam o banco não são marcados**: o Postgres existe nos dois perfis (container
  no `docker`, nativo no `sandbox`).
- **Documentação**: o `README.md` explica como rodar os testes (subir os containers antes, perfil
  padrão, como usar o `sandbox`) e o perfil padrão de cada serviço.

## Requisitos não-funcionais

- O CI continua igual: perfil `docker`, todos os testes marcados incluídos.
- A marcação tem o mesmo nome e o mesmo motivo nos três módulos, para ser reconhecível na leitura
  e no relatório.
- Nenhum código de teste entra em módulo de produção (constitution, "Código de teste/dev nunca
  dentro da aplicação"): a anotação de marcação fica em `src/test` de cada módulo.

## Fora de escopo

- O step de CI do `email-service` (spec 05-028, T012).
- Os cenários `@requires-real-ses` (Issue #104): eles dependem do SES real, não de container, e
  continuam fora da execução padrão como hoje.
- Trocar o Dev Services do `email-lambda` pelo LocalStack do Compose.
- Provisionar o Postgres nativo do sandbox.

## Decisões em aberto

Ver `plan.md`. As principais são o mecanismo de skip da suíte Cucumber, como o `email-lambda`
(Quarkus) lê o perfil e a porta do Postgres do `email-service` no sandbox.
