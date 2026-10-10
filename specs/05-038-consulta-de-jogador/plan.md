# Plan: Consulta de jogador (resumo por id e por e-mail)

Traduz `spec.md` em decisões técnicas. Valida contra `memory/constitution.md`.

## Contexto técnico

- `user/User` (`app_user`: `id`, `name`, `email` único, `registered`) e `user/UserService`
  (`getById`, `findByEmail`, `hasRole`).
- `competition/Competition.creator` e `competition/Participation.user` já são `@ManyToOne` para
  `User` — `competition` depende de `user`, nunca o contrário (spec 05-029).
- `loginsession/CurrentUserService` (`currentUser()`, `currentUserIsAdministrator()`) é o ponto
  único para "quem está logado".
- Spec 05-029: nenhum módulo acessa `*Repository` de outro; `ArchitectureTest` verifica.

## Decisões de arquitetura

| Pergunta | Decisão | Status | Raciocínio |
|---|---|---|---|
| Caminhos | `GET /players/{userId}` (`getPlayer`) e `GET /players?email=` (`findPlayerByEmail`), tag nova `player-directory` (ver linha abaixo). | em aberto (proposta) | O pedido original era `/competition/players/{userId}` e `/competition/players/email/{email}`. `/competitions/players/...` disputaria o template `/competitions/{competitionId}` e sugere que o recurso pertence a uma competição, o que não é o caso. E-mail no caminho exige escapar `+`/`@` e vaza dado pessoal em access log; como parâmetro de query também vai para o log, mas a alternativa sem log nenhum (corpo de `POST`) já é a busca da spec 05-039 com filtro de e-mail exato. Se a 05-039 ganhar esse filtro, `findPlayerByEmail` pode até ser dispensada. |
| Tag no contrato | Tag nova `player-directory` no fim da lista de tags. | resolvida | A tag `players` hoje agrupa a gestão de jogadores *dentro* de uma competição (`/competitions/{id}/players/...`). Com `useTags=true` no gerador (`app/pom.xml`), cada tag vira uma interface Java: reaproveitar `players` poria as operações novas em `PlayersApi`, junto das de competição; a tag nova gera `PlayerDirectoryApi`, implementada só pelo controller desta spec, e uma seção própria no Swagger UI. |
| Schema de resposta | `PlayerSummary { userId, name, email, ownedCount, publicCount, privateCount }`, contagens `integer` (`int64`). | resolvida | Reaproveitado sem mudança como item da lista na spec 05-039. |
| Onde fica o cálculo das contagens | Módulo `competition`, num serviço novo `PlayerDirectoryService`. | resolvida | As contagens são dados de `competition`, e `competition` já depende de `user` — a direção permitida. Colocar em `user` exigiria `user` → `competition`, um ciclo. Um módulo novo (`player/`) só para isso não teria repositório próprio e seria só um repasse. |
| Como o resumo é consultado | Um único SELECT JPQL a partir de `User`, filtrado por `u.id = :id` ou `lower(u.email) = lower(:email)`, trazendo `u.id, u.name, u.email` e as três contagens como subconsultas correlacionadas (`select count(c) from Competition c where c.creator = u`; `select count(p) from Participation p where p.user = u and p.status = IN_COMPETITION and p.competition.type = PUBLIC`, idem `PRIVATE`), projetado direto num record. É a mesma consulta que a busca da spec 05-039 estende com filtros e paginação; mora em `ParticipationRepository` (linha "Onde mora a consulta" abaixo). | resolvida | Substitui a ideia anterior de três consultas (usuário via `UserService`, `countByCreator_Id`, contagem agrupada de participações): uma ida ao banco em vez de três, e uma definição só das contagens para a consulta de um jogador e para a busca — as duas não podem divergir. |
| Onde mora a consulta | `competition/ParticipationRepository`, como método `@Query` com JPQL que parte da entidade `User`. | resolvida | Decisão da sessão de 2026-10-10. As contagens são sobre participações (e competições criadas), dados do módulo `competition`, que já depende de `user` — a direção permitida. A regra da spec 05-029 é sobre classes `*Repository` de outro módulo e não é violada: nenhum código chama `UserRepository`; a consulta só referencia a entidade `User` no JPQL, como `Participation.user` e `Competition.creator` já fazem. A alternativa sem referenciar `User` (pedir os ids a `UserService` e contar em `competition`) não pagina corretamente na busca da 05-039 quando há filtros nos dois lados. Acrescentar o caso à seção correspondente de `docs/diagrams/modulos.md` na implementação. |
| Subconsultas correlacionadas ou `LEFT JOIN` + `GROUP BY` | Subconsultas correlacionadas no SELECT, uma por contagem. | resolvida | (1) **Contagens corretas sem `distinct`:** um `LEFT JOIN` com `participation` e com `competition` (como dono) junta duas relações um-para-muitos e multiplica as linhas (3 participações × 2 competições criadas = 6 linhas), exigindo `count(distinct ...)` com `case` por tipo; cada subconsulta é independente e espelha a definição da spec. (2) **Calculadas só para a página:** o PostgreSQL (≥ 9.6) adia expressões do SELECT para depois de `ORDER BY ... LIMIT` quando a ordenação não depende delas (aqui, `name, id`), então só os ≤ 100 usuários da página têm contagens calculadas; com `GROUP BY`, o banco agrega todos os usuários filtrados antes de cortar a página. (3) **Mesmo estilo dos filtros**, que já são `exists (...)` correlacionados (05-039). (4) **JPQL padrão:** a versão correta com `JOIN` precisaria de subconsulta no `FROM` (contagens pré-agregadas por `user_id`), que é HQL do Hibernate, não JPQL. O `JOIN` só ganharia calculando contagens de muitos usuários sem `LIMIT` (ex.: exportar todos), fora do escopo. |
| View materializada com as contagens | Não usar. | resolvida | O PostgreSQL não atualiza view materializada sozinho nem incrementalmente — `REFRESH` (com ou sem `CONCURRENTLY`, que ainda exige índice único) recalcula tudo. Os cenários esperam a contagem atualizada na hora (o jogador entra numa competição e o resumo já mostra), o que exigiria refresh a cada escrita em `participation`/`competition` (custo maior que o problema) ou refresh agendado (dado atrasado, e os testes teriam que forçar o refresh). Com página ≤ 100 e os índices abaixo, não há problema de desempenho para resolver. Também descartada a view comum (não materializada): teria o mesmo custo das subconsultas, e a consulta no `ParticipationRepository` já é a definição única das contagens. Reavaliar só com volume grande e uma spec que aceite atraso. |
| Índices | Migration Flyway nova com `CREATE INDEX` em `participation (user_id)` e `competition (creator_id)`. | resolvida | O PostgreSQL não cria índice para chave estrangeira, e hoje nenhuma das duas colunas tem índice: sem eles, cada subconsulta varre a tabela inteira para cada usuário da página; com eles, vira busca por índice. Barato e independente do volume atual. |
| Onde fica o controller | `competition/PlayerDirectoryController`, implementando a interface gerada da tag escolhida. | resolvida | Mesmo módulo do serviço. |
| Autorização de `GET /players/{userId}` | `x-roles: [PLAYER, ADMINISTRATOR]` no contrato e só `authenticated()` no `SecurityConfigContributor`; a regra "jogador só vê o próprio" fica no serviço: se `!currentUserIsAdministrator() && userId != currentUser().getId()` → mesmo `404` de "não existe". | resolvida | A regra depende do valor do caminho, não só do papel — não cabe num `requestMatchers`. Responder `404` (e não `403`) não revela se o id existe. |
| Autorização de `GET /players?email=` | `hasRole("ADMINISTRATOR")` em `CompetitionSecurityConfigContributor` para `GET /players` (sem id). | resolvida | Regra só por papel; jogador recebe `403` do próprio Spring Security. |
| Normalização do e-mail | `findByEmail` hoje compara exatamente. Comparar com `lower(email)` nessa consulta. | em aberto | Depende de como o e-mail é gravado na criação (se já é normalizado, não há nada a fazer). Verificar na implementação e registrar aqui. |
| Exceções | Reaproveitar o tratamento de "não encontrado" já existente: o controller devolve `ResponseEntity.notFound()` a partir de um `Optional` vazio, como `SessionsController.revokeSession`. Nenhuma classe de exceção nova. | resolvida | Sem exceção nova, o critério de aceite da constitution fica restrito aos ramos `404`/`403`/`400`, todos cobertos por cenário. |

