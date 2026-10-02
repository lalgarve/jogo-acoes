# Plan: Remover a leitura de e-mail de `app/` — Python lê o LocalStack direto

Traduz `spec.md` em mudanças arquivo por arquivo. Valida contra `memory/constitution.md`.

## Contexto técnico

`GET {LOCALSTACK_URL}/_aws/ses` (verificado nesta sessão, Issue #87/#88) devolve
`{"messages": [...]}`, cada mensagem com `Id`, `Region`, `Timestamp`, `Source`, `Subject`,
`Destination.ToAddresses` (lista), `Body.html_part`/`Body.text_part`. O parâmetro `?email=` da
própria API filtra pelo campo `Source` (remetente) — confirmado contra a spec oficial do
LocalStack (`openapi/emulators/localstack-spec-latest.yml`) nesta sessão — então filtrar por
destinatário precisa ser feito no lado do cliente. `messages` vem em ordem de envio, então o
último elemento que bate com o destinatário é sempre o mais recente.

Todo template que manda link (`login-link-request.html`, `login-link-invite.html`, etc.) gera
`<a href="/login-links/{token}">` ou `.../registration` — mesmo formato em todos, então uma
única regex cobre qualquer template sem precisar saber qual foi usado.

## Decisões de arquitetura

| Pergunta | Decisão | Raciocínio |
|---|---|---|
| Onde fica o endereço do LocalStack | Variável de ambiente `LOCALSTACK_URL` (padrão `http://localhost:4566`), lida uma vez dentro de `blackbox_fixtures.py` — não é mais parâmetro de função | Diferente de `base_url` (que varia por dispositivo/cliente simulado), o LocalStack é um endereço fixo do ambiente de teste inteiro — não há razão pra threading esse valor por ~10 assinaturas de função (`flows.py`, steps, seeder) quando um valor de módulo já resolve, mesmo padrão de `ADMIN_EMAIL` já sendo uma constante fixa no mesmo arquivo. |
| Como filtrar por destinatário | List comprehension em Python sobre `messages`, checando `email in m["Destination"]["ToAddresses"]` | Mais simples que tentar usar `?email=` da API (filtra o campo errado) ou paginar/id — o volume de e-mails numa sessão de teste é pequeno o bastante pra filtrar em memória sem custo real. |
| Como extrair o link do corpo | Regex `href="([^"]*login-links[^"]*)"` sobre `Body.html_part` | Pedido explícito do usuário ("um regex ou algo semelhante"). Funciona igual pra link relativo ou absoluto (Issue #92 track à parte) porque captura o valor do atributo como está, sem assumir formato. |
| Como evitar que a caixa do LocalStack cresça sem limite | `last_email` apaga a mensagem encontrada por `Id` (`DELETE {LOCALSTACK_URL}/_aws/ses?id=<Id>`) antes de devolver — ler consome | Visto num artefato de referência consultado nesta sessão ("LocalStack SES: consultar e apagar e-mails") que o padrão usual é um `DELETE /_aws/ses` geral (sem filtro) em `@BeforeEach`/`@AfterEach` — mas isso apagaria mensagens de testes concorrentes rodando contra o mesmo LocalStack compartilhado. Apagar só o `Id` específico que o próprio teste encontrou é seguro porque cada teste já usa um endereço único (`success+<qualificador>-<uuid>@...`, spec 05-016) — nunca acha nem apaga a mensagem de outro teste. |
| O que fazer com o campo `template` de `LastEmail` | Remover — não recuperável da resposta do LocalStack (o par `Template`/`TemplateData` da API é do recurso nativo de *SES templates*, não usado aqui) | Nenhum call site lê esse campo hoje (busca em todo `blackbox-tests/` nesta sessão: só `.link`/`.sent_at` são lidos) — não é uma perda de cobertura, é remover campo morto que por acaso ficaria impossível de preencher. |
| `LastEmail` continua `@dataclass(frozen=True)`? | Sim, só com dois campos em vez de três | Nenhuma razão pra mudar a forma, só o conteúdo. |

## Mudanças arquivo por arquivo

### Apagar (2 arquivos, `app/`)

- `app/src/main/java/dev/leilaalgarve/jogoacoes/blackbox/BlackboxController.java`
- `app/src/main/java/dev/leilaalgarve/jogoacoes/blackbox/BlackboxSecurityConfigContributor.java`

### `app/src/test/java/.../blackbox/BlackboxProfileIntegrationTest.java`

Remove os dois métodos de teste do controller apagado (`lastEmailReturnsTheMostRecentLinkSentToAnAddress`,
`lastEmailReturnsNotFoundWhenNothingWasSentToTheAddress`), o helper `saveSentEmail`, o campo
`@Autowired SentEmailRepository`, o campo `@Value("${local.server.port}") port` (só usado pelas
chamadas RestAssured desses dois testes) e os imports que ficam órfãos
(`EmailTemplate`, `SentEmail`, `SentEmailRepository`, `RestAssured`, `LocalDateTime`,
`TestEmails.unique` — a checar um por um, já que `unique` pode ainda ser usado por outro teste
do arquivo). Mantém `captchaIsAlwaysAccepted` e `administratorIsSeededIdempotently` como estão.
Javadoc da classe: tira a menção a "três mecanismos", ajusta pra dois (captcha + seeder).

### `app/src/main/java/.../blackbox/BlackboxDataSeeder.java`

Só o Javadoc: remove o `{@link ...BlackboxController}` no comentário sobre `@Profile("blackbox")`
(referência a uma classe que deixa de existir) — nenhuma mudança de comportamento.

### `blackbox-tests/common/blackbox_fixtures.py` (reescrita)

```python
"""Fixed facts about the `blackbox` environment (spec 05-014) shared by the Cucumber step
definitions (features/steps/) and the data seeder (seed/, spec 05-018): the seeded
administrator's e-mail, and reading the link (and metadata) of the most recent e-mail sent to a
given address.

`last_email`/`last_email_link` read LocalStack's own SES message store directly
(`GET {LOCALSTACK_URL}/_aws/ses`) -- there is no Java code in the loop (spec 05-023 removed
`BlackboxController`/`GET /blackbox/last-email`, the app-side endpoint this used to call): the
link only ever mattered to this Python suite, and LocalStack already exposes the full rendered
e-mail (`POST /login-requests` always returns 202 with no body, so this is the only way to see
the link at all). Reading a message deletes it (by its own id, never a blanket clear) so the
store doesn't grow without bound over a long test session, and concurrent test runs never
collide (every address is already unique per test -- spec 05-016).
"""

import datetime
import os
import re

import httpx

ADMIN_EMAIL = "success+admin@simulator.amazonses.com"

LOCALSTACK_URL = os.environ.get("LOCALSTACK_URL", "http://localhost:4566")

# Every template that sends a link embeds it as <a href="...login-links...">. Matches whether
# the href is a relative path or a full absolute URL (Issue #92 tracks fixing which one it is).
_LINK_PATTERN = re.compile(r'href="([^"]*login-links[^"]*)"')


class NoEmailSentError(Exception):
    """Raised when no e-mail has ever been sent to the given address."""


from dataclasses import dataclass


@dataclass(frozen=True)
class LastEmail:
    link: str
    sent_at: datetime.datetime


def last_email(email: str) -> LastEmail:
    """Returns the most recent e-mail sent to ``email`` (link, and when it was sent -- used by
    the seeder, spec 05-018, to confirm the app is really running with the clock offset it was
    told about).

    Deletes the message from LocalStack's SES store before returning -- reading an e-mail
    consumes it, so a long test session doesn't accumulate messages forever. Safe under
    concurrent test runs: every address is already unique per test/scenario (spec 05-016), so
    the one message id this deletes could never belong to a different, concurrently-running
    test -- unlike a blanket `DELETE /_aws/ses` (no filter), which would also wipe out messages
    other tests haven't read yet.

    Raises ``NoEmailSentError`` if nothing has been sent to that address yet.
    """
    response = httpx.get(f"{LOCALSTACK_URL}/_aws/ses")
    response.raise_for_status()
    # LocalStack's own ?email= filter matches the *sender*, not the recipient (this project's
    # sender is always the same address), so recipient filtering happens here instead.
    matches = [
        message
        for message in response.json()["messages"]
        if email in message["Destination"]["ToAddresses"]
    ]
    if not matches:
        raise NoEmailSentError(f"No e-mail sent to {email} yet")

    latest = matches[-1]  # `messages` comes back in send order.
    html = latest["Body"]["html_part"] or ""
    link_match = _LINK_PATTERN.search(html)
    if link_match is None:
        raise RuntimeError(f"Could not find a login link in the e-mail sent to {email}")

    result = LastEmail(link=link_match.group(1), sent_at=datetime.datetime.fromisoformat(latest["Timestamp"]))
    httpx.delete(f"{LOCALSTACK_URL}/_aws/ses", params={"id": latest["Id"]}).raise_for_status()
    return result


def last_email_link(email: str) -> str:
    """Convenience wrapper over :func:`last_email` for callers that only need the link."""
    return last_email(email).link
```

(O `import` de `dataclasses` ficaria no topo do arquivo de verdade — separado aqui só por
clareza de diff.)

### `blackbox-tests/seed/flows.py` (4 call sites)

Cada ocorrência perde o argumento `base_url`:

- linha 38: `last_email(base_url, admin_email).link` → `last_email(admin_email).link`
- linha 136: `last_email(base_url, email).link` → `last_email(email).link`
- linha 147: `last_email(base_url, email).link` → `last_email(email).link`
- linha 158: `last_email(base_url, email).link` → `last_email(email).link`

Nenhuma assinatura de função muda (o parâmetro `base_url` de `admin_login`/
`public_entry_new_player`/etc. continua existindo — ainda é usado pra `new_client(base_url)`,
só não é mais repassado pra `last_email`).

### `blackbox-tests/seed/__main__.py` (1 call site)

- `verify_clock`, linha 67: `last_email(base_url, checked_email)` → `last_email(checked_email)`.
  Assinatura de `verify_clock` não muda (`base_url` continua vindo do argumento de linha de
  comando, só não é mais repassado aqui).

### `blackbox-tests/features/steps/manage_active_sessions_steps.py` (3 call sites)

- linha 59: `last_email_link(context.api_base_url, ADMIN_EMAIL)` → `last_email_link(ADMIN_EMAIL)`
- linha 82: `last_email_link(context.api_base_url, context.player_email)` → `last_email_link(context.player_email)`
- linha 106: `last_email_link(context.api_base_url, context.player_email)` → `last_email_link(context.player_email)`

### `blackbox-tests/features/steps/public_competition_entry_steps.py` (2 call sites)

- linha 39: `last_email_link(context.api_base_url, email)` → `last_email_link(email)`
- linha 87: `last_email_link(context.api_base_url, context.player_email)` → `last_email_link(context.player_email)`

### `blackbox-tests/tests/test_mailbox.py`

- linha 20: `last_email_link(API_BASE_URL, never_used_email)` → `last_email_link(never_used_email)`
- Remove a constante `API_BASE_URL` (linha 13) e o `import os` (linha 6) — nada mais no arquivo
  os usa.

### Documentação

- `README.md`: se houver menção a `GET /blackbox/last-email`, trocar pela explicação nova
  (Python lê `/_aws/ses` direto) — checar na implementação, não localizada uma referência clara
  nesta sessão além do que já documenta o pipeline de e-mail.
- `blackbox-tests/README.md`: linha(s) que hoje explicam a dependência de
  `GET /blackbox/last-email` (achadas nesta sessão: linhas ~25 e ~75, a conferir o texto exato
  na implementação) — atualizar pra refletir a leitura direta do LocalStack.

## Testes

- `BlackboxProfileIntegrationTest` (Java): dois testes a menos, os dois que sobram continuam
  cobrindo captcha + seeder sob o profile `blackbox` de verdade.
- `blackbox-tests/tests/test_mailbox.py`: mesmo teste único
  (`test_last_email_link_raises_when_nothing_was_sent_to_the_address`), só sem o argumento de
  URL — continua exercitando `NoEmailSentError` contra um LocalStack real (precisa de Docker,
  mesma limitação já documentada em specs anteriores).
- Um teste novo vale a pena pro comportamento de consumo: enviar dois e-mails pro mesmo endereço
  único, chamar `last_email` uma vez (deve devolver o mais recente e apagá-lo), chamar de novo
  (deve devolver o outro, não `NoEmailSentError` — confirma que só a mensagem lida foi apagada,
  não a caixa inteira). Além do cenário de borda já coberto (endereço nunca usado) — se a
  implementação encontrar outro (ex.: e-mail sem link, HTML malformado), registrar como achado,
  não assumir aqui.

## Validação manual (antes de fechar a Issue #84 nesta parte)

Repetir, contra o ambiente real (mesmo `docker compose up` já validado nas Issues #84/#87/#88):
1. Rodar `blackbox-tests` (`behave`/`pytest`) contra o `docker compose up` de verdade e confirmar
   que os cenários que dependem de `last_email_link` continuam passando.
2. Confirmar que `mvn -pl app -am test` continua verde sem as duas classes apagadas.
