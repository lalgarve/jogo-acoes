# Spec: Padronizar e-mails de teste para o simulador de caixa de entrada do Amazon SES

**Status:** rascunho
**Issue:** —
**Iteração:** iteration-5

## Resumo

Todo endereço de e-mail usado pela suíte de testes automatizados (`app/`, `email-lambda/` e a
suíte blackbox em Python, spec 05-015) passa a usar o domínio do *mailbox simulator* do Amazon
SES (`simulator.amazonses.com`), no formato `success+<qualificador>@simulator.amazonses.com`
— nunca mais `@example.com` ou outro domínio fictício.

## Motivação

Hoje toda a suíte roda contra infraestrutura simulada — H2 no perfil `sandbox`, LocalStack para
SQS (`docker-compose.yml`) e para SES via Quarkus Dev Services/Testcontainers
(`email-lambda`) — então nenhum e-mail de teste realmente sai para a internet, e usar
`@example.com` "funciona" sem consequência aparente.

Mas `example.com` é só um domínio reservado para documentação (RFC 2606) — sem MX de verdade,
sem qualquer relação com a AWS. Se algum dia um desses fluxos alcançar o SES de verdade (uma
execução manual contra AWS real, fora do LocalStack, ou o ambiente `blackbox` — spec 05-014 —
rodando sem a sobreposição de infraestrutura simulada), um envio para `@example.com` pode ser
tratado como *bounce*, prejudicando a reputação de envio da conta — a mesma preocupação já
registrada na Issue #24 ("Reduce SES bounce rate"), hoje superada só do lado do produto (a
validação de MX/domínio descartável passa a ser responsabilidade do futuro Serviço de E-mail),
não do lado dos próprios testes.

O Amazon SES já resolve exatamente esse problema com endereços de simulação garantidos (o
*mailbox simulator*): `success@simulator.amazonses.com` sempre produz uma entrega simulada de
sucesso, sem de fato entregar a nenhuma caixa postal e sem contar como *bounce*/*complaint*
para a reputação da conta. O simulador respeita *plus addressing* (RFC 5233):
`success+qualquer-coisa@simulator.amazonses.com` se comporta exatamente como
`success@simulator.amazonses.com` — o texto depois do `+` é só um identificador, preservado
nos metadados da entrega simulada — o que permite manter, para cada teste, o mesmo endereço
único que hoje `@example.com` já garante (evitar colisão em colunas `UNIQUE`, diferenciar
cenários), sem abrir mão da garantia de sucesso.

## Cenários (comportamento esperado)

Não aplicável — troca de valores literais em código de teste existente, sem `.feature` novo
nem mudança de comportamento de negócio (mesmo padrão das specs 05-006/05-008).

## Requisitos funcionais

- Todo endereço de e-mail usado por um teste automatizado como "e-mail válido de teste" (não
  como valor propositalmente inválido, ver "Fora de escopo") passa a ter domínio
  `simulator.amazonses.com`, no formato `success+<qualificador>@simulator.amazonses.com`.
- O `<qualificador>` mantém a mesma função que o prefixo/UUID já cumpre hoje (diferenciar
  endereços entre cenários, evitar colisão em colunas `UNIQUE` como `app_user.email`) — só o
  domínio muda; a lógica de geração de um endereço único por cenário continua a mesma.
- Um helper único, no módulo `app` (`common/testsupport`), centraliza o formato — sem literais
  `"success+" + x + "@simulator.amazonses.com"` espalhados pelos ~17 arquivos que hoje usam
  `@example.com` (`UserMother`, `CompetitionMother`, `RequestCompetitionEntrySteps`,
  `ManageCompetitionPlayersSteps`, `LoginSteps`, e os demais listados em `plan.md`) — mesmo
  raciocínio de reutilização já aplicado a esses fixtures.
- **Exceção explícita, fora de `src/test`**: `BlackboxDataSeeder.ADMIN_EMAIL` (spec 05-014,
  `app/src/main`) também muda — existe só para sustentar o ambiente de teste `blackbox`
  (nunca ativo fora do perfil `blackbox`), e seu valor passa pelo mesmo pipeline real de envio
  de e-mail (`SqsEmailSender`, já que `blackbox` empilha sobre `docker`) que qualquer endereço
  de teste passaria. `blackbox-tests/features/steps/public_competition_entry_steps.py` (spec
  05-015) precisa da mesma troca, mantendo os dois lados sincronizados como já são hoje.
- `email-lambda/src/test` (módulo Quarkus, sem acesso ao `common/testsupport` do `app`) segue
  o mesmo formato, mas como literal direto — uma única ocorrência hoje não justifica um helper
  próprio nesse módulo.

## Requisitos não-funcionais

- Nenhuma mudança de comportamento de produção fora da exceção explícita acima
  (`BlackboxDataSeeder.ADMIN_EMAIL`) — o restante é só troca de literais em código de teste
  (`src/test`), sem tocar `EntryRequestService`/`EmailSender`/qualquer lógica de envio.
- Suíte completa (`mvn test` em `app/` e `email-lambda/`, `behave`/`pytest` em
  `blackbox-tests/`) continua verde depois da troca — nenhum teste depende do domínio literal
  `example.com` em si, só do formato de um e-mail válido e da unicidade entre cenários.

## Fora de escopo

- Usar os outros endereços do simulador (`bounce@`, `complaint@`, `ooto@`,
  `suppressionlist@simulator.amazonses.com`) para testar esses cenários — por enquanto todo
  e-mail de teste é do tipo sucesso; testar bounce/complaint fica para quando houver essa
  necessidade.
- Endereços propositalmente inválidos usados para testar validação/rejeição (ex.:
  `"not-an-email"` em `RequestCompetitionEntrySteps`) — continuam como estão; o requisito é
  sobre endereços que representam um e-mail *válido* de teste, não sobre valores inválidos de
  propósito.
- Exemplos de e-mail em `docs/openapi.yaml` (contrato/documentação da API, não código de
  teste) — fora do escopo desta spec.
- Migrar a suíte para de fato enviar contra o SES real (fora do LocalStack) — esta spec só
  prepara os endereços para quando/se isso acontecer, não muda a infraestrutura usada hoje.

## Decisões em aberto

Nenhuma — nome/local do helper de geração de endereço, formato exato do qualificador, e o
alcance da exceção sobre `BlackboxDataSeeder.ADMIN_EMAIL` resolvidos em `plan.md`.
