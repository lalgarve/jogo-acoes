# Plan: Cobertura de testes do `app` depois da PR #120

Traduz `spec.md` em decisões técnicas. Valida contra `memory/constitution.md` ("CI e cobertura de
testes", "Testes: preferir real a fake sempre que der", "Testes exigem a infraestrutura de pé" e
"Dados de teste: Object Mother + Test Data Builder").

## Contexto técnico

Conferido no branch da PR #120 (`claude/project-thread-0ooceb`, commit `d5970d4`) em 2026-10-06:

- **JaCoCo 0.8.15** em `app/pom.xml`. O `report` roda na fase `test` e o `check` (LINE ≥ 0,80,
  `BUNDLE`) na fase `verify`. O `<excludes>` fica na `<configuration>` do plugin, então vale
  para as três execuções (`prepare-agent`, `report` e `check`). Hoje ele exclui
  `dev/leilaalgarve/jogoacoes/api/**` e `org/openapitools/**`.
- **Duas execuções do openapi-generator** em `app/pom.xml`: a do contrato do `app`
  (`apiPackage` `dev.leilaalgarve.jogoacoes.api`) e a do cliente do email-service
  (`apiPackage` `dev.leilaalgarve.jogoacoes.email.client.api`, `modelPackage` `...api.model`,
  `configPackage` `...api.configuration`). Só a primeira está excluída.
- **CI** (`.github/workflows/ci.yml`): `mvn -B -pl app -am verify` e depois
  `madrapps/jacoco-report@v1.7` lendo `app/target/site/jacoco/jacoco.xml`. Nenhum
  `upload-artifact`.
- **`EmailServiceGateway`** (`app/.../email/client/`): quatro métodos públicos. Os de leitura e
  os idempotentes passam por `retrying` (até 3 tentativas, 200 ms entre elas, só para
  `EmailServiceUnavailableException`). `translate` transforma 401 em
  `EmailServiceAuthenticationException`, 5xx/sem conexão em `EmailServiceUnavailableException` e
  qualquer outro status em `EmailServiceRejectedException`.
- **`EmailServiceClientIntegrationTest`** já sobe o contexto com
  `email-service.base-url=http://localhost:8082/api` e `email.sender=email-service`, e já cobre
  upsert repetido de template existente, preview válido e template inexistente.
- **`PlayerManagementService.removePlayer`**: `findParticipation` busca por
  `findByIdAndCompetition_Id` e lança `PlayerNotFoundException` antes do `delete` e do
  `auditLogService.record`.
- **`EntryRequestService.requestEntry`**: confere a competição (privada → `CompetitionNotFoundException`)
  e depois o e-mail (`null`/em branco → `EntryRequestValidationException`). Só então verifica o
  captcha, então E1/E2 nunca chegam ao `AltchaCaptchaVerifier`.
- **Apoio de teste** em `common/testsupport/`: `CompetitionFixtures` (`publicCompetition()`,
  `privateCompetition()`), `UserMother` e `TestEmails.unique(...)`.

## Decisões de arquitetura

