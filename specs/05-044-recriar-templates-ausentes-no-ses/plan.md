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
| Subida sem SES | Nenhuma chamada ao SES no `refresh` do contexto. A reconciliação começa no `ApplicationReadyEvent`, numa thread do `TaskScheduler`, e não segura a subida. | resolvida (Leila, 2026-10-14) | O `email-service` tem que subir mesmo sem o SES. |
| Onde mora a recriação | `SesTemplateClient` ganha dois métodos: `putTemplate(sesName, subject, body)` (tenta `CreateTemplate`; com `AlreadyExistsException`, faz `UpdateTemplate`) e `createIfAbsent(sesName, subject, body)` (tenta `CreateTemplate`; com `AlreadyExistsException`, não faz nada). `updateTemplate` passa a tentar `UpdateTemplate` e, com `TemplateDoesNotExistException`, fazer `CreateTemplate`. | proposta | O SES v1 não tem "upsert". Mantém o SDK dentro do `SesTemplateClient` (regra que a Issue #136 quer verificar com ArchUnit) e o `TemplateService` sem tratar exceção do SDK. |
| `PUT /templates/{name}` | `TemplateService.update` continua igual; quem muda é `SesTemplateClient.updateTemplate` (linha acima). O `TemplateNotFoundException` desse método deixa de existir; o 404 do `PUT` vem só do `findOrThrow`. | proposta | Requisito "No `PUT`" da spec. |
| Preview | `TemplateService.preview`: se `testRenderTemplate` lançar `TemplateNotFoundException` com a linha existindo, chama `putTemplate` com `subject`/`body` da linha e renderiza uma segunda vez. Se a segunda falhar do mesmo jeito, o 404 sobe. | proposta | Uma nova tentativa só, sem laço. O `SesTemplateClient` continua sem acesso ao banco. |
| `POST /templates` com sobra no SES | `TemplateService.create` chama `putTemplate` em vez de `createTemplate`. O 409 continua vindo só do `existsByClientIdAndName`. | proposta | Decisão em aberto 2 da spec. Ordem SES → banco da 05-025 mantida. |
| Reconciliação | Classe nova `template/SesTemplateReconciler`: lista os nomes do SES (laço por `NextToken`, como `TemplateCleanupHooks`), lê `repository.findAll()`, chama `createIfAbsent` para cada linha cujo `ses_template_name` não está na lista e registra `WARN` para cada nome do SES com o separador `__` sem linha. | proposta | `createIfAbsent`, e não `putTemplate`, porque a reconciliação roda com o serviço já aceitando requisições: se ela lesse a linha antiga e um `PUT` atualizasse o SES no meio, um `UpdateTemplate` da reconciliação voltaria o SES para o conteúdo antigo. |
| Novas tentativas da reconciliação | Qualquer falha numa rodada (SES inacessível, `ListTemplates` ou `CreateTemplate` recusado) vira `WARN` com os nomes afetados e agenda outra rodada completa. Espera inicial de 1 s, dobrando até 5 min, sem desistir. Valores em `email.ses.reconciliation.initial-delay` e `max-delay`. Para depois da primeira rodada sem falha. | proposta | O serviço precisa dos templates no SES para enviar; desistir deixaria o problema escondido até alguém reiniciar. Uma rodada completa é idempotente, então repetir tudo é mais simples que guardar quais faltaram. |
| Concorrência entre instâncias | Coberta por `createIfAbsent`: a segunda instância recebe `AlreadyExistsException` e segue. | proposta | Sem lock nem tabela de controle. |
| SES fora do ar nas rotas de template | Exceção nova `SesUnavailableException` (pacote `template`), lançada pelo `SesTemplateClient` quando o SDK não chega ao SES (`SdkClientException`) ou o SES responde 5xx. `ApiExceptionHandler` → `503`. O resto das `SesException` continua `TemplateRejectedBySesException` (`422`). | proposta | Decisão em aberto 3 da spec. Hoje SES fora do ar vira `422`, que diz ao cliente que o template está errado. |
| Contrato | `docs/openapi-email-service.yaml`: `503` em `POST /templates`, `PUT /templates/{name}` e `POST /templates/{name}/preview`; descrições dizem que o serviço recria/sobrescreve o template no SES. | proposta | Contract-first: o contrato muda no mesmo PR da implementação. |
| `app` | Sem mudança. O `EmailServiceGateway` já tenta de novo `PUT` com 5xx (3 vezes, 200 ms) e o `app` não sobe se o SES continuar fora. | proposta | Decisão em aberto 3 da spec. |
| Spec 05-025 | `data-model.md`, "Invariantes": acrescentar que o banco é a fonte e o SES é recriado a partir dele (05-044). | proposta | O invariante de hoje fala só da ordem de escrita. |

## Estrutura de módulos/pacotes

- `email-service/.../template/SesTemplateClient.java`: `putTemplate`, `createIfAbsent`;
  `updateTemplate` com recriação; `SesUnavailableException`.
- `email-service/.../template/TemplateService.java`: `create` e `preview`.
- `email-service/.../template/SesTemplateReconciler.java` (novo).
- `email-service/.../template/SesUnavailableException.java` (novo).
- `email-service/.../common/ApiExceptionHandler.java`: `503` para `SesUnavailableException`.
- `email-service/src/test/resources/features/register_templates.feature` e
  `template/steps/RegisterTemplatesSteps.java`: cenários novos.
- `email-service/src/test/java/.../template/SesTemplateReconcilerIntegrationTest.java` (novo).
- `email-service/src/test/java/.../template/SesUnavailableIntegrationTest.java` (novo).
- `docs/openapi-email-service.yaml`, `specs/05-025-servico-email-templates/data-model.md`.

Nada sai do pacote `template` além do handler; nenhuma dependência nova.

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
   espera (Awaitility) o SES voltar a ter cada um, com o conteúdo do banco. Hoje: o SES continua
   sem os templates. Casos:
   - um template ausente;
   - mais templates ausentes que uma página de `ListTemplates` (o padrão do SES é 10; o teste usa
     11). Confirmar na implementação que o LocalStack pagina; se não paginar, registrar aqui e
     manter o caso;
   - template só no SES (`client-a__orphan`): continua no SES e aparece um `WARN` com o nome
     (`OutputCaptureExtension`);
   - SES inacessível na subida e de volta depois: o segundo contexto aponta o SES para um
     encaminhador TCP do próprio teste, fechado no começo. O contexto sobe e responde
     requisições, um `WARN` registra a falha; o teste abre o encaminhador (para o LocalStack) e
     espera o template aparecer no SES. Hoje: o contexto sobe, mas o template nunca aparece.
3. `SesUnavailableIntegrationTest`: contexto com o SES apontado para uma porta fechada;
   `POST /templates` → `503` e nenhuma linha gravada; `PUT` e preview de um template com linha →
   `503`. Hoje: `422`.
4. Reprodução da Issue, manual (Leila): passos 1–4 da Issue #144. Depois da correção o `app` sobe
   na segunda execução sem `down -v`.

## Riscos e trade-offs

- **Templates podem faltar no SES por um tempo** depois da subida, até a reconciliação terminar.
  Um envio nesse intervalo falha na `email-lambda`, chega pela DLQ como `TEMPLATE_MISSING` e é
  recriado e publicado de novo (05-046).
- **Conteúdo divergente não é detectado.** Comparar conteúdo exigiria um `GetTemplate` por
  template a cada subida.
- **Sobrescrever no `POST`** apaga um template que alguém criou no SES por fora com o mesmo nome
  `<client>__<name>`. Aceito: o prefixo é do `email-service`.
- **Exceção sem teste possível:** `SesException` genérica (não 5xx) em `putTemplate` com o SES no
  ar. O LocalStack não rejeita sintaxe (`@requires-real-ses`).
