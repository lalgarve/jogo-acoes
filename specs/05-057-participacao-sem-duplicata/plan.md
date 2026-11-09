# Plan: convite e entrada em competição sem participação duplicada

Traduz `spec.md` em decisões técnicas. Valida contra `memory/constitution.md`.

## Contexto técnico

- Todos os caminhos que criam ou mudam uma participação estão no módulo `competition` do `app`:
  - `CompetitionService.create`: laço sobre `request.getEmails()` na competição privada, sem
    conferir repetição; `validate` lança `CompetitionValidationException` (`400`).
  - `PlayerManagementService.invitePlayers` (`POST /competitions/{id}/players`): cria uma
    participação por e-mail e convida cada uma, dentro de um `@Transactional`.
  - `PlayerManagementService.updateEmail`: já recusa e-mail de outro jogador da competição com
    `existsByCompetition_IdAndIdNotAndEmailIgnoreCase` e `PlayerValidationException` (`400`).
  - `EntryRequestService.requestEntry` (anônimo): busca com
    `findByCompetition_IdAndEmailAndStatusNot(competitionId, email, IN_COMPETITION)`, que
    diferencia maiúsculas e **ignora a participação `IN_COMPETITION`**. Um jogador que já está na
    competição e pede entrada de novo (cenário "Registered player requests entry again into a
    competition they already participate in", `login.feature`) ganha hoje uma segunda
    participação, e a primeira fica como está. Com o índice único, esse caminho quebraria.
  - Ainda em `requestEntry`: depois de enviar, a participação vai para `EMAIL_SENT` e grava
    `PARTICIPATION_STATUS_CHANGED`. Aplicado à participação `IN_COMPETITION`, isso a rebaixaria.
  - `EntryRequestService.confirmEntry` (logado): regrava `IN_COMPETITION`, `joinedAt = hoje` e
    auditoria a cada chamada; cria a participação de competição pública quando não existe.
  - `CompetitionLinkHandler.complete`: muda a participação existente (não cria), fora de escopo.
- Tabela `participation` (`V1__init_schema.sql`): sem restrição de unicidade. O e-mail é gravado
  como chegou.
- A última migration do `app` é `V9__index_participation_user_and_competition_creator.sql`.
- Pré-produção (constitution, "Status do sistema: pré-produção"): a migration não trata
  duplicatas já gravadas (decidido em 2026-10-25).
- Testes que tocam esses fluxos: `manage_competition_players.feature` /
  `ManageCompetitionPlayersSteps`, `create_competition.feature` / `CreateCompetitionSteps`,
  `request_competition_entry.feature` / `RequestCompetitionEntrySteps`, `login.feature`,
  `PlayerManagementServiceIntegrationTest` e `EntryRequestServiceIntegrationTest`. A suíte
  `blackbox-tests/` também convida e pede entrada.

## Decisões de arquitetura

| Pergunta | Decisão | Status | Raciocínio |
|---|---|---|---|
| Restrição no banco | `V10__unique_participation_email_per_competition.sql`: `CREATE UNIQUE INDEX participation_competition_email_uidx ON participation (competition_id, lower(email))`. | proposta | Comparar sem diferenciar maiúsculas é o que a `spec.md` pede e o que `updateEmail` já faz. Índice de expressão porque o e-mail continua gravado como foi digitado (é o que aparece para o administrador). |
| Guardar o e-mail em minúsculas | Não. | proposta | Mudaria o que o administrador vê e os e-mails de teste já usados nas suítes; o índice de expressão resolve a comparação sem mexer no dado. |
| Consultas que comparam e-mail | `findByCompetition_IdAndEmailAndStatusNot` sai. Entra `findByCompetition_IdAndEmailIgnoreCase(Long, String)` (qualquer status). Para o convite, `findByCompetition_IdAndEmailInIgnoreCase` não existe no Spring Data; usar uma `@Query` com `lower(p.email) in :emails` recebendo a lista já em minúsculas. | proposta | Uma busca por e-mail que ignore status e maiúsculas é a mesma regra do índice: o que o banco recusa, o código encontra antes. |
| Convite com e-mail já na competição (`invitePlayers`) | Antes de criar qualquer linha, buscar os e-mails da lista que já estão na competição. Se houver, `PlayerValidationException` com a mensagem `E-mail(s) already in this competition: a@x, b@y` (na grafia gravada) → `400`. Nada é gravado nem enviado. | proposta | Decidido na `spec.md` (2026-10-25): erro, pedido inteiro recusado, mesma exceção e status de `updateEmail`. Conferir tudo antes do laço evita mandar convite para o primeiro e-mail e só então falhar no segundo. |
| Lista com e-mail repetido | `invitePlayers`: `PlayerValidationException` (`Repeated e-mail(s) in the list: a@x`). `CompetitionService.validate`: `CompetitionValidationException` com a mesma mensagem. Os dois comparam em minúsculas. | proposta | Cada fluxo usa a exceção de validação que já tem; as duas viram `400` com corpo `Error`. |
| Convite simultâneo do mesmo e-mail (dois administradores, duplo clique) | `invitePlayers` grava com `saveAndFlush`; um `DataIntegrityViolationException` do índice vira `PlayerValidationException` com a mesma mensagem de "já na competição". | proposta | O segundo pedido espera o primeiro terminar no índice e falha. `saveAndFlush` faz o erro aparecer dentro do método, onde dá para traduzir; com `save` ele só apareceria no commit, como `500`. A transação é desfeita de qualquer forma. |
| Pedido de entrada repetido (`requestEntry`) | Usa a participação existente em qualquer status. Se ela está `IN_COMPETITION`, manda o link de novo e não muda status nem grava `PARTICIPATION_STATUS_CHANGED`. Nos outros status, como hoje. | proposta | Corrige o caminho que hoje cria a segunda participação (Contexto técnico). O jogador já na competição continua recebendo o link e indo direto para a competição, como no cenário existente. |
| Pedidos de entrada simultâneos sem participação | Inserir com `INSERT ... ON CONFLICT (competition_id, lower(email)) DO NOTHING` (query nativa `insertIfAbsent` no `ParticipationRepository`) e ler de volta com `findByCompetition_IdAndEmailIgnoreCase`. | proposta | Os dois pedidos dão certo e ficam com a mesma linha: o segundo espera o primeiro no índice, não insere, e a leitura seguinte (nova instrução, `READ COMMITTED`) enxerga a linha já gravada. Traduzir a violação em erro faria um dos dois pedidos falhar com `400`, o que a `spec.md` não pede para o pedido de entrada. |
| `confirmEntry` repetido | Se a participação já está `IN_COMPETITION`, devolve como está, sem `save` nem auditoria. A criação da participação de competição pública usa o mesmo `insertIfAbsent`. | proposta | `spec.md`: `200` com a participação, `joinedAt` e auditoria intactos. |
| Teste de concorrência | `EntryRequestServiceIntegrationTest`: dois `requestEntry` para o mesmo e-mail (um com maiúsculas) em duas threads, liberadas juntas por um `CountDownLatch`, contra o PostgreSQL do Compose. Confere uma única participação e nenhuma exceção. Um segundo teste, em `PlayerManagementServiceIntegrationTest`, faz o mesmo com `invitePlayers` e espera um sucesso e um `PlayerValidationException`. | proposta | Banco real, sem mock (constitution, "Testes"). Os testes de serviço já existem nesses arquivos. O envio de e-mail nesses testes é o mesmo que eles já usam hoje. |
| Exceções novas | Nenhuma classe nova. | resolvida | `PlayerValidationException` e `CompetitionValidationException` já existem e já têm `@ExceptionHandler`. Os caminhos novos que as lançam ganham um teste cada (ver `tasks.md`, T008). |
| Contrato | `docs/openapi.yaml`: `invitePlayers` ganha a resposta `400` (`Error`) com a descrição das duas regras; `createCompetition` cita o e-mail repetido na descrição do `400` que já existe. | proposta | Contract-first: o contrato muda antes do código. |

Decisões marcadas "em aberto" viram commit `decision:` quando resolvidas (ver
`memory/constitution.md`), atualizando esta tabela no mesmo commit.

## Estrutura de módulos/pacotes

- `app/src/main/resources/db/migration-jogo-acoes/V10__unique_participation_email_per_competition.sql` (novo).
- `competition/ParticipationRepository.java`: `findByCompetition_IdAndEmailIgnoreCase`,
  `findEmailsAlreadyIn(competitionId, lowerEmails)` (`@Query`) e `insertIfAbsent` (nativa,
  `@Modifying`); sai `findByCompetition_IdAndEmailAndStatusNot`.
- `competition/PlayerManagementService.java`: checagem da lista e dos e-mails já presentes antes
  do laço; `saveAndFlush` com tradução da violação.
- `competition/CompetitionService.java`: `validate` recusa e-mail repetido.
- `competition/EntryRequestService.java`: `requestEntry` e `confirmEntry` com as regras acima.
- `docs/openapi.yaml`: respostas de `invitePlayers` e `createCompetition`.
- Testes: cenários novos em `manage_competition_players.feature` e
  `request_competition_entry.feature` (texto na `spec.md`), um cenário para a lista repetida na
  criação em `create_competition.feature`, passos em `ManageCompetitionPlayersSteps`,
  `RequestCompetitionEntrySteps` e `CreateCompetitionSteps`, e os testes de concorrência.

Nenhum outro módulo muda. O `ArchitectureTest` não é afetado.

## Verificações (antes da correção, falhando)

1. Os cenários novos falham hoje: o segundo convite cria outra participação, a lista repetida
   cria duas, e a confirmação repetida muda `joinedAt`.
2. O teste de concorrência de `requestEntry` falha hoje (duas participações).
3. Depois da correção, os cenários existentes de entrada repetida continuam verdes,
   inclusive "Registered player requests entry again into a competition they already
   participate in", agora sem segunda participação.
4. Suíte do `app` verde (`mvn -pl app -am verify`) e suíte `blackbox-tests/` verde
   (`./scripts/cloud-blackbox-tests.sh` na nuvem, ou como no `README.md`).

## Riscos e trade-offs

- **Testes que reaproveitam e-mail.** Algum teste ou o gerador de dados (`blackbox-tests/seed`)
  pode convidar o mesmo e-mail duas vezes na mesma competição. Vai falhar com `400` depois da
  mudança; a correção é no teste, não na regra. Rodar as duas suítes cobre isso.
- **Query nativa.** `insertIfAbsent` amarra o repositório ao PostgreSQL. O projeto já usa
  PostgreSQL em todos os ambientes, e o índice de expressão também é específico dele.
- **Mensagem com e-mails.** A mensagem de erro repete e-mails que o próprio administrador
  mandou, só para ele. Não é log; a regra de não registrar e-mail completo em log (Issue #117,
  item 7) não se aplica.

## Decisões em aberto

Nenhuma.