## Cenários Gherkin (`app/src/test/resources/features/view_player_profile.feature`, novo)

```gherkin
Feature: View player profile
  As a player or administrator
  I want to see a player's summary
  So that I know who they are and how many competitions they are involved in

  Scenario: Player views their own profile
    Given the user is a registered player
    And they joined 2 public competitions and 1 private competition
    And the user is logged into the system
    When they view their own profile
    Then the system shows their name and e-mail
    And shows 0 owned, 2 public and 1 private competitions

  Scenario: Pending invitations are not counted
    Given the user is a registered player
    And they joined 1 private competition
    And they were invited to another private competition and did not confirm entry
    And the user is logged into the system
    When they view their own profile
    Then the system shows 0 owned, 0 public and 1 private competitions

  Scenario: Player tries to view another player's profile
    Given the user is a registered player
    And another registered player exists
    And the user is logged into the system
    When they try to view that other player's profile
    Then the system shows an error message without revealing whether the player exists

  Scenario: Administrator views any player's profile
    Given the user is the administrator
    And a registered player joined 1 public competition
    And the user is logged into the system
    When they view that player's profile
    Then the system shows 0 owned, 1 public and 0 private competitions

  Scenario: Administrator's own profile counts the competitions they created
    Given the user is the administrator
    And they created 3 competitions
    And the user is logged into the system
    When they view their own profile
    Then the system shows 3 owned competitions

  Scenario: Administrator views a profile that does not exist
    Given the user is the administrator
    And the user is logged into the system
    When they try to view the profile of a player that does not exist
    Then the system reports that the player was not found

  Scenario: Administrator looks up a player by e-mail
    Given the user is the administrator
    And a registered player with e-mail "ana@example.com" exists
    And the user is logged into the system
    When they look up the player with e-mail "ana@example.com"
    Then the system shows that player's summary

  Scenario: Administrator looks up an unknown e-mail
    Given the user is the administrator
    And the user is logged into the system
    When they look up the player with e-mail "nobody@example.com"
    Then the system reports that the player was not found

  Scenario: Administrator looks up a malformed e-mail
    Given the user is the administrator
    And the user is logged into the system
    When they look up the player with e-mail "not-an-email"
    Then the system rejects the request as invalid

  Scenario: Player tries to look up a player by e-mail
    Given the user is a registered player
    And the user is logged into the system
    When they try to look up a player by e-mail
    Then the system denies access

  Scenario: Anonymous user tries to view a profile
    Given the user is not logged in
    When they try to view a player's profile
    Then the system rejects the request as not logged in
```

