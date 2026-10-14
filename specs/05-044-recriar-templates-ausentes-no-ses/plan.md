# Plan: Recriar no SES os templates que só existem no banco do email-service

Traduz `spec.md` em decisões técnicas. Valida contra `memory/constitution.md`.

## Contexto técnico

Tudo no pacote `template` do `email-service`:

- `TemplateService` orquestra: confere a linha no banco, chama o `SesTemplateClient`, grava.
- `SesTemplateClient` é a única classe que fala com o SES (`SesClient` do SDK v2, API v1) e
  traduz as exceções do SDK para as do pacote. Hoje:
  - `createTemplate`: `AlreadyExistsException` (e qualquer `SesException`) →
    `TemplateRejectedBySesException` (422);
  - `updateTemplate`: `TemplateDoesNotExistException` → `TemplateNotFoundException` (404), com o
    comentário "defensive fallback" que descreve exatamente o caso desta spec;
  - `testRenderTemplate`: `TemplateDoesNotExistException` → `TemplateNotFoundException` (404).
- `EmailTemplateRepository` só tem consultas por cliente; a reconciliação precisa de todas as
  linhas (`findAll()` do `JpaRepository`, já disponível).
- Testes: Cucumber (`register_templates.feature`) contra PostgreSQL real (5433) e LocalStack, com
  `TemplateCleanupHooks` apagando banco e SES depois de cada cenário. O `SesClient` está
  disponível nos steps (o hook já o usa), então "o SES perdeu o template" se provoca com
  `DeleteTemplate` direto.
- No Compose, o healthcheck do `email-service` só testa a porta 8080, e o `app` espera esse
  healthcheck para sincronizar os templates.

## Decisões de arquitetura

