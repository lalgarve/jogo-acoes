# Plan: pedido de link de login não deixa rastro quando o e-mail não sai

Traduz `spec.md` em decisões técnicas. Valida contra `memory/constitution.md`.

## Contexto técnico

- `LoginController.requestLoginLink` (`loginsession`) orquestra o pedido sem transação própria.
  Cada passo abre e fecha a sua: `LinkService.invalidateActiveLinksFor`, `LinkService.create` e
  `AuditLogService.record` são `@Transactional` com a propagação padrão (`REQUIRED`). O
  `EmailSender.send` roda depois que os três já foram gravados.
- Com a propagação `REQUIRED`, os três passos entram numa transação que já existe. Basta um
  método `@Transactional` por fora para que uma exceção no envio desfaça tudo. Nenhum dos três
  precisa mudar.
- As exceções que o envio pode lançar são todas `RuntimeException`, então o Spring desfaz a
  transação sem configuração extra (`rollbackFor` não é necessário):
  `EmailServiceUnavailableException` (`503`), `RecipientDomainRejectedException` (`422`, spec
  05-047), `EmailServiceRejectedException` e `EmailServiceAuthenticationException`.
- O `RecipientDomainRejectedException` apareceu depois da Issue #135 (spec 05-047) e tem o mesmo
  defeito hoje: um `422` no pedido de link também invalida os links anteriores e deixa o link novo
  ativo. A correção cobre os dois casos, porque a `spec.md` fala de qualquer falha no envio.
- `EmailServiceEmailSender` chama o `email-service` antes de gravar `sent_email` (spec 05-034),
  então envio que falha já não deixa linha. Com a transação, a linha de um envio que deu certo
  também só é gravada no commit.
- Os outros fluxos de e-mail já seguem esse padrão: `EntryRequestService`, `CompetitionService`
  e `PlayerManagementService` chamam o `EmailSender` dentro do próprio `@Transactional`.
- `ApiExceptionHandler` já converte `EmailServiceUnavailableException` em `503` e
  `RecipientDomainRejectedException` em `422`. Não muda.
- Testes que tocam o fluxo hoje:
  - `login.feature` / `LoginSteps`, cenário "Requesting a new login link invalidates the previous
    one", no contexto Cucumber com o `StubEmailSender`;
  - `AuditLoggingIntegrationTest`, que chama `loginController.requestLoginLink(...)` direto;
  - `RecipientDomainRejectionIntegrationTest.aLoginLinkToADomainThatTakesNoEmailIsRejected`,
    contra o `email-service` real (só confere o `422`).

## Decisões de arquitetura

