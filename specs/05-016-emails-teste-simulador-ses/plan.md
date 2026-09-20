# Plan: Padronizar e-mails de teste para o simulador de caixa de entrada do Amazon SES

Traduz `spec.md` em decisões técnicas. Valida contra `memory/constitution.md`.

## Contexto técnico

Hoje `@example.com` aparece em 53 ocorrências espalhadas por 17 arquivos de teste do módulo
`app` (`common/testsupport/UserMother.java`/`CompetitionMother.java`, os passos Cucumber de
`competition/steps`/`link`, e vários testes JUnit de `email/`/`log`/`login`/`blackbox`), mais
uma ocorrência em `email-lambda/src/test` e uma em
`blackbox-tests/features/steps/public_competition_entry_steps.py` (spec 05-015). A maioria já
segue um padrão (`"prefixo-" + UUID.randomUUID() + "@example.com"`) para garantir um endereço
único por cenário — só o domínio precisa trocar, a necessidade de unicidade continua igual.

O Amazon SES documenta oficialmente o *mailbox simulator* (`simulator.amazonses.com`) com
*plus addressing* (RFC 5233): qualquer coisa depois de um `+` no endereço `success@` é
preservada como identificador, mas a entrega simulada continua sendo sempre de sucesso — é
esse mecanismo que permite manter um endereço único por teste sem abrir mão da garantia.

## Decisões de arquitetura

| Pergunta | Decisão | Status | Raciocínio |
|---|---|---|---|
| Formato do endereço | `success+<qualificador>@simulator.amazonses.com` | resolvida | *Plus addressing* do *mailbox simulator* do SES — sempre resulta em entrega simulada de sucesso, nunca conta como *bounce*/*complaint*, e preserva um identificador único por teste (mesma função que a UUID já cumpre hoje). |
| Onde centralizar a geração, no módulo `app` | Nova classe `common/testsupport/TestEmails.java`, dois métodos estáticos: `unique(String qualifier)` (`"success+" + qualifier + "-" + UUID.randomUUID() + "@simulator.amazonses.com"`, substitui o padrão `"prefixo-" + UUID.randomUUID() + "@example.com"` já usado hoje) e `fixed(String qualifier)` (`"success+" + qualifier + "@simulator.amazonses.com"`, para os poucos literais fixos como `"alice@example.com"`). | resolvida | Um único dono do domínio/formato — trocar de novo no futuro (ex. testar *bounce*) vira mudança num arquivo, não em ~17. Mesmo raciocínio de reutilização já aplicado a `UserMother`/`CompetitionFixtures`. |
| `email-lambda` (módulo Quarkus separado) | Literal direto (`"success+player@simulator.amazonses.com"`), sem helper | resolvida | Módulo Maven diferente, sem acesso a `common/testsupport` do `app`; uma única ocorrência não justifica dependência cruzada nem duplicar o helper. |
| `blackbox-tests/` (Python, spec 05-015) | Literal direto/pequena constante em `public_competition_entry_steps.py`, mesmo raciocínio | resolvida | Poucas ocorrências (a constante `ADMIN_EMAIL` e o e-mail dinâmico do jogador) — sem necessidade de módulo/abstração própria ainda. |
| `BlackboxDataSeeder.ADMIN_EMAIL` (spec 05-014, `app/src/main`) | Muda de `admin@blackbox.local` para `success+admin@simulator.amazonses.com`, mesmo nome de constante | resolvida | Existe só para sustentar o ambiente `blackbox` (nunca ativo fora desse perfil) e seu valor passa pelo pipeline real de envio (`SqsEmailSender`, já que `blackbox` empilha sobre `docker`) — mesma exposição a *bounce* que qualquer endereço de teste. `blackbox-tests/`' `ADMIN_EMAIL` muda junto, mantendo os dois lados sincronizados como já são hoje. |
| Endereços propositalmente inválidos (`"not-an-email"`) | Não mudam | resolvida | Não representam um e-mail válido de teste — são o próprio objeto do teste de validação/rejeição. |

## Estrutura de módulos/pacotes

- `app/src/test/java/dev/leilaalgarve/jogoacoes/common/testsupport/TestEmails.java` (novo) —
  `unique(String qualifier)`/`fixed(String qualifier)`.
- Arquivos que passam a chamar `TestEmails` em vez do literal `@example.com` (17, via `grep`
  atual): `common/testsupport/UserMother.java`, `common/testsupport/CompetitionMother.java`,
  `competition/steps/RequestCompetitionEntrySteps.java`,
  `competition/steps/CreateCompetitionSteps.java`,
  `competition/steps/ManageCompetitionPlayersSteps.java`, `competition/CompetitionLinkHandlerTest.java`,
  `link/LoginSteps.java`, `link/LinkServiceTest.java`, `login/LoginLinkHandlerTest.java`,
  `email/EmailContentRendererTest.java`, `email/StubEmailSenderTest.java`,
  `email/SqsEmailSenderTest.java`, `email/SqsEmailSenderDockerIntegrationTest.java`,
  `log/LogRepositoryTest.java`, `log/AuditLogServiceTest.java`,
  `log/AuditLoggingIntegrationTest.java`,
  `common/logging/QueueLoggingAspectIntegrationTest.java`,
  `blackbox/BlackboxProfileIntegrationTest.java`.
- `app/src/main/java/dev/leilaalgarve/jogoacoes/blackbox/BlackboxDataSeeder.java` (modificado)
  — só o valor de `ADMIN_EMAIL`.
- `email-lambda/src/test/java/dev/leilaalgarve/jogoacoes/email/lambda/EmailSendHandlerTest.java`
  (modificado) — literal direto.
- `blackbox-tests/features/steps/public_competition_entry_steps.py` (modificado) — constante
  `ADMIN_EMAIL` e o e-mail dinâmico do jogador (`context.player_email`).
- `README.md` (modificado) — a menção a `admin@blackbox.local` na seção "Ambiente de testes
  blackbox" atualizada para o novo endereço.

## Riscos e trade-offs

- **Mudar `BlackboxDataSeeder.ADMIN_EMAIL` depois da spec 05-014 já mesclada** é uma mudança
  em código de produção (`src/main`), ainda que só ativo no perfil `blackbox` — mitigado por
  ser só o valor da constante (nenhuma lógica muda) e pela suíte completa (`mvn test`) rodando
  de novo depois, incluindo `BlackboxProfileIntegrationTest`.
- **Cobertura da varredura**: a lista de 17 arquivos acima vem de um `grep` pontual — se algum
  `@example.com` novo for introduzido depois sem usar `TestEmails`, volta a divergir do
  padrão. Mitigado incluindo, no `tasks.md`, uma tarefa final de `grep -r "@example\.com"` para
  confirmar zero ocorrências restantes fora do que está listado em "Fora de escopo".
- **`success+<qualificador>@simulator.amazonses.com` só importa em ambientes com rota até o
  SES** (real ou LocalStack) — não muda em nada o comportamento de `StubEmailSender`/H2 hoje,
  que nunca validam o domínio do destinatário; o ganho é só de precaução para quando/se algum
  ambiente alcançar o SES de verdade.
