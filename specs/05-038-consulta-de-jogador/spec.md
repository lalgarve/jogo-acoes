# Spec: Consulta de jogador (resumo por id e por e-mail)

**Status:** rascunho
**Issue:** —
**Iteração:** iteration-5

## Resumo

Um usuário autenticado consulta o próprio resumo — nome, e-mail e quantas competições criou e
de quantas públicas e privadas participa. O administrador consulta o resumo de qualquer usuário,
por id ou por e-mail.

## Motivação

Hoje não há nenhuma operação que devolva dados de um usuário. O jogador não consegue ver o
próprio cadastro, e o administrador só enxerga jogadores dentro de uma competição específica
(`GET /competitions/{competitionId}/players`, que devolve `Participation`, não o usuário). Para
responder "quem é este e-mail?" ou "em quantas competições este jogador está?", o administrador
precisaria percorrer todas as competições.

## Cenários (comportamento esperado)

- `app/src/test/resources/features/view_player_profile.feature` (novo) — texto exato em
  `plan.md`, a escrever antes do código.

## Requisitos funcionais

- Resumo de jogador (`PlayerSummary`): `userId`, `name`, `email`, `ownedCount`, `publicCount`,
  `privateCount`.
  - `ownedCount`: número de competições cujo criador é o usuário.
  - `publicCount` / `privateCount`: número de competições públicas / privadas em que o usuário
    tem participação com status `IN_COMPETITION` (ver "Decisões em aberto").
- `GET /players/{userId}`:
  - jogador (`PLAYER`): só o próprio `userId`. Qualquer outro id → `404`, sem distinguir "não
    existe" de "existe mas não é seu" (mesmo padrão de `getCompetitionDetail` e
    `revokeSession`);
  - administrador (`ADMINISTRATOR`): qualquer `userId`; `404` se não existir;
  - sem sessão: `401`.
- `GET /players?email=<e-mail>` — só administrador:
  - devolve o resumo do usuário com aquele e-mail (comparação sem diferenciar maiúsculas);
  - `404` se não existir; `403` para jogador; `401` sem sessão;
  - parâmetro `email` ausente ou malformado → `400`.

## Requisitos não-funcionais

- O e-mail não aparece em caminho de URL (ver `plan.md`): caminho vai para access log e
  histórico do navegador, e e-mail é dado pessoal.
- Um jogador não descobre, por nenhuma resposta, se outro `userId` existe.

## Fora de escopo

- Editar nome ou e-mail do usuário.
- Listar/filtrar vários usuários — é a spec
  [`05-039-busca-de-jogadores`](../05-039-busca-de-jogadores/spec.md).
- Detalhar *quais* competições (só as contagens). A lista de competições do próprio usuário já
  existe em `GET /competitions/mine`.
- Qualquer UI/frontend.

## Decisões em aberto

- **O que `publicCount`/`privateCount` contam.** Proposta: só participações `IN_COMPETITION`
  (entrada confirmada), em qualquer status de competição (aberta ou encerrada). Alternativas:
  contar também convites/pedidos pendentes (`EMAIL_NOT_SENT`, `EMAIL_SENT`, `LINK_CLICKED`), ou
  contar só competições ainda abertas. A definição escolhida vale também para o filtro por tipo
  de competição da spec 05-039.
- **O administrador aparece como "jogador"?** Proposta: sim — `/players/{userId}` aceita
  qualquer usuário, e é o único jeito de o administrador ver o próprio `ownedCount`. O nome
  `players` fica por consistência com o resto do contrato, que já chama de "player" todo usuário
  que não está agindo como administrador.