| Pergunta | Decisão | Status | Raciocínio |
|---|---|---|---|
| Onde mora a recriação | `SesTemplateClient` ganha `putTemplate(sesName, subject, body)`: tenta `CreateTemplate`; com `AlreadyExistsException`, faz `UpdateTemplate`. `updateTemplate` passa a tentar `UpdateTemplate` e, com `TemplateDoesNotExistException`, fazer `CreateTemplate`. As duas variações deixam o SES com o conteúdo pedido, exista ou não o template. | proposta | O SES v1 não tem "upsert". Mantém o SDK dentro do `SesTemplateClient` (regra que a Issue #136 quer verificar com ArchUnit) e o `TemplateService` sem tratar exceção do SDK. |
| `PUT /templates/{name}` | `TemplateService.update` continua igual; quem muda é `SesTemplateClient.updateTemplate` (linha acima). O `TemplateNotFoundException` desse método deixa de existir; o 404 do `PUT` vem só do `findOrThrow`. | proposta | Requisito "No `PUT`" da spec. |
| Preview | `TemplateService.preview`: se `testRenderTemplate` lançar `TemplateNotFoundException` com a linha existindo, chama `putTemplate` com `subject`/`body` da linha e renderiza uma segunda vez. Se a segunda falhar do mesmo jeito, o 404 sobe. | proposta | Uma nova tentativa só, sem laço. O `SesTemplateClient` continua sem acesso ao banco. |
| `POST /templates` com sobra no SES | `TemplateService.create` chama `putTemplate` em vez de `createTemplate`. O 409 continua vindo só do `existsByClientIdAndName`. | proposta | Decisão em aberto 2 da spec. Ordem SES → banco da 05-025 mantida. |
| Reconciliação na subida | Classe nova `template/SesTemplateReconciler`, `SmartInitializingSingleton`: em `afterSingletonsInstantiated()`, lista os nomes do SES (laço por `NextToken`, como `TemplateCleanupHooks`), lê `repository.findAll()`, chama `putTemplate` para cada linha cujo `ses_template_name` não está na lista e registra `WARN` para cada nome do SES com o separador `__` sem linha. | proposta | Roda durante o `refresh` do contexto, antes de o Tomcat abrir a porta 8080 (a porta abre no `finishRefresh`). Assim o healthcheck do Compose só passa com o SES reconciliado. Um `ApplicationRunner` ou `ApplicationReadyEvent` rodaria com a porta já aberta e o `app` poderia sincronizar no meio. |
| Concorrência entre instâncias | Coberta por `putTemplate`: a segunda instância recebe `AlreadyExistsException` e cai no `UpdateTemplate` com o mesmo conteúdo. | proposta | Sem lock nem tabela de controle. |
| Falha na subida | `SesTemplateReconciler` tenta todos os templates ausentes, junta as falhas e, se houver alguma, lança `TemplateReconciliationException` (nova, `RuntimeException`) com os nomes e a mensagem de cada falha. Falha de `ListTemplates` (SES fora do ar) também vira essa exceção. A exceção derruba o `refresh` e o serviço não sobe. | proposta | Decisão em aberto 3 da spec. Tentar todos antes de falhar mostra o problema inteiro numa subida só. |
| Contrato | `docs/openapi-email-service.yaml`: descrições de `PUT /templates/{name}`, `POST /templates/{name}/preview` e `POST /templates` dizem que o serviço recria/sobrescreve o template no SES. Nenhum status novo nem removido. | proposta | Contract-first: o texto do contrato muda no mesmo PR da implementação. |
| `app` | Sem mudança. | proposta | `upsertTemplate` faz `PUT` primeiro, que passa a responder 200. |
| Spec 05-025 | `data-model.md`, "Invariantes": acrescentar que o banco é a fonte e o SES é recriado a partir dele (05-044). | proposta | O invariante de hoje fala só da ordem de escrita. |

## Estrutura de módulos/pacotes

- `email-service/.../template/SesTemplateClient.java`: `putTemplate`; `updateTemplate` com
  recriação.
- `email-service/.../template/TemplateService.java`: `create` e `preview`.
- `email-service/.../template/SesTemplateReconciler.java` (novo).
- `email-service/.../template/TemplateReconciliationException.java` (novo).
- `email-service/src/test/resources/features/register_templates.feature` e
  `template/steps/RegisterTemplatesSteps.java`: cenários novos.
- `email-service/src/test/java/.../template/SesTemplateReconcilerIntegrationTest.java` (novo).
- `docs/openapi-email-service.yaml`, `specs/05-025-servico-email-templates/data-model.md`.

Nada sai do pacote `template`; nenhuma dependência nova.

## Verificações (antes das correções, falhando)

1. Cenários Cucumber (passo novo `Given SES lost the template named "welcome"`, que faz
   `DeleteTemplate` direto no LocalStack):
   - atualizar `welcome` depois de o SES perdê-lo → conteúdo novo no banco e no SES. Hoje: 404.
   - preview de `welcome` depois de o SES perdê-lo → assunto e corpo renderizados. Hoje: 404.
   - cadastrar `welcome` com `client-a__welcome` já no SES e sem linha → 201, SES com o conteúdo
     pedido. Hoje: 422 (`AlreadyExistsException`).
2. `SesTemplateReconcilerIntegrationTest` (PostgreSQL e LocalStack reais): cadastra templates
   pelo `TemplateService`, apaga do SES, sobe um segundo contexto com
   `SpringApplicationBuilder(EmailServiceApplication.class)` contra a mesma infraestrutura e
   confere com `GetTemplate` que o SES voltou a ter cada um, com o conteúdo do banco. Hoje: o
   segundo contexto sobe e o SES continua sem os templates. Casos:
   - um template ausente;
   - mais templates ausentes que uma página de `ListTemplates` (o padrão do SES é 10; o teste usa
     11). Confirmar na implementação que o LocalStack pagina; se não paginar, registrar aqui e
     manter o caso;
   - template só no SES (`client-a__orphan`): continua no SES e aparece um `WARN` com o nome
     (`OutputCaptureExtension`);
   - SES inacessível (endpoint para uma porta fechada): o segundo contexto não sobe e a causa é
     `TemplateReconciliationException`. Hoje: sobe.
3. Reprodução da Issue, manual (Leila): passos 1–4 da Issue #144. Depois da correção o `app` sobe
   na segunda execução sem `down -v`.

## Riscos e trade-offs

- **Envio continua sujeito ao problema** entre a perda no SES e a próxima subida do
  `email-service` ou `PUT` do `app` (spec, "Fora de escopo").
- **Conteúdo divergente não é detectado.** Comparar conteúdo exigiria um `GetTemplate` por
  template a cada subida.
- **Subida depende do SES.** Já dependia na prática (o `app` sincroniza logo em seguida), mas
  agora a falha aparece no `email-service`, com os nomes dos templates.
- **Sobrescrever no `POST`** apaga um template que alguém criou no SES por fora com o mesmo nome
  `<client>__<name>`. Aceito: o prefixo é do `email-service`.
- **Exceção sem teste possível:** `SesException` genérica em `putTemplate` durante a recriação
  com o SES no ar. O LocalStack não rejeita sintaxe (`@requires-real-ses`); a falha da
  reconciliação é exercitada pelo caso de SES inacessível.
