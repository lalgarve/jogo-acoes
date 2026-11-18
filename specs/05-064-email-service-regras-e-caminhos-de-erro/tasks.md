# Tasks: email-service, regras de arquitetura e caminhos de erro que faltam

Quebra `plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`).

Issue-épico: [#136](https://github.com/lalgarve/jogo-acoes/issues/136). As tarefas abaixo viram
checklist dela.

**Ordem:** os testes vêm antes do código, num commit `test: ... (spec 05-064)` próprio, rodados
contra o código como está e falhando pelo motivo esperado (constitution, "Fase vermelha: testes
antes do código, com prova"). Se um teste não roda contra o código atual, ou falha por outro
motivo, **parar e perguntar** à Leila, sem contornar. Nenhuma task começa sem pedido explícito
(constitution, "Nunca começar a implementar sem pedido explícito").

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| T001 | Medir, sem mudar código, o tempo de uma chamada normal ao LocalStack: `CreateTemplate` e `TestRenderTemplate` no SES e `GetQueueUrl` e `SendMessage` no SQS, dez vezes cada. Registrar a mediana e o máximo abaixo, ao lado dos valores decididos (3 s e 10 s). Se o máximo chegar perto de um deles, **parar e perguntar** | — | [P] | #136 |
| T002 | `BlackHoleServer` no `testsupport` e `SesTimeoutIntegrationTest`: com `email.ses.api-call-timeout=1s` e o endpoint do SES apontando para o `BlackHoleServer`, `POST /templates` devolve `503` com corpo `Error` em menos de 5 s, e nada fica gravado. Rodar contra o código atual e confirmar que falha na asserção do tempo (hoje a propriedade não existe e a resposta demora dezenas de segundos). Commit `test:` | — | [P] | #136 |
| T003 | `SqsTimeoutIntegrationTest`: com `email.publisher.sqs-timeout=1s` e o endpoint do SQS apontando para o `BlackHoleServer`, um envio aceito fica `PENDING` com uma tentativa `QUEUE_UNAVAILABLE` registrada em menos de 5 s. Rodar contra o código atual e confirmar que falha na asserção do tempo (hoje o limite é fixo em 10 s). Commit `test:` | T002 (usa o `BlackHoleServer`) | | #136 |
| T004 | Teste de contrato, direção "status devolvido e não declarado": `swagger-request-validator-restassured` com escopo `test` (versão mais recente no Maven Central, registrada abaixo), `ContractStatusFilter` e o `LauncherSessionListener` que o registra. Rodar a suíte inteira do `email-service` e registrar abaixo **cada** divergência (operação, status, teste). Esperado: pelo menos o `400` de `POST /templates` no cenário de nome em branco. Confirmar também que nenhum teste falha por outro motivo (caminho fora do contrato, Jackson). Commit `test:`. O `400` já está decidido (entra no contrato); qualquer outra divergência faz a sessão **parar** e levar a lista à Leila (T008) | — | [P] | #136 |
| T005 | Inventário, sem mudar código (requisito 3 da `spec.md`): (a) para cada uma das 13 classes de exceção, o teste que a faz ser lançada pelo caminho real; (b) para cada ramo de erro (`@ExceptionHandler` do `ApiExceptionHandler`; `catch` do `SesTemplateClient`, do `EmailQueuePublisher`, do `DeadLetterConsumer`, do `SesTemplateReconciler`, do `TemplateService`, do `DisposableDomainImportService`, do `DisposableDomainImportScheduler`, dos tasklets e do processador de `disposable`, do `RecipientDomainVerifier` e do `DnsMailDomainLookup`), o teste que o percorre; (c) para cada status de erro declarado em `docs/openapi-email-service.yaml`, o teste que o produz. Tabelas abaixo; o que não tiver teste fica marcado | — | [P] | #136 |
| T006 | Testes que faltam segundo a T005. Cada teste novo roda contra o código atual: se já passa (protege um comportamento que existe), registrar abaixo com o porquê; se falha, é defeito, e a sessão **para e pergunta** antes de corrigir. O que for difícil demais de provocar é justificado no `plan.md`, seção "Critério de aceite: exceções". Commit `test:` | T005 | | #136 |
| T007 | `ArchitectureTest`: `repositoriesAreOnlyUsedInsideTheirOwnModule` e `onlyTheAwsIntegrationClassesUseAws` (`plan.md`, decisões). As duas nascem verdes; registrar abaixo. Prova de cada uma: plantar uma violação temporária (roteiro da T015, passos 2 e 3), ver a mensagem, reverter sem commitar e registrar a mensagem | — | [P] | #136 |
| T008 | Só se a T004 achar divergência além do `400` de `POST /templates`, ou a T001 medir um tempo perto dos limites: levar à Leila e registrar a decisão num commit `decision:` no `plan.md`. Os valores dos tempos limite, o `400` e a direção "declarado e não produzido" (pelo inventário da T005) já foram decididos em 2026-11-18 | T001, T004 | | #136 |
| T009 | `SesClientConfig` (`SesClientCustomizer` com `apiCallTimeout`) e `email.ses.api-call-timeout` no `application.yml`, com comentário. `SesClientConfig` entra na lista da regra de AWS. Confirmar T002 verde, `SesUnavailableIntegrationTest` e `register_templates.feature` verdes | T002, T007, T008 | | #136 |
| T010 | `EmailQueuePublisher`: `email.publisher.sqs-timeout` no `application.yml`, com comentário; `apiCallTimeout` por chamada no `GetQueueUrlRequest` e no `SendMessageRequest`; `Future.get` com o mesmo valor mais 1 s; `SQS_TIMEOUT_SECONDS` sai. Confirmar T003 verde, `send_email.feature`, `EmailQueuePublisherConcurrencyTest` e os testes do `DeadLetterConsumer` verdes (o *long polling* do listener não pode ser afetado) | T003, T008 | | #136 |
| T011 | Ajustes de contrato ou de código decididos na T008. Confirmar T004 verde, sem nenhuma divergência | T004, T008 | | #136 |
| T012 | Constitution, "Fronteira entre módulos e serviços", parágrafo "Como é verificado": no `email-service`, repository só dentro do próprio módulo e AWS só nas classes de integração (spec 05-064) | T007 | | #136 |
| T013 | Critério de aceite de exceções (`memory/constitution.md`, "Critério de aceite: exceções e caminhos de erro exercitados por teste"): listar as classes de exceção criadas (esperado: nenhuma) e os ramos de erro alterados (tempo limite do SES no `catch (SdkClientException)` do `SesTemplateClient.call`; tempo limite do SQS nos `catch` do `EmailQueuePublisher`); apontar o teste de cada um. Juntar com o inventário da T005/T006. O que não tiver teste é justificado no `plan.md` e avisado no chat ao abrir a PR | T006, T009, T010, T011 | | #136 |
| T014 | Rodar `mvn -pl email-service -am verify` com a infraestrutura do README ("Suítes do `email-service` e da `email-lambda`") e confirmar verde, e `mvn -pl app -am verify` (o `app` usa o contrato do `email-service`). Se o container da sessão não conseguir rodar algum teste por limitação do ambiente, registrar qual e fechar pelo CI da PR para `development` | T009–T013 | | #136 |
| T015 | Verificação manual (Leila): rodar o roteiro "Verificação manual (T015)" abaixo e conferir os resultados esperados | T014 | | #136 |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em qualquer
  ordem/em paralelo.
- A Issue #136 leva os labels `iteration-5` e `test`.
- Marcar o ID como concluído (`~~T001~~` ou checkbox `[x]`) na mesma PR que resolve a task,
  junto com o registro da verificação (`memory/constitution.md`, "Rastreamento de trabalho via
  Issues"). Nunca depois do merge.

## Verificação manual (T015)

Na raiz do repositório, no WSL, no branch da implementação. Os passos 2 a 4 mexem no código de
propósito, e cada um termina revertendo a mudança; o `git status` do fim confirma.

### 1. Ambiente e suíte do `email-service`

O container do `email-service` fica **fora do ar** durante a suíte (README, "Suítes do
`email-service` e da `email-lambda`").

```bash
docker compose down -v
docker compose up -d --wait db-email-service localstack dns config-server
./scripts/test-api-key.sh restore
mvn -pl email-service -am verify 2>&1 | grep -E "Tests run: [0-9]+, Fail|BUILD" | tail -3
```

Esperado: a última linha `Tests run:` com `Failures: 0, Errors: 0`, e `BUILD SUCCESS`.

### 2. Plantar um repository usado fora do próprio módulo

```bash
cat > email-service/src/main/java/dev/leilaalgarve/jogoacoes/emailservice/domain/PlantedRepositoryBreak.java <<'JAVA'
package dev.leilaalgarve.jogoacoes.emailservice.domain;

class PlantedRepositoryBreak {
    dev.leilaalgarve.jogoacoes.emailservice.template.EmailTemplateRepository templates;
}
JAVA
mvn -pl email-service -am test -Dtest=ArchitectureTest -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | grep -E "PlantedRepositoryBreak" | sort -u
rm email-service/src/main/java/dev/leilaalgarve/jogoacoes/emailservice/domain/PlantedRepositoryBreak.java
```

Esperado: pelo menos uma linha citando `PlantedRepositoryBreak.templates` e
`EmailTemplateRepository`, vinda de `repositoriesAreOnlyUsedInsideTheirOwnModule`.

### 3. Plantar uma classe que usa AWS fora da lista

```bash
cat > email-service/src/main/java/dev/leilaalgarve/jogoacoes/emailservice/domain/PlantedAwsBreak.java <<'JAVA'
package dev.leilaalgarve.jogoacoes.emailservice.domain;

class PlantedAwsBreak {
    software.amazon.awssdk.services.ses.SesClient ses;
}
JAVA
mvn -pl email-service -am test -Dtest=ArchitectureTest -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | grep -E "PlantedAwsBreak" | sort -u
rm email-service/src/main/java/dev/leilaalgarve/jogoacoes/emailservice/domain/PlantedAwsBreak.java
```

Esperado: pelo menos uma linha citando `PlantedAwsBreak.ses` e `SesClient`, vinda de
`onlyTheAwsIntegrationClassesUseAws`.

### 4. Tirar do contrato um status que o código devolve

Remove a resposta `404` de `updateTemplate` (`PUT /templates/{name}`) do contrato e roda só o
cenário "Client tries to update a template that does not exist" (`register_templates.feature`).

```bash
cp docs/openapi-email-service.yaml /tmp/openapi-email-service.yaml.bak
python3 - <<'PY'
import re
p = 'docs/openapi-email-service.yaml'
s = open(p, encoding='utf-8').read()
start = s.index('operationId: updateTemplate')
block_end = s.index('operationId:', start + 1)
block = s[start:block_end]
new_block = re.sub(r"\n        '404':\n(?:          .*\n)+", "\n", block, count=1)
assert new_block != block, '404 of updateTemplate not found'
open(p, 'w', encoding='utf-8').write(s[:start] + new_block + s[block_end:])
PY
git diff --stat docs/openapi-email-service.yaml
mvn -pl email-service -am test -Dtest=RunCucumberTest -Dcucumber.filter.name="Client tries to update a template that does not exist" -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | grep -E "status.unknown|404|BUILD" | sort -u | head -5
cp /tmp/openapi-email-service.yaml.bak docs/openapi-email-service.yaml
git status --short
```

Esperado:
- `git diff --stat` mostra o arquivo do contrato com linhas removidas;
- a saída do `mvn` traz a mensagem do validador para o status `404` não declarado e
  `BUILD FAILURE`;
- o `git status --short` do fim volta vazio.

Conferidos em 2026-11-17: a classe que roda o Cucumber é `RunCucumberTest`, e o bloco `'404'` de
`updateTemplate` tem a indentação que o script espera. Falta confirmar na T004 que o
`-Dcucumber.filter.name` chega ao Cucumber pelo Surefire; se não chegar, o passo muda aqui.

### 5. Derrubar

```bash
docker compose down -v
git status --short
```

Esperado: `git status --short` vazio.

## Registro

Vazio até a implementação começar.
