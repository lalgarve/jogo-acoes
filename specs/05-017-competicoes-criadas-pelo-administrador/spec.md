# Spec: Administrador lista as competições que criou

**Status:** rascunho
**Issue:** —
**Iteração:** iteration-5

## Resumo

`GET /competitions/mine` ganha um quarto grupo, `created` — as competições que o usuário
logado criou, independente de ter (ou poder ter) uma participação nelas. Hoje um
administrador que cria uma competição não tem nenhuma rota que devolva essa competição de
volta, a não ser que ele já saiba o id.

## Motivação

`Competition` já tem `creator` (`User`, obrigatório) desde a criação da entidade, mas nenhum
repositório/endpoint consulta por ele. `GET /competitions/mine`
(`CompetitionViewService.listMyCompetitions`) monta os três grupos hoje existentes
(`participating`, `pastParticipations`, `pendingConfirmation`) só a partir de
`participationRepository.findByUser_Id` — um administrador não é participante da própria
competição, então ela nunca aparece ali. `GET /competitions/public` só devolve públicas em
`OPEN`; uma privada criada pelo administrador nunca aparece em nenhuma listagem para ele.

`view_my_competitions.feature` (spec 05-004) já tem uma regra sobre o administrador acessar o
*detalhe* de uma competição sem ser participante dela ("Administrator accesses the details of
a competition they did not join") — mas isso é sobre abrir uma competição específica cujo id
já se conhece, nunca sobre *listar* quais o administrador criou; a listagem foi escrita
pensando só na perspectiva de jogador. Sem isso, a
única forma de um administrador (ou uma automação, como o gerador de dados de teste do
ambiente `blackbox`) saber quais competições já criou é guardar os ids no momento da criação
e nunca perdê-los.

## Cenários (comportamento esperado)

- `app/src/test/resources/features/view_my_competitions.feature` — nova regra, cenário do
  administrador vendo uma competição que criou sob o grupo `created` (a escrever antes do
  código, ver `plan.md` para o texto exato).

## Requisitos funcionais

- `GET /competitions/mine` devolve um quarto array, `created`, sempre presente mesmo vazio
  (mesma convenção dos outros três) — toda competição cujo `creator` é o usuário logado, em
  qualquer status (`AWAITING_INVITES`/`OPEN`/`CLOSED`).
- `created` é independente dos outros três grupos — uma competição pode aparecer em `created`
  e, se algum dia o criador também tiver uma participação nela (não acontece em nenhum fluxo
  hoje), em outro grupo ao mesmo tempo; nenhuma deduplicação entre grupos.
- Vale para qualquer usuário logado, não só administrador — hoje só administrador cria
  competição (`x-roles: [ADMINISTRATOR]` em `POST /competitions`), então `created` é sempre
  vazio para jogador; a consulta em si não distingue papel.

## Requisitos não-funcionais

- Mudança de contrato aditiva (`docs/openapi.yaml`) — campo novo em `MyCompetitions`, nenhum
  campo/rota existente muda de formato; clientes que ignoram campos desconhecidos continuam
  funcionando sem alteração.

## Fora de escopo

- Paginação de `created` — mesma decisão já implícita nos outros três grupos (sem paginação
  hoje); fica para quando o volume justificar.
- Qualquer mudança em `POST /competitions`, `GET /competitions/{id}` ou no cálculo de
  `AccessLevel` — esta spec só acrescenta uma forma de *listar*, não muda quem pode ver o quê.
- Resolver como o gerador de dados de teste do ambiente `blackbox` usa isso (spec separada,
  ainda sem número) — esta spec só entrega a capacidade na API.

## Decisões em aberto

Nenhuma — o formato do campo novo (`created`, array de `CompetitionSummary`, sempre presente)
foi decidido em conversa antes de escrever este documento; detalhamento técnico em `plan.md`.
