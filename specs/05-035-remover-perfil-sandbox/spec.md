# Spec: Remover o perfil `sandbox` — testes sempre contra a infraestrutura real

**Status:** implementada (ver `tasks.md`)
**Issue:** [#139](https://github.com/lalgarve/jogo-acoes/issues/139)
**Iteração:** iteration-5

## Resumo

O perfil `sandbox` existia para rodar o projeto num ambiente de desenvolvimento sem Docker (o
ambiente da Claude). Esse ambiente passou a ter Docker, e nenhum outro lugar onde o projeto roda
deixa de ter. Esta spec remove o perfil `sandbox` de `app` e `email-service`, torna `docker` o
perfil padrão (inclusive nos testes) e tira dos testes toda decisão de pular feita em tempo de
execução, conforme `memory/constitution.md`, seções "Nomenclatura de ambientes" e "Testes exigem
a infraestrutura de pé".

## Motivação

Uma conferência do `master` em 2026-10-05 achou o perfil `sandbox` desalinhado com a realidade e
com o resto do projeto:

- **O ambiente que ele descreve não existe mais.** As verificações registradas nas specs 05-030
  (T013) e 05-032 (T009, em 2026-10-04) rodaram `docker compose up` em sessões da Claude. Os
  dois clusters Postgres nativos que a spec 05-028 descreve (portas 5432 e 5433) também não
  aparecem: a 05-032 (T011) não achou nada na 5433, e o `application-sandbox.yml` do
  `email-service` continua apontando para lá.
- **Cada módulo trata a falta de Docker de um jeito**, sempre decidindo em tempo de execução:
  - **`app`**: `SqsEmailSenderDockerIntegrationTest` e `QueueLoggingAspectIntegrationTest` usam
    `assumeTrue(reachable("localhost", 5432) && reachable("localhost", 4566))` num `@BeforeAll`.
    Com o LocalStack fora do ar, inclusive no CI, os testes aparecem como pulados em vez de
    falhar.
  - **`email-lambda`**: `EmailSendHandlerTest.sendsAWellFormedMessageWithoutError` captura
    `SdkClientException` e chama `assumeTrue(false, ...)` quando o Dev Services não sobe o
    LocalStack.
  - **`email-service`**: a suíte Cucumber precisa do LocalStack e simplesmente quebra sem ele.
- **O perfil padrão diverge**: `app` usa `sandbox` e `email-service` usa `docker`. Nos testes, o
  `src/test/resources/application.yml` substitui o principal no classpath, então nenhum perfil
  fica ativo sem `SPRING_PROFILES_ACTIVE`; o CI define `docker`, mas uma execução à mão roda num
  estado que o CI nunca testa.

A spec 05-028 tentou tratar parte disso com um filtro de tags (T005/T006/T011). Essas tasks
foram substituídas por esta spec.

> **Atualização (2026-10-08):** os `assumeTrue` do `app` e do `email-lambda` descritos acima já
> saíram do `master` por outras specs (05-034 e 05-031; ver `tasks.md`, T003 e T004). O resto da
> motivação continua valendo.

## Cenários (comportamento esperado)

Esta spec é infraestrutura de execução e de teste, não comportamento de produto. Não há
`.feature` novo.

### C1 — Perfil padrão é `docker`

**Dado** que nenhum perfil foi escolhido (`SPRING_PROFILES_ACTIVE` não definido)
**Quando** `app` ou `email-service` sobe, ou `mvn -pl <módulo> -am verify` roda
**Então** o perfil ativo, na aplicação e nos testes, é `docker`
**E** o resultado dos testes é o mesmo do CI.

### C2 — Infraestrutura de pé

**Dado** que `docker compose up -d --wait db db-email-service localstack` rodou antes
**Quando** as suítes de `app`, `email-service` e `email-lambda` rodam
**Então** todos os testes rodam e nenhum aparece como pulado por falta de infraestrutura.

### C3 — Infraestrutura fora do ar

**Dado** que o LocalStack (ou o Postgres) não está de pé
**Quando** um teste que depende dele roda
**Então** ele falha com erro de conexão
**E** não aparece como pulado.

### C4 — O perfil `sandbox` não existe mais

**Dado** o repositório depois desta spec
**Quando** alguém procura por `application-sandbox.yml` ou tenta `SPRING_PROFILES_ACTIVE=sandbox`
**Então** não há arquivo de perfil `sandbox` em nenhum módulo
**E** a documentação ativa (README, scripts, comentários de configuração) não menciona o perfil.

### C5 — Nenhuma decisão em tempo de execução

**Dado** o código de teste de `app`, `email-service` e `email-lambda`
**Quando** o teste de arquitetura verifica o uso de `Assumptions` do JUnit
**Então** nenhuma classe de teste usa `Assumptions`
**E** o teste de arquitetura falha se alguém reintroduzir esse padrão.

## Requisitos funcionais

- Apagar `app/src/main/resources/application-sandbox.yml` e
  `email-service/src/main/resources/application-sandbox.yml`.
- `spring.profiles.default: docker` em `app/src/main/resources/application.yml` (hoje `sandbox`),
  `app/src/test/resources/application.yml` e `email-service/src/test/resources/application.yml`
  (o principal do `email-service` já é `docker`).
- Remover os `assumeTrue(reachable(...))` de `app` e o `catch` + `assumeTrue(false, ...)` de
  `email-lambda`: sem infraestrutura, esses testes falham. Já feito fora desta spec (ver
  `tasks.md`, T003 e T004).
- Regra ArchUnit nos três módulos: nenhuma classe de teste usa
  `org.junit.jupiter.api.Assumptions`.
- Remover as menções ao perfil `sandbox` da documentação e da configuração ativas (lista em
  `plan.md`). Specs já implementadas e diários de iteração ficam como estão, porque são
  histórico. Menções ao "modo sandbox" do Amazon SES não têm relação com o perfil e ficam.
- `README.md`: seção "Como rodar os testes" (subir `db`, `db-email-service` e `localstack` antes;
  `docker` é o perfil padrão) e tabela "Ambientes" sem o `sandbox`.

## Requisitos não-funcionais

- O CI continua igual: perfil `docker`, com todos os testes.
- Nenhuma mudança de comportamento em `staging`/`production`.

## Fora de escopo

- O step de CI do `email-service` (spec 05-028, T012).
- Os cenários `@requires-real-ses` (Issue #104): dependem do SES real, não de container, e
  continuam fora da execução padrão.
- Trocar o Dev Services do `email-lambda` pelo LocalStack do Compose.
- Tirar o `StubEmailSender` do código de produção: as suítes Cucumber do `app` continuam usando
  o stub (spec 05-034), e a remoção é uma pendência separada registrada lá.

## Decisões em aberto

Nenhuma. A convivência com a spec 05-034 (PR #120), que mexe nos mesmos arquivos do `app`, está
em `plan.md`.
