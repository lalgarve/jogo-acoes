# Plan: Fronteira síncrona entre `app` e `email-service` (Etapa 2)

Traduz `spec.md` em decisões técnicas. Valida contra `memory/constitution.md`.

## Contexto técnico

- **`app`** (Spring Boot 4.1, Java 21): `email/` tem a interface `EmailSender` (`send(EmailRequest)`)
  com duas implementações escolhidas por `email.sender`: `StubEmailSender` (padrão, só grava em
  `sent_email`) e `SqsEmailSender` (`docker`/`staging`/`production`, renderiza com
  `EmailContentRenderer` + Thymeleaf e publica em SQS). `SentEmailRecorder` grava `sent_email`
  nos dois casos. `QueueLoggingAspect` intercepta `SqsEmailSender.send`. O `app` já usa o
  `openapi-generator-maven-plugin` (gerador `spring`, `interfaceOnly`) para o próprio
  `docs/openapi.yaml`.
- **`EmailRequest`** carrega `template` (`INVITE`, `REGISTRATION_LINK`, `LOGIN_LINK`) e, para
  `LOGIN_LINK`, `competitionName`/`origin` escolhem um de 3 arquivos físicos — 5 templates no
  total (`invite.html`, `registration-link.html`, `login-link.html`, `login-link-invite.html`,
  `login-link-request.html`) e fragmentos de cabeçalho/rodapé em `fragments/`.
- **`email-service`**: contrato `docs/openapi-email-service.yaml` (`/templates`,
  `/templates/{name}`, `/templates/{name}/preview` e, pela 05-031, `/emails`). `POST /templates`
  devolve `409` se o nome já existe para o cliente; `PUT /templates/{name}` devolve `404` se não
  existe. Templates em sintaxe Handlebars do SES.
- **Ambiente**: `docker-compose.yml` já sobe `email-service` (porta 8082) e já passa
  `EMAIL_SERVICE_API_KEY` (chave de teste do cliente `jogo-acoes`, spec 05-030) para o `app`,
  sem ninguém ler ainda.
- **Pacote raiz compartilhado**: `app` usa `dev.leilaalgarve.jogoacoes.<módulo>` e o
  `email-service` usa `dev.leilaalgarve.jogoacoes.emailservice.<módulo>`. O `ArchitectureTest`
  do `app` importa `dev.leilaalgarve.jogoacoes`; se o jar do `email-service` aparecesse no
  classpath, as classes dele entrariam na análise como se fossem do `app`.