| Pergunta | Decisão | Status | Raciocínio |
|---|---|---|---|
| Onde fica a fronteira da transação | Serviço novo `LoginLinkRequestService` em `loginsession`, com um método `@Transactional` `requestLoginLink(String email, String returnTo)` que faz todo o pedido (achar o usuário, invalidar, criar, auditar, enviar). O `LoginController.requestLoginLink` só chama o serviço e devolve `202`. | proposta | É o que a Issue sugere e o padrão dos outros fluxos de e-mail: a orquestração e a transação ficam num serviço, não no controller. Um serviço próprio, e não um método no `LoginLinkSessionService`, porque este cuida de estabelecer a sessão ao consumir o link (responsabilidade diferente). |
| `@Transactional` direto no controller | Não. | proposta | Funcionaria, mas manteria a orquestração no controller, que é justamente o segundo problema que a Issue aponta. Nenhum outro controller do projeto é transacional. |
| Assinatura do método | Recebe `email` e `returnTo` (cru, ainda não validado) e não devolve nada. A validação de `returnTo` (`ReturnToValidator`) passa para o serviço. | proposta | O controller fica sem regra nenhuma, e o serviço não depende do DTO gerado do contrato (`RequestLoginLinkRequest`). Não há o que devolver: a resposta é `202` tanto para e-mail conhecido quanto desconhecido. |
| E-mail desconhecido | O serviço volta sem fazer nada, dentro da mesma transação (só leitura). | proposta | Mantém o comportamento de não revelar se o endereço existe. Uma transação só de leitura nesse caminho não custa nada relevante. |
| Ordem dos passos | Mantida: invalidar, criar, auditar, enviar. | proposta | Com tudo na mesma transação, a ordem não muda o resultado de uma falha. Enviar por último segura a conexão do banco o menor tempo possível antes da chamada HTTP. |
| Envio dentro da transação | Sim, como nos outros fluxos. | resolvida | Já aceito na spec 05-034 ("Riscos e trade-offs": "Envio dentro da transação"), com o tempo limite de 5 s do cliente limitando o pior caso. Enviar depois do commit está fora de escopo (`spec.md`). |
| Teste do caminho `503` (o que a Issue pede) | Cenário novo em `login.feature` (texto na `spec.md`). O passo `Given the e-mail service is unavailable` liga um `EmailSender` de teste, em `app/src/test`, que lança `EmailServiceUnavailableException` enquanto estiver ligado e, desligado, delega ao `StubEmailSender`. É registrado como `@Primary` no contexto Cucumber e desligado num `@After` do Cucumber. | em aberto | Não há como derrubar o `email-service` real no meio de um cenário: a suíte Cucumber usa o stub, e um `app` apontando para um `email-service` fora do ar nem sobe (o `EmailTemplateSynchronizer` falha no `ApplicationReadyEvent`). O fake lança a mesma exceção que o `EmailServiceGateway` lança quando a conexão falha, que já é testada contra o serviço real em `EmailServiceClientIntegrationTest`. É o primeiro dublê de teste do `app`, por isso fica para a Leila decidir (ver "Decisões em aberto"). |
| Teste com a infraestrutura real | Estender `RecipientDomainRejectionIntegrationTest.aLoginLinkToADomainThatTakesNoEmailIsRejected`: o usuário já tem um link ativo antes do pedido; depois do `422`, esse link continua sem `invalidated_at`, não há link novo para o usuário e não há `LOGIN_LINK_ISSUED` dele. | proposta | Prova o desfazer contra o `email-service` real (domínio `missing.test` no DNS de teste), sem dublê (constitution, "Testes: preferir real a fake sempre que der"). Usa o mesmo mecanismo do `503`: as duas são `RuntimeException` lançadas pelo `EmailSender` dentro da transação. |
| Como conferir "nenhum link novo" e "nenhuma auditoria" | Pelo usuário do cenário, que é único por teste (`UserMother`): `LinkRecordRepository` sem nenhum registro dele além do link prévio, e `LogRepository` sem `LOGIN_LINK_ISSUED` com esse usuário. | proposta | As tabelas são compartilhadas entre os testes (já causou o Issue #41). Filtrar pelo usuário do teste evita contar linhas de outros testes. Se faltar um método de consulta no repositório, usar `findAll()` filtrado, como já faz o `AuditLoggingIntegrationTest`. |
| Exceções novas | Nenhuma. | resolvida | O caminho de erro usa exceções e tratadores que já existem. Os dois branches que esta correção passa a exercitar no fluxo de login (`EmailServiceUnavailableException` e `RecipientDomainRejectedException` lançadas dentro da transação) ganham um teste cada, pelas duas linhas acima. |

Decisões marcadas "em aberto" viram commit `decision:` quando resolvidas (ver
`memory/constitution.md`), atualizando esta tabela no mesmo commit.

## Estrutura de módulos/pacotes

- `app/src/main/java/.../loginsession/LoginLinkRequestService.java` (novo): a orquestração que
  hoje está no controller, com `@Transactional`.
- `app/src/main/java/.../loginsession/LoginController.java`: perde as dependências que só o
  pedido de link usava (`UserService`, `EmailSender` e `AuditLogService`) e passa a depender de
  `LoginLinkRequestService`. `LinkService` fica, porque `consumeLoginLink` e
  `completeRegistration` continuam usando. O Javadoc da classe deixa de dizer que
  `requestLoginLink` "fica aqui".
- `app/src/test/resources/features/login.feature`: o cenário da `spec.md`.
- `app/src/test/java/.../loginsession/LoginSteps.java`: os passos novos
  (`the e-mail service is unavailable`, `the system reports that the e-mail could not be sent`,
  `the previous login link can still be used to log in`, `no new login link was issued`).
- `app/src/test/java/.../common/` (se a decisão em aberto for pelo fake): o `EmailSender` de
  teste ligável e a configuração que o registra no contexto Cucumber.
- `app/src/test/java/.../email/RecipientDomainRejectionIntegrationTest.java`: o teste estendido.

Nenhuma mudança em `LinkService`, `AuditLogService`, `EmailSender`, `ApiExceptionHandler`,
`docs/openapi.yaml` ou migrations. O `ArchitectureTest` não é afetado: `loginsession` já depende
de `link`, `log`, `user` e `email`.

## Verificações (antes da correção, falhando)

1. O cenário novo de `login.feature` falha no código atual: o link prévio fica com
   `invalidated_at`, há um link novo ativo e uma entrada `LOGIN_LINK_ISSUED`.
2. O teste estendido de `RecipientDomainRejectionIntegrationTest` falha no código atual pelos
   mesmos motivos, com `422`.
3. Depois da correção, os dois passam, e continuam verdes "Requesting a new login link
   invalidates the previous one", os demais cenários de `login.feature`,
   `redirect_after_login.feature` e `AuditLoggingIntegrationTest`.
4. Suíte completa do `app` verde (`mvn -pl app -am verify`, com a infraestrutura do Compose de
   pé e a chave de teste restaurada, como no `README.md`).

## Riscos e trade-offs

- **Conexão do banco presa durante o envio.** O pedido de link passa a segurar uma conexão do
  pool enquanto espera o `email-service`, até 5 s no pior caso. É o mesmo custo dos outros fluxos
  de e-mail, já aceito na 05-034.
- **E-mail de um link desfeito.** Se o `email-service` aceitar o envio e a resposta se perder (ou
  o commit falhar depois), o link é desfeito e o e-mail com ele já saiu. Fora de escopo
  (`spec.md`); a idempotência de `POST /emails` ([Issue #205](https://github.com/lalgarve/jogo-acoes/issues/205))
  não resolve esse caso.
- **Dublê de teste.** Se a decisão em aberto for pelo fake, o cenário de `503` não passa pela
  chamada HTTP real. O mapeamento de falha de conexão para `EmailServiceUnavailableException`
  continua coberto pelo `EmailServiceClientIntegrationTest`, e o desfazer é coberto também pelo
  teste real do `422`.

## Decisões em aberto

1. **Dublê para o cenário de `503`.** Opções:
   - **A (recomendada):** o `EmailSender` de teste ligável descrito na tabela, só em
     `app/src/test`, mais o teste real do `422`. Cobre exatamente o critério da Issue (`503`) e
     prova o desfazer também contra a infraestrutura real.
   - **B:** só o teste real do `422`, sem dublê e sem cenário Cucumber. O critério da Issue passa
     a ser cumprido pelo mesmo mecanismo com outra exceção, e a `spec.md` e a Issue ficam com o
     critério ajustado para "o envio falha" em vez de "`email-service` inacessível".

   A recomendação é A porque o `503` é o caso descrito na Issue e o mais provável em produção, e
   o dublê fica restrito ao código de teste (a regra de não misturar código de teste em módulo de
   produção não é violada).
