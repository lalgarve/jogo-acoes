# Plan: Busca de jogadores com filtros e paginação

Traduz `spec.md` em decisões técnicas. Valida contra `memory/constitution.md`.

## Contexto técnico

Mesmo contexto da spec 05-038: `PlayerSummary`, `PlayerDirectoryService` e
`PlayerDirectoryController` no módulo `competition`, que já mapeia `User` por `@ManyToOne` em
`Competition.creator` e `Participation.user`. O contrato ainda não tem nenhuma operação
paginada — esta é a primeira, então o formato escolhido aqui vira o padrão das próximas.

## Decisões de arquitetura

| Pergunta | Decisão | Status | Raciocínio |
|---|---|---|---|
| Método e caminho | `POST /players/search` (`searchPlayers`), tag `player-directory`, `x-roles: [ADMINISTRATOR]`, resposta `200`. | em aberto (proposta) | O pedido original era `POST /competitions/players/list` com filtro em JSON. `POST` porque o filtro vem no corpo (e assim o domínio do e-mail não vai para o access log); `/players/...` pelos motivos do `plan.md` da 05-038. `search` diz o que a operação faz; `list` sugere uma listagem sem filtro. |
| Formato do pedido | `PlayerSearchRequest { competitionType?: CompetitionType, emailDomain?: string, owner?: boolean, page?: integer = 0, size?: integer = 20 }`. Corpo opcional: ausente equivale a `{}`. | resolvida | Reaproveita o enum `CompetitionType` já existente. Paginação no mesmo corpo do filtro, para o cliente reenviar um objeto só ao trocar de página. |
| Formato da resposta | `PlayerPage { total: int64, page: int32, size: int32, items: PlayerSummary[] }`. | resolvida | `total` foi pedido explicitamente; `page`/`size` ecoam o que foi aplicado (útil quando o cliente omitiu e valeu o padrão). |
| Paginação | Página/tamanho (offset), tamanho máximo 100. | em aberto (proposta) | Volume esperado pequeno (usuários de um jogo de turma), e offset dá o `total` sem custo extra de modelagem. Cursor só valeria a pena com volume grande ou com dados mudando muito entre páginas — nenhum dos dois se aplica. |
| Consulta | Dois SELECTs JPQL a partir de `User` por busca: (1) a página — o mesmo SELECT do resumo da spec 05-038 (`u.id, u.name, u.email` e as três contagens como subconsultas correlacionadas), agora com os filtros abaixo, `order by u.name, u.id` e `offset`/`limit`; (2) `count(u)` com os mesmos filtros, para `total`. | resolvida | Contagens no mesmo SELECT da página evitam N+1 (requisito não-funcional), e reaproveitar o SELECT da 05-038 garante que as contagens da busca e da consulta de um jogador não divergem. Considerado e descartado: juntar página e total num SELECT só com `count(*) over()` — é HQL específico do Hibernate (não JPQL padrão), e com a página vazia não volta linha nenhuma, então o caso "página além do fim" (que precisa do `total`) exigiria um `count` à parte de qualquer jeito. Filtros viram `exists (...)` correlacionados: tipo de competição → `exists` em `Participation` com `status = IN_COMPETITION` e `competition.type = :type`; dono → `exists`/`not exists` em `Competition` com `creator = u`; domínio → `lower(u.email) like concat('%@', lower(:domain))`. Filtro ausente vira `(:param is null or ...)`, ou a consulta é montada com `Specification`/Criteria se o JPQL com parâmetros nulos ficar ilegível — decidir na implementação. |
| Onde mora a consulta | `competition/ParticipationRepository` — o mesmo método criado pela 05-038, estendido com filtros e paginação. | resolvida | Decidido e justificado no `plan.md` da 05-038 (linhas "Onde mora a consulta", "Subconsultas correlacionadas ou `LEFT JOIN` + `GROUP BY`", "View materializada com as contagens" e "Índices"), que valem para as duas specs. |
| Índice para domínio de e-mail | Nenhum agora. | resolvida | `like '%@dominio'` não usa índice B-tree comum, mas o volume não justifica índice funcional. Reavaliar se a tabela crescer. |
| Validação | Bean Validation gerada a partir do contrato (`minimum`/`maximum` em `page`/`size`, `pattern: '^[^@\s]+$'` e `minLength: 1` em `emailDomain`); `400` pelo tratamento já existente em `common/ApiExceptionHandler`. | resolvida | Nenhuma classe de exceção nova. Cada regra de `400` do `spec.md` tem um exemplo no cenário de entrada inválida (`Scenario Outline`). |
| Autorização | `hasRole("ADMINISTRATOR")` para `POST /players/search` em `CompetitionSecurityConfigContributor`. | resolvida | Regra só por papel. |