- **Depende da spec 05-031** (PR #110): sem `POST /emails` não há para onde o cliente Feign
  enviar.

## Decisões de arquitetura

| Pergunta | Decisão | Status | Raciocínio |
|---|---|---|---|
| Cliente HTTP | Spring Cloud OpenFeign (`spring-cloud-starter-openfeign`) | resolvida (requisito da Etapa 2) | A disciplina pede OpenFeign. O Spring Boot 4 tem `@HttpExchange` nativo e o OpenFeign está em modo de manutenção, mas trocar não é escolha desta spec. Versão do Spring Cloud alinhada ao Spring Boot do projeto; se não houver release compatível com o 4.1, reportar e decidir (ver "Riscos"). |
| De onde vem a interface Feign | Gerada a partir de `docs/openapi-email-service.yaml` pelo `openapi-generator-maven-plugin` já usado no `app`, gerador `spring` com `library: spring-cloud` (`@FeignClient`), pacotes `dev.leilaalgarve.jogoacoes.email.client.api`/`.model` | implementada (2026-10-05) | Contract-first também do lado do consumidor: se o contrato mudar, o `app` deixa de compilar em vez de falhar em produção. Os DTOs ficam do `app` (gerados), sem importar nada do `email-service`. Alternativa: interface escrita à mão (menos ferramenta, mas pode divergir do contrato sem ninguém perceber). |
| Onde fica o cliente dentro do `app` | `email/client/`: interface gerada + `EmailServiceGateway` (única classe que chama a interface Feign, traduz `FeignException` em exceções do `app`) + `EmailServiceEmailSender implements EmailSender` | implementada (2026-10-05) | Convenção `client/` (Feign + Gateway que traduz exceções + DTO remoto) já registrada na spec 05-002 a partir do repositório de referência da disciplina. Os outros módulos continuam chamando só `EmailSender`, sem saber que existe HTTP. |
| Destino do envio por SQS no `app` | Remover `SqsEmailSender`, `EmailMessage`, `EmailContentRenderer`, `RenderedEmail`, os templates Thymeleaf de e-mail, `spring-cloud-aws-starter-sqs` do `pom.xml` e `email.queue-name`/endpoint SQS dos perfis. `email.sender` passa a aceitar `stub` e `email-service` | implementada (2026-10-06) | Com a 05-031 a Lambda só aceita mensagem com template, publicada pelo `email-service`. Manter um segundo publicador no `app` seria uma segunda fronteira com a fila, que é da Etapa 4. Pré-produção: sem período de convivência. |
| `QueueLoggingAspect` | Passa a interceptar `EmailServiceGateway` (ou é renomeado para algo como `EmailServiceLoggingAspect`), logando template, `correlationId`/`id` devolvido e status HTTP, nunca a chave nem o corpo do e-mail | implementada (2026-10-06): `EmailServiceLoggingAspect` | Mantém a rastreabilidade que existe hoje sem vazar dado sensível. |
| Nome dos templates do `jogo-acoes` no `email-service` | `invite`, `registration-link`, `login-link`, `login-link-invite`, `login-link-request` (mesmo nome dos arquivos atuais); o mapeamento `EmailRequest` → nome fica no `EmailServiceEmailSender` | implementada (2026-10-05) | Uma tabela de tradução só, no lado de quem sabe a regra (o `app`). O `email-service` aplica o namespace `jogo-acoes__<nome>` no SES. |
| Conteúdo dos templates e cabeçalho/rodapé | Templates Handlebars em `app/src/main/resources/email-templates/` (um arquivo de assunto e um de corpo por template, mais `header`/`footer`). O `app` concatena cabeçalho + corpo + rodapé antes de cadastrar | implementada (2026-10-05) | O SES não tem `th:insert`; montar no `app` antes de cadastrar evita duplicar cabeçalho/rodapé à mão em 5 arquivos (`docs/context/iteracao-5.md`, 3.2). Isso não é renderizar: as variáveis continuam `{{...}}` para o SES substituir. |
| Sincronização dos templates | `EmailTemplateSynchronizer` no `app`, rodando em `ApplicationReadyEvent` quando `email.sender=email-service`: para cada template, `PUT /templates/{name}`; se `404`, `POST /templates`; se o `POST` der `409` (outra instância criou no meio), `PUT` de novo | implementada (2026-10-05) | Upsert idempotente usando só o contrato atual, sem mudar o `email-service`. Rodar duas vezes, ou duas instâncias ao mesmo tempo, termina no mesmo estado. Falha na sincronização impede o `app` de ficar pronto (falha cedo) em vez de deixar o primeiro e-mail falhar depois. |
| Retry automático | Feign `Retryer` desligado por padrão; retry simples (até 3 tentativas, espera curta) só no `EmailServiceGateway` e só para `GET`, `PUT /templates/{name}` e `preview`, e só para erro de conexão/`503` | implementada (2026-10-05) | Repetir `POST /emails` sem idempotência pode enviar o e-mail duas vezes; a idempotência dele fica na Etapa 4 (decidido em 2026-10-05, `spec.md`, "Decisões resolvidas"). `401`, `404`, `409`, `422` não são repetidos: repetir não muda a resposta. |
| Tempo limite | `connectTimeout` 2 s e `readTimeout` 5 s em `spring.cloud.openfeign.client.config.email-service`, sobrescrevíveis por perfil | implementada (2026-10-05) | O `email-service` responde depois de gravar e publicar (05-031) ou depois de falar com o SES (cadastro de template); 5 s cobre o SES real com folga sem pendurar uma requisição do usuário. |
| Falha no envio vista pelo usuário do `app` | `EmailServiceUnavailableException` (do `app`) → `503` no `GlobalExceptionHandler`, mesmo tratamento que uma falha de fila teria hoje; a transação de negócio que pediu o e-mail é desfeita | implementada (2026-10-06) | O usuário pode tentar de novo (pedir outro link, reenviar convite) e não fica um convite gravado cujo e-mail nunca saiu. Conferir na implementação se o fluxo atual chama `EmailSender` dentro da transação; se não, registrar aqui. |
| `sent_email` × `id` do `email-service` | Nova coluna `email_service_id UUID` em `sent_email` (migration nova em `db/migration-jogo-acoes`), preenchida com o `id` do `202`. Nula só quando `email.sender=stub` | implementada (2026-10-06) | Permite seguir um envio do `app` até o registro do `email-service` (e, na Etapa 4, até o evento do SES pelo `correlationId`) sem um serviço ler o banco do outro. Nula por causa do stub, não por compatibilidade. `sent_email` continua só recebendo inserções. |
| Configuração | `email-service.base-url` e `email-service.api-key` (`${EMAIL_SERVICE_API_KEY}`) no `app`; `base-url` por perfil (`http://email-service:8080` no `docker`, `http://localhost:8082` no `sandbox`, de fora em `staging`/`production`) | implementada (2026-10-05) | A chave nunca vai para arquivo versionado fora da chave de teste já documentada (05-030). O interceptor que põe `X-API-Key` fica no `client/`, não espalhado. |
| Proibir dependência Java entre os serviços | `maven-enforcer-plugin` com `bannedDependencies` em cada módulo: `app` proíbe `dev.leilaalgarve.jogoacoes:email-service` e o `email-service` proíbe `dev.leilaalgarve.jogoacoes:jogo-acoes`, inclusive transitivas | implementada (2026-10-05) | Pega o acoplamento no ponto mais cedo (resolução de dependências), antes de qualquer classe ser usada. |
| Regras de arquitetura no `app` (ArchUnit) | Novas regras em `common/ArchitectureTest`: (a) nenhuma classe do `app` reside em nem depende de `..jogoacoes.emailservice..`; (b) nenhuma classe depende de SQS (`io.awspring.cloud.sqs..`, `software.amazon.awssdk.services.sqs..`); (c) nenhuma classe depende de Thymeleaf para e-mail (`org.thymeleaf..`); (d) a API Feign gerada (`..email.client.api..`) só é acessada pelo `EmailServiceGateway`; (e) nenhuma classe fora de `..email..` depende de `..email.client..` | implementada (2026-10-05) | (b) e (c) nascem vermelhas com exatamente 1 violação cada hoje (`SqsEmailSender`, `EmailContentRenderer`). (d) nasce vermelha porque o pacote ainda não existe (ArchUnit falha sem classes para checar). (a) e (e) nascem verdes; a prova de que pegam algo é uma violação temporária, registrada em `tasks.md`. |
| Regras de arquitetura no `email-service` | `ArchitectureTest` novo: nenhuma classe depende de `dev.leilaalgarve.jogoacoes..` fora de `..emailservice..` (ou seja, de classe do `app`) | implementada (2026-10-05) | Mesmo raciocínio do lado do `app`. O `email-service` não tem `ArchitectureTest` hoje; precisa de `archunit-junit5` no `pom.xml`. |
| Como o `app` testa o cliente | `EmailServiceClientIntegrationTest` no `app` (`@SpringBootTest`, perfil com `email.sender=email-service`) contra o `email-service` real do Docker Compose (que usa Postgres e LocalStack reais), com a chave de teste do cliente `jogo-acoes` | implementada (2026-10-05) | Constitution: real em vez de mock. Testa o que mock não testa: o contrato de verdade, a API-KEY de verdade, o namespace no SES do LocalStack. As suítes Cucumber do `app` continuam com `stub`. |
| Regra no `memory/constitution.md` | Seção nova "Fronteira entre módulos e serviços": módulo (mesmo processo) fala por serviço Java, nunca por repositório de outro módulo; serviço (deploy próprio) fala só por contrato (HTTP documentado em OpenAPI, ou mensagem com contrato próprio), sem dependência Java, sem persistência compartilhada, sem exceção interna atravessando; cada lado documenta o que é dono; operação síncrona que o cliente repete tem que ser idempotente ou não é repetida automaticamente | implementada (2026-10-05) | Pedido explícito do épico #117 ("revisar e registrar na constitution antes da implementação"). Fica genérica (vale para serviços futuros); o caso `app`/`email-service` entra como exemplo, no mesmo formato da seção "Banco de dados: um schema por serviço". |

Decisões marcadas "em aberto" (e as "proposta" que dependem delas) viram commit `decision:` quando
resolvidas (ver `memory/constitution.md`), atualizando esta tabela no mesmo commit.

## Ajustes feitos na implementação (2026-10-05)

O que mudou em relação às linhas da tabela acima, e por quê:

- **Ativação pelo `email-service.base-url`, não por `email.sender=email-service`.** Cliente,
  gateway e sincronização existem só onde a URL está definida (`EMAIL_SERVICE_BASE_URL`, hoje só
  no contêiner `app` do `docker-compose.yml` e no teste de integração). Assim a sincronização já
  roda antes de a 05-031 existir, e as suítes Cucumber continuam sem `email-service` (decisão
  resolvida 2 da `spec.md`). `email.sender=email-service` entra com o `EmailServiceEmailSender`
  (T014).
- **URL por variável de ambiente, não por perfil.** Pôr a URL no `application-docker.yml`
  ligaria a sincronização em todos os testes da CI (que rodam com o perfil `docker`). A URL
  inclui o `/api` do contrato.
- **Sem classe `EmailServiceApiKeyInterceptor`.** O `RequestInterceptor` é um `@Bean` em
  `EmailServiceClientConfiguration`; os `ClientConfiguration`/`ApiKeyRequestInterceptor` que o
  gerador também produz (pacote `email.client.api.configuration`) ficam sem uso, porque a
  propriedade que eles leem tem nome derivado do título do contrato.
- **Exceções em `email/exception/`** (convenção da spec 05-002: duas ou mais exceções no
  módulo), não em `email/client/`.
- **Propriedades `templates.name`/`templates.url`.** São os nomes que o `TemplatesApiClient`
  gerado lê; `templates.url` aponta para `email-service.base-url`.
- **Spring Cloud 2025.1.3 no Spring Boot 4.1.0.** Nenhum *release train* tem o 4.1 como alvo
  (o 2025.1 tem o 4.0, o 2026.0.0-M1 tem o 4.2). Um teste mínimo (`@FeignClient` chamando um
  endpoint local) rodou sem erro no 4.1.0, e o build do `app` também; o risco abaixo fica
  registrado, não confirmado.
- **O `email-service` já tinha `ArchitectureTest`** (spec 05-030): a regra nova entrou nele, sem
  mudar o `pom.xml`.



```
memory/constitution.md                           # + "Fronteira entre módulos e serviços"
CLAUDE.md                                        # + lembrete curto, se couber
specs/05-034-.../contracts/feign-client.md       # como o app consome o email-service (T007)
app/pom.xml                                      # + openfeign, + execução do generator
                                                 #   (openapi-email-service.yaml, spring-cloud),
                                                 #   + enforcer; - spring-cloud-aws-starter-sqs
app/src/main/java/dev/leilaalgarve/jogoacoes/email/
  EmailSender.java, EmailRequest.java, EmailTemplate.java       # sem mudança de interface
  SentEmail.java, SentEmailRecorder.java, SentEmailRepository.java  # + email_service_id
  StubEmailSender.java                                          # sem mudança
  client/
    EmailServiceGateway.java                    # única classe que usa a API Feign gerada
    EmailServiceEmailSender.java                # EmailSender → POST /emails
    EmailTemplateSynchronizer.java              # upsert dos 5 templates ao subir
    EmailServiceApiKeyInterceptor.java          # X-API-Key
    EmailServiceUnavailableException.java, ...
  (removidos) SqsEmailSender, EmailMessage, EmailContentRenderer, RenderedEmail
app/src/main/resources/
  email-templates/                              # Handlebars: header, footer, 5 × (subject, body)
  templates/email/                              # removido
  db/migration-jogo-acoes/V8__add_email_service_id_to_sent_email.sql
app/src/test/java/.../common/ArchitectureTest.java              # + regras (a)–(e)
app/src/test/java/.../email/client/EmailServiceClientIntegrationTest.java
email-service/pom.xml                           # + archunit-junit5, + enforcer
email-service/src/test/java/.../common/ArchitectureTest.java   # novo
docker-compose.yml                              # app depende de email-service; EMAIL_SERVICE_URL
```


## Ajustes feitos na segunda rodada (2026-10-06, depois da 05-031)

- **Envio dentro da transação: conferido.** `CompetitionService.decideInviteEmailTiming`,
  `EntryRequestService.requestEntry` e `PlayerManagementService` (`invitePlayers`,
  `resendInviteEmail(s)`) chamam `EmailSender` dentro do próprio `@Transactional`: uma
  `EmailServiceUnavailableException` desfaz o convite/link gravado. A exceção é o
  `POST /login-requests` (`LoginController.requestLoginLink`), que não é transacional: se o envio
  falhar, o link já criado fica ativo sem ter sido enviado. Não é risco novo (é o mesmo
  comportamento de antes com a fila) e o usuário recupera pedindo outro link, que invalida os
  anteriores; não foi alterado nesta spec.
- **`503` no contrato.** `docs/openapi.yaml` ganhou a resposta `EmailServiceUnavailable` (`503`
  com `Error`) nas seis operações que enviam e-mail: `decideInviteEmailTiming`,
  `requestOrConfirmEntry`, `requestLoginLink`, `invitePlayers`, `resendPlayerInviteEmail` e
  `resendPlayerInviteEmails`. O tratador é o `ApiExceptionHandler` (o nome real do
  "`GlobalExceptionHandler`" da tabela).
- **Ordem do envio.** O `EmailServiceEmailSender` chama o `email-service` primeiro e só então
  grava `sent_email` com o `id` do `202`: a tabela só aceita inserção, então o `id` precisa
  existir na hora do `INSERT`. Envio que falha não deixa linha.
- **Onde fica o `EmailServiceEmailSender`.** No pacote `email`, não em `email/client`: usa o
  `SentEmailRecorder`, que é do pacote `email`. Só o `EmailServiceGateway` fica em `client/`.
- **`email.sender` no perfil `docker`.** Vem da variável `EMAIL_SENDER` (padrão `stub`, em
  `application.yml`). O container `app` do Compose define `email-service`; as suítes Cucumber,
  que rodam com o perfil `docker` no runner da CI, ficam com o stub, como decidido na `spec.md`.
  `staging`/`production` fixam `email-service` e exigem `EMAIL_SERVICE_BASE_URL`, sem padrão.
- **Teste de envio contra o SES, não contra a fila.** O item (5) do T006 confere a mensagem no
  SES do LocalStack (`GET /_aws/ses`, template `jogo-acoes__<nome>` e o `link` no
  `templateData`), não na fila: o `email-lambda` do Compose consome a fila logo que a mensagem
  chega, então olhar a fila seria uma corrida. O SES cobre a fila e vai além dela.
- **`assumeTrue` removido** do `EmailServiceClientIntegrationTest`, seguindo a constitution
  ("Testes exigem a infraestrutura de pé"). O `ProductionProfileSuppressesLoggingAspectsTest`
  passa a apontar para o `email-service` real, porque o perfil `production` agora exige a URL.
- **CI.** `scripts/test-api-key.sh restore` roda de novo depois de o `email-service` subir, para
  configurar o remetente do cliente `jogo-acoes` (a tabela `client_sender` só existe depois do
  Flyway do `email-service`).
## Riscos e trade-offs

- **Compatibilidade do Spring Cloud com o Spring Boot 4.1.** Se não houver release do Spring
  Cloud OpenFeign compatível, a implementação para e reporta a incompatibilidade concreta, sem
  trocar Spring Boot nem Java por conta própria (`CLAUDE.md`, "Baseline Java").
- **Gerador `spring-cloud` do openapi-generator com Spring Boot 4.** Pode gerar código para
  versões anteriores (anotações, `jakarta`). Se não servir, a alternativa é a interface Feign
  escrita à mão, com um teste que confere contra o contrato; a decisão fica registrada aqui.
- **Ordem de subida no Docker Compose.** Com a sincronização ao subir, o `app` precisa do
  `email-service` pronto: `depends_on` com `condition: service_healthy` e healthcheck no
  `email-service`. Sem isso, o `app` falha ao subir e o Compose não reinicia sozinho.
- **Envio dentro da transação.** Uma chamada HTTP dentro de uma transação de banco segura uma
  conexão do pool enquanto espera; com tempo limite de 5 s o pior caso é limitado. Mover o envio
  para depois do commit muda a semântica de falha (convite gravado sem e-mail) e não é desta
  spec.
- **Conteúdo dos e-mails.** Converter Thymeleaf para Handlebars pode mudar detalhes de
  formatação. Conferir os 5 e-mails no SES Viewer do LocalStack antes e depois, registrando em
  `tasks.md`.
- **Sincronização ao subir com o SES real.** Cada subida do `app` faz 5 `UpdateTemplate` no SES.
  Aceitável no volume atual; se incomodar, comparar antes (`GET`) e só atualizar o que mudou.
- **Idempotência de `POST /emails`** fica para a Etapa 4 (decidido em 2026-10-05). Até lá, uma falha de rede depois do `202` pode fazer o usuário
  pedir de novo e receber dois e-mails; é o mesmo risco que já existe com o SQS hoje.
- **Regras que nascem verdes** (a, e, enforcer) só provam algo com a violação temporária
  descrita em `tasks.md`; essa verificação é manual e fica registrada, não automatizada.