O cenário "Pending invitations are not counted" fixa a decisão em aberto do `spec.md`; muda junto
com ela se a escolha for outra.

## Estrutura de módulos/pacotes

- `docs/openapi.yaml` (modificado) — tag nova, `/players` e `/players/{userId}`, schema
  `PlayerSummary`.
- `competition/PlayerDirectoryService.java` (novo).
- `competition/PlayerDirectoryController.java` (novo).
- `competition/ParticipationRepository.java` (modificado) — o SELECT do resumo.
- Migration Flyway nova em `db/migration-jogo-acoes/` (próxima versão livre, hoje `V9`) —
  índices em `participation (user_id)` e `competition (creator_id)`.
- `competition/CompetitionSecurityConfigContributor.java` (modificado) — regras de `/players`.
- `app/src/test/resources/features/view_player_profile.feature` (novo) e os steps
  correspondentes.

## Riscos e trade-offs

- **Contagens calculadas, não armazenadas.** Três subconsultas correlacionadas num único
  SELECT por resumo; barato para um usuário só, e o mesmo SELECT serve à página inteira da
  busca (spec 05-039) sem N+1.
- **O caminho `/players` é genérico.** Se no futuro existir um recurso "jogador dentro de uma
  competição" no nível raiz, os nomes vão competir. Hoje não há nada assim no contrato.