| Pergunta | Decisão | Status | Raciocínio |
|---|---|---|---|
| Como tirar o cliente gerado da contagem? | `<exclude>dev/leilaalgarve/jogoacoes/email/client/api/**</exclude>` no `<excludes>` que já existe, com o comentário reescrito para citar as duas execuções do gerador | resolvida | O padrão cobre `api`, `api.model` e `api.configuration` de uma vez e não pega as classes escritas à mão de `email.client`, que ficam um nível acima. Constitution, "CI e cobertura de testes". |
| Excluir por padrão de nome (`**/api/**`) em vez de pacote explícito? | Não. Pacote explícito | resolvida | Um padrão genérico também pegaria algum pacote `api` escrito à mão no futuro, sem ninguém notar. |
| Como publicar o HTML no CI? | `actions/upload-artifact@v4`, `name: jacoco-app`, `path: app/target/site/jacoco/`, `if: always()`, logo depois de "Run app tests with coverage" | resolvida | `always()` para publicar também quando o `check` derruba o build, que é justamente quando o relatório mais importa (C2). Retenção padrão do GitHub. |
| A métrica do comentário da PR muda? | Não | resolvida | Fora de escopo na spec. C3 exige linhas e instruções ≥ 80%. |
| Onde validar nome de template vazio/nulo (D1)? | Um método privado `requireName(String)` no `EmailServiceGateway`, chamado na primeira linha de `sendEmail`, `findTemplate`, `preview` e `upsertTemplate` (este com `Objects.requireNonNull`-equivalente para o template, lançando `IllegalArgumentException`) | resolvida | Validar antes do `retrying` e do `try` garante que nenhuma chamada HTTP acontece. `IllegalArgumentException`, e não `NullPointerException`, para que nome nulo e nome em branco caiam no mesmo tipo de erro. |
| `ApiExceptionHandler` precisa tratar `IllegalArgumentException`? | Não | resolvida | O nome nunca vem do cliente HTTP do `app`: vem de `EmailTemplate`/`EmailTemplateSynchronizer`. Uma `IllegalArgumentException` aqui é erro de programação e deve aparecer como 500. |
| Onde ficam os testes novos do gateway? | `EmailServiceClientIntegrationTest`, que já tem o contexto certo (base URL real, `email.sender=email-service`) | resolvida | Uma classe nova com a mesma `@SpringBootTest` duplicaria configuração e criaria outro contexto Spring no cache. D3: dependências reais. |
| Como provar "sem nova tentativa" (P1, P2, P4)? | `assertThat(Duration)` menor que `RETRY_WAIT_MILLIS` (200 ms), medido em volta da chamada | resolvida | Com uma nova tentativa, a espera mínima seria 200 ms. O preview no LocalStack responde bem abaixo disso. Ver riscos. |
| Como gerar o nome único do U1? | `"coverage-" + UUID.randomUUID()` | resolvida | O email-service não tem `DELETE /templates/{name}` (spec, "Requisitos funcionais"), então o nome precisa ser novo a cada execução. |
| Onde ficam os testes de `removePlayer` e `requestEntry`? | Classes novas `competition/PlayerManagementServiceIntegrationTest` e `competition/EntryRequestServiceIntegrationTest`, com `@SpringBootTest` (sem web), usando `CompetitionFixtures` e o Postgres do perfil `docker` | resolvida | Mesmo padrão de `common/logging/*IntegrationTest`. D3: Postgres real, sem mock de repositório. |
| `removePlayer` precisa de usuário logado? | Não | resolvida | O caminho testado lança antes de `currentUserService.currentUser()`. |
| Como provar "nenhum e-mail enviado" em E1/E2? | O contexto padrão dos testes usa `email.sender=stub`, então não pode haver nenhuma linha em `sent_email` com o e-mail do teste. Para E1 (`null`), conferir que a contagem de `sent_email` e de participações da competição não muda | resolvida | Constitution, "Testes: preferir real a fake": o stub grava o que enviaria, para poder conferir por asserção. |
| Os testes de D2 (S1, S2, S4) verificam a entrega no SES? | Não. Só o id devolvido | resolvida | D2 decide registrar o comportamento atual do gateway. Esperar a falha assíncrona no `email-lambda` deixaria o teste lento e frágil sem testar nada do `app`. |

## Estrutura de módulos/pacotes

```
app/pom.xml                                                     + exclude email/client/api/**; comentário
.github/workflows/ci.yml                                        + upload-artifact jacoco-app
app/src/main/java/.../email/client/EmailServiceGateway.java     + requireName / template não nulo (D1)
app/src/test/java/.../email/client/EmailServiceClientIntegrationTest.java
                                                                + S0–S4, F1–F2, U1–U5, P0–P4
app/src/test/java/.../competition/PlayerManagementServiceIntegrationTest.java   (novo)
app/src/test/java/.../competition/EntryRequestServiceIntegrationTest.java       (novo)
```

## Riscos e trade-offs

- **O limite de 200 ms em P1/P2/P4 pode ficar apertado** num runner lento do CI. Se falhar sem
  ter havido nova tentativa, a alternativa é contar as chamadas pelo log do
  `EmailServiceLoggingAspect` em vez de medir tempo. Decidir na implementação se acontecer.
- **Os templates `coverage-*` do U1 se acumulam** no email-service local entre execuções. No CI
  não, porque o `docker compose down -v` apaga o volume. Localmente, quem incomodar faz o mesmo.
- **O ambiente local desta sessão já tem um template de nome `""`** no email-service, criado
  pela investigação de 2026-10-06. Ele não afeta os testes (U2 confere o tipo da exceção, não a
  ausência do template) e some com `docker compose down -v`.
- **S1, S2 e S4 passam com um comportamento ruim.** É o que D2 decidiu: o teste documenta, não
  aprova. Quando a validação de `templateData` existir (Issue própria), esses testes mudam.
- **A exclusão sozinha já resolve C3.** Os testes novos têm valor próprio (casos-limite e o 0% do
  `EmailServiceRejectedException`), mas não são eles que tiram a cobertura de baixo do piso. Por
  isso a exclusão vem primeiro em `tasks.md`.
