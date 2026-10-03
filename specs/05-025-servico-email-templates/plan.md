# Plan: Serviço de E-mail — cadastro de templates

Traduz `spec.md` em decisões técnicas. Valida contra `memory/constitution.md`.

## Contexto técnico

- Novo módulo Maven `email-service`, adicionado ao reator raiz (`pom.xml`) ao lado de `app`/
  `email-lambda`/`blackbox-proxy`. Mesmo `groupId` (`dev.leilaalgarve.jogoacoes`), mesmo parent
  (`spring-boot-starter-parent` 4.1.0), mesmo `java.version` (21) — convenção já estabelecida
  pelos três módulos existentes (verificado em `app/pom.xml`, `blackbox-proxy/pom.xml`,
  `email-lambda/pom.xml`).
- Pacote base: `dev.leilaalgarve.jogoacoes.emailservice` (mesmo padrão de `...blackboxproxy`,
  `...email.lambda`).
- API-first via `docs/openapi-email-service.yaml` (já escrito) — `openapi-generator-maven-plugin`
  gera as interfaces de controller e os DTOs, mesma configuração de `app/pom.xml`
  (`interfaceOnly=true`, `library=spring-boot`, `useSpringBoot3=true`), apontando
  `apiPackage`/`modelPackage` para `dev.leilaalgarve.jogoacoes.emailservice.api[.model]`.
- Integração com SES via `spring-cloud-aws-starter-ses` (mesma versão `4.1.0` de
  `spring-cloud-aws-starter-sqs` em `app/pom.xml`) — não pelo motivo óbvio (o `MailSender` de
  alto nível que esse starter expõe), mas porque ele autoconfigura um bean `SesClient` (SDK v2
  cru) usando a mesma estrutura de propriedades já usada por `app`
  (`spring.cloud.aws.credentials.*`, `spring.cloud.aws.ses.endpoint`) — o `MailSender` em si
  nunca é usado, porque não expõe `CreateTemplate`/`UpdateTemplate`/`TestRenderTemplate` (essas
  são chamadas de baixo nível, só disponíveis no `SesClient` injetado diretamente, mesmo cliente
  que `email-lambda` já usa via `software.amazon.awssdk:ses`, verificado em
  `email-lambda/pom.xml`/`EmailSendHandler.java`).

## Decisões de arquitetura

