# Spec: Busca de jogadores com filtros e paginação

**Status:** rascunho
**Issue:** —
**Iteração:** iteration-5
**Depende de:** [`05-038-consulta-de-jogador`](../05-038-consulta-de-jogador/spec.md) — reaproveita
o schema `PlayerSummary` e a definição das contagens.

## Resumo

O administrador lista os usuários do sistema filtrando por tipo de competição de que participam,
domínio do e-mail e se criaram alguma competição. O resultado vem paginado, com o total de
usuários que atendem ao filtro.

## Motivação

A spec 05-038 responde "quem é este usuário?" quando já se conhece o id ou o e-mail. Falta a
pergunta inversa: "quais usuários são de tal domínio?", "quem participa de competições
privadas?", "quem já criou competição?". Hoje a única listagem de jogadores é por competição
(`GET /competitions/{competitionId}/players`).

## Cenários (comportamento esperado)

- `app/src/test/resources/features/search_players.feature` (novo) — texto exato em `plan.md`, a
  escrever antes do código.

## Requisitos funcionais

- Só administrador. Jogador → `403`; sem sessão → `401`.
- Filtros, todos opcionais, combinados com E (um usuário precisa atender a todos os informados):
  - **tipo de competição** (`PUBLIC` ou `PRIVATE`): o usuário tem pelo menos uma participação
    contada em `publicCount`/`privateCount` (mesma definição da spec 05-038) em competição
    daquele tipo;
  - **domínio do e-mail**: parte depois do `@`, comparação exata sem diferenciar maiúsculas
    (`acme.com` não casa `sub.acme.com`);
  - **dono de competição** (`true`/`false`): criou pelo menos uma competição / não criou nenhuma.
- Sem filtro nenhum: todos os usuários.
- Resposta: `total` (quantos usuários atendem ao filtro, ignorando a paginação) e `items` (os
  `PlayerSummary` da página pedida).
- Paginação por número de página e tamanho: página começa em 0, tamanho padrão 20, máximo 100.
  Página além do fim → `items` vazio com o `total` correto (não é erro).
- Ordem estável e definida: por nome, desempate por `userId` — a mesma consulta repetida devolve
  as mesmas páginas, sem repetir nem pular usuário.
- Entrada inválida → `400`: tipo de competição fora do enum, domínio vazio ou contendo `@`,
  página negativa, tamanho menor que 1 ou maior que 100.

## Requisitos não-funcionais

- Custo de uma busca não cresce com o tamanho da página em número de consultas (sem N+1 para
  calcular as contagens de cada item).

## Fora de escopo

- Ordenação escolhida pelo chamador.
- Busca textual por nome ou parte do e-mail.
- Exportar a lista (CSV etc.).
- Filtrar por papel (`PLAYER`/`ADMINISTRATOR`) ou por status de participação.
- Qualquer UI/frontend.

## Decisões em aberto

Nenhuma. Resolvidas na sessão de 2026-10-10 (propostas aceitas):

- **Filtro de e-mail exato:** não entra nesta spec. A busca por e-mail continua sendo
  `GET /players?email=` (spec 05-038).
- **Paginação:** por página e tamanho (offset), porque o pedido inclui o total e o volume
  esperado é pequeno. Ver `plan.md`.