## Cenários Gherkin (`app/src/test/resources/features/search_players.feature`, novo)

```gherkin
Feature: Search players
  As the administrator
  I want to filter the list of players
  So that I can find players by competition type, e-mail domain or ownership

  Background:
    Given the user is the administrator
    And the following players exist:
      | name  | email             | public | private |
      | Ana   | ana@acme.com      | 1      | 0       |
      | Bruno | bruno@acme.com    | 0      | 2       |
      | Carla | carla@example.org | 1      | 1       |
      | Davi  | davi@ACME.com     | 0      | 0       |
    And the user is logged into the system

  Scenario: Search without filters returns every player
    When they search players with no filter
    Then the result contains "Ana", "Bruno", "Carla", "Davi" and the administrator
    And the result total matches the number of players returned

  Scenario Outline: Search by a single filter
    When they search players filtering by <filter>
    Then the result contains exactly <names>

    Examples:
      | filter                   | names                    |
      | competition type PUBLIC  | "Ana", "Carla"           |
      | competition type PRIVATE | "Bruno", "Carla"         |
      | e-mail domain "acme.com" | "Ana", "Bruno", "Davi"   |

  Scenario: Filters are combined
    When they search players filtering by competition type PUBLIC and e-mail domain "acme.com"
    Then the result contains exactly "Ana"

  Scenario: Search by competition owners
    When they search players filtering by owners only
    Then the result contains exactly the administrator

  Scenario: Search by players who own no competition
    When they search players filtering by non-owners only
    Then the result contains exactly "Ana", "Bruno", "Carla", "Davi"

  Scenario: Long result is split into pages
    Given 25 additional players with e-mail domain "bulk.test" exist
    When they search players filtering by e-mail domain "bulk.test" with page size 10
    Then the result total is 25
    And the result has 10 players
    And requesting pages 0, 1 and 2 returns every player exactly once, ordered by name

  Scenario: Page beyond the end
    When they search players filtering by e-mail domain "acme.com" on page 5
    Then the result has no players
    And the result total is 3

  Scenario Outline: Invalid search
    When they search players with <invalid input>
    Then the system rejects the request as invalid

    Examples:
      | invalid input              |
      | page size 0                |
      | page size 500              |
      | page -1                    |
      | e-mail domain ""           |
      | e-mail domain "a@acme.com" |
      | competition type "HYBRID"  |

  Scenario: Player tries to search players
    Given the user is a registered player
    And the user is logged into the system
    When they try to search players
    Then the system denies access
```

Os cenários de domínio dependem de "Davi" (`davi@ACME.com`) para fixar a comparação sem
diferenciar maiúsculas. O último cenário não usa o `Background` como está (o usuário ali é o
administrador) — separar com `Rule` ou arquivo próprio ao escrever o `.feature`. "Participação"
nas colunas `public`/`private` é sempre `IN_COMPETITION`, conforme a definição da spec 05-038.

## Estrutura de módulos/pacotes

- `docs/openapi.yaml` (modificado) — `POST /players/search`, schemas `PlayerSearchRequest` e
  `PlayerPage`.
- `competition/PlayerDirectoryService.java` (modificado, criado pela 05-038) — `search(...)`.
- `competition/PlayerDirectoryController.java` (modificado) — `searchPlayers`.
- `competition/ParticipationRepository.java` (modificado) — filtros e paginação no SELECT
  criado pela 05-038, e o `count`.
- `competition/CompetitionSecurityConfigContributor.java` (modificado).
- `app/src/test/resources/features/search_players.feature` (novo) e os steps.

## Riscos e trade-offs

- **Subconsultas correlacionadas por linha.** Três por usuário da página, executadas pelo banco
  numa única ida e, com os índices da 05-038, por busca em índice. Aceitável até a casa dos
  milhares de usuários; acima disso, reavaliar as alternativas descartadas no `plan.md` da
  05-038 (`JOIN` com contagens pré-agregadas, view materializada).
- **Offset com dados mudando entre páginas.** Um usuário criado entre a página 0 e a 1 pode
  deslocar o resultado. Aceito: o caso de uso é consulta administrativa, não sincronização.
- **Primeira operação paginada do contrato.** Se o formato `PlayerPage` for virar padrão, vale
  extrair um `Page<T>` genérico depois — o gerador OpenAPI usado hoje não gera genéricos, então
  cada página continuaria com um schema próprio.