| Pergunta | Decisão | Status | Raciocínio |
|---|---|---|---|
| Onde roda o processo? | Módulo Spring Boot próprio no reator atual | resolvida | Decidido em sessão anterior (ver `spec.md`, "Fora de escopo") — não é repositório novo. |
| Banco de dados | PostgreSQL dedicado, container `db-email-service` próprio no `docker-compose.yml` (não reaproveita `db`/`jogo_acoes`) | resolvida | "Persistência própria" (spec.md) interpretado literalmente — isolamento de infraestrutura, não só lógico, mesmo padrão de independência já usado pelo próprio projeto como critério de "candidato a serviço independente" (`docs/roadmap.md`). Reaproveitar o mesmo container com um 2º database exigiria um init script de `CREATE DATABASE` mais frágil (não roda na mesma transação/sessão do `01-roles.sql` atual) sem ganho real. |
| Papéis do banco | Mesmo padrão de `docker/postgres/init/01-roles.sql`: `email_service_admin` (dono do schema, roda Flyway) + `email_service_app` (`SELECT`/`INSERT`/`UPDATE`/`DELETE` via `ALTER DEFAULT PRIVILEGES`) | resolvida | Reaproveita a convenção já estabelecida, só duplicada para o novo container/banco. |
| Cliente SES | `spring-cloud-aws-starter-ses`, usando o `SesClient` que ele autoconfigura (não o `MailSender`) | resolvida | Ver "Contexto técnico" acima. |
| Namespacing do template no SES | `<client_id>__<name>` | resolvida (spec.md) | Evita colisão entre clientes; `client_id` aqui é o valor bruto da API-KEY (esqueleto). |
| Ordem das operações em criar/atualizar | SES primeiro (`CreateTemplate`/`UpdateTemplate`), banco só grava/atualiza **depois** do SES confirmar sucesso | resolvida | Evita uma linha no banco apontando para um template que não existe de fato no SES (ver `data-model.md`, "Invariantes"). Em caso de falha do SES, nada é persistido. |
| Exceções do SES → resposta HTTP | `InvalidTemplateException`→422 (create/update); `AlreadyExistsException`/`LimitExceededException`→422 (create); `TemplateDoesNotExistException`→404 (update/preview, fallback — não deveria acontecer já que o banco é checado antes); `InvalidRenderingParameterException`/`MissingRenderingAttributeException`→422 (preview); qualquer outra `SesException`→422 com a mensagem da AWS | resolvida | Nomes de classe confirmados no jar já resolvido localmente (`software.amazon.awssdk:ses:2.49.1`, pacote `...ses.model`), não supostos. |
| Formato da resposta do `TestRenderTemplate` | AWS devolve `RenderedTemplate` como **uma string MIME crua** (cabeçalhos `Date`/`Message-ID`/`Subject`/`MIME-Version`/`Content-Type` + corpo) — não como `{subject, body}` já separados. Parsear com Jakarta Mail (`jakarta.mail:jakarta.mail-api` + `org.eclipse.angus:angus-mail` como implementação, versões exatas a confirmar contra o Maven Central na implementação) construindo um `MimeMessage` a partir do texto e lendo `getSubject()`/o conteúdo — não regex manual (cabeçalho pode vir com *folding*/codificação `quoted-printable` para caracteres não-ASCII) | resolvida | Verificado via documentação oficial da AWS nesta sessão (não suposto) — formato muda a implementação do endpoint de preview, mas não o contrato já escrito em `docs/openapi-email-service.yaml` (`TemplatePreviewResponse` continua `{subject, body}`; o parsing fica inteiramente dentro do serviço). |
| Identificação do cliente (esqueleto de API-KEY) | `ApiKeyAuthenticationFilter` (`jakarta.servlet.Filter`) rejeita com 401 header ausente/vazio; um bean `@RequestScope` (`ClientIdentityResolver`) lê `X-API-Key` direto e devolve seu valor bruto como id do cliente | resolvida | Isola a resolução de identidade num único ponto de troca (spec.md, "Requisitos não-funcionais") — quando a validação real existir, só essa classe muda. |
| Framework de teste de aceite | Gherkin/Cucumber + Spring (`@SpringBootTest(webEnvironment=RANDOM_PORT)` + RestAssured), mesmo padrão de `app` (`CucumberSpringConfiguration`) | resolvida (sessão anterior) | Ver `spec.md`, "Cenários". |
| Serviço SES nos testes de aceite padrão | LocalStack já iniciado pelo `docker-compose.yml`; a suíte chama sua API real, sem mock e sem Testcontainers. A rejeição de sintaxe inválida especificamente pelo Amazon SES não é simulada pelo LocalStack e está adiada para a Issue #104 (Iteração 6). | resolvida para a suíte padrão; validação AWS adiada | LocalStack exercita o fluxo de integração e preview sem credenciais AWS. Os dois cenários que dependem de validação de sintaxe pela AWS usam `@requires-real-ses` e ficam fora da execução padrão até serem validados contra SES real. |
| Banco nos testes de aceite | PostgreSQL dedicado `db-email-service`, iniciado pelo `docker-compose.yml`; sem H2 | resolvida (spec 05-028) | Segue a decisão da spec 05-028 de usar PostgreSQL real também nos testes. A suíte requer o serviço Compose previamente iniciado e não cria containers por conta própria. |
| Inicialização de infraestrutura nos testes | `db-email-service` e `localstack` pré-iniciados via Docker Compose; nenhum Testcontainers | resolvida (spec 05-028) | Mantém o mesmo mecanismo de infraestrutura usada pelo serviço e evita que a suíte tente descobrir/acessar Docker pelo cliente Java de Testcontainers. |
| `docker-compose.yml`: LocalStack dedicado ou reaproveitar o existente? | Reaproveita o serviço `localstack` já existente (SES já habilitado via `SERVICES=...,ses,...`) | resolvida | Um único LocalStack serve `email-lambda` e `email-service` sem conflito — nomes de template são namespaced por cliente, nomes de fila/lambda não colidem com nada que `email-service` usa. |

## Estrutura de módulos/pacotes

```
email-service/
  pom.xml
  src/main/java/dev/leilaalgarve/jogoacoes/emailservice/
    EmailServiceApplication.java
    auth/
      ApiKeyAuthenticationFilter.java
      ClientIdentityResolver.java
    template/
      EmailTemplate.java              (entidade JPA -- nome só coincide com o enum homônimo de app/, sem relação)
      EmailTemplateRepository.java
      TemplateService.java            (orquestra: valida duplicidade -> chama SES -> persiste)
      SesTemplateClient.java          (encapsula CreateTemplate/UpdateTemplate/TestRenderTemplate + mapeamento de exceções)
      MimeRenderedTemplateParser.java (parseia o RenderedTemplate cru em subject/body)
      TemplateController.java         (implementa a interface gerada a partir do OpenAPI)
    common/
      ApiExceptionHandler.java        (mesmo papel do de app/, adaptado às exceções deste módulo)
  src/main/resources/
    application.yml / application-docker.yml / application-production.yml
    db/migration/V1__create_email_template_table.sql
    (sem migration H2; testes e execução Docker usam db/migration no PostgreSQL)
  src/test/java/dev/leilaalgarve/jogoacoes/emailservice/
    CucumberSpringConfiguration.java
    template/steps/RegisterTemplatesSteps.java
  src/test/resources/
    application.yml (PostgreSQL `localhost:5433` + LocalStack `localhost:4566` via Compose)
    features/register_templates.feature (já escrito)
```

Fora do módulo, mudam:

- `pom.xml` (raiz): `<modules>` ganha `<module>email-service</module>`.
- `docker-compose.yml`: novo serviço `db-email-service` (Postgres, mesmo padrão de `db`) e novo
  serviço `email-service` (Spring Boot, `depends_on: db-email-service, localstack`).
- `docker/postgres-email-service/init/01-roles.sql`: novo, mesmo conteúdo de
  `docker/postgres/init/01-roles.sql` trocando os nomes dos papéis/banco.

## Riscos e trade-offs

- **Namespacing `<client_id>__<name>` com `client_id` sendo a API-KEY bruta (esqueleto)**: se a
  chave em texto puro contiver caracteres que o SES não aceita em `TemplateName` (SES só aceita
  `[a-zA-Z0-9_-]`), a chamada `CreateTemplate` falha mesmo com um `body`/`subject` válidos. Nesta
  spec isso não é tratado (chaves de teste serão strings simples alfanuméricas); quando a
  validação real de API-KEY existir, o id do cliente vira um valor controlado por nós
  (`service_name` de uma tabela própria, não a chave em si) e o problema desaparece sozinho.
- **MIME parsing do preview**: depende de uma biblioteca nova (`angus-mail`) só usada neste
  endpoint — risco pequeno (API estável, bem documentada), mas é peso extra no módulo só por
  causa do formato de resposta do `TestRenderTemplate`. Alternativa descartada: devolver a string
  MIME crua ao cliente — rejeitada por quebrar o contrato `{subject, body}` já escrito em
  `docs/openapi-email-service.yaml` e empurrar o parsing para cada consumidor.
- **Dois LocalStack diferentes no fluxo** (um para `email-service` registrar templates, outro
  seria o que `email-lambda` usa para efetivamente enviar) **são o mesmo container** nesta spec
  — fica fora de escopo verificar que um template registrado por `email-service` é de fato o que
  `email-lambda`/`SendTemplatedEmail` usaria num envio real (isso é a spec seguinte do Serviço de
  E-mail, "envio").
- **`db-email-service` como container Postgres separado** duplica a imagem `postgres:16` já
  rodando como `db` — custo de memória/disco extra em desenvolvimento local, aceito pela mesma
  razão que `deployo-api-key` também roda seu próprio Postgres: isolamento real, não só lógico,
  é o que o roadmap da Iteração 5 pede como critério de serviço independente.
