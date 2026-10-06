# Spec: Isolar repositórios por módulo — comunicação entre módulos só por serviço

**Status:** implementada (tabela conferida contra o `master` em 2026-10-06; ver `tasks.md`)
**Issue:** [#95](https://github.com/lalgarve/jogo-acoes/issues/95) (auditoria que originou esta
spec — "Scope" daquela Issue é exatamente o que esta spec implementa)
**Iteração:** iteration-5
**Depende de:** [`05-027-dividir-pacote-login`](../05-027-dividir-pacote-login/spec.md)
([PR #106](https://github.com/lalgarve/jogo-acoes/pull/106), ainda não mesclada) — esta spec parte
da estrutura de pacotes daquela PR (`login/` deixa de existir e vira `user`, `loginsession` e
`loginsecurity`; `LoginSession`/`LoginSessionRepository` saem de `link` para `loginsession`). A
implementação só começa depois da PR #106 mesclada.

## Resumo

Nenhuma classe de um módulo acessa o `*Repository` de outro módulo. Quando um módulo precisa de
dado ou de uma operação de outro, chama um **serviço público** desse outro módulo. Um
`*Repository` passa a ser detalhe interno do módulo dono dele, e uma regra de `ArchitectureTest`
garante isso no build.

"Módulo" aqui tem o mesmo sentido já usado em `docs/diagrams/modulos.md`: o primeiro segmento de
pacote abaixo de `dev.leilaalgarve.jogoacoes` (`competition`, `email`, `link`, `log`, ...).
Subpacotes (`competition/exception`, `link/dto`) pertencem ao mesmo módulo.

## Motivação

A Issue #95 mapeou módulos lendo o repositório de outro módulo diretamente. Isso tem dois custos
concretos:

- **Lógica duplicada.** "Quem é o usuário logado" está copiado cinco vezes (quatro em
  `competition/`, uma em `SessionsController`), cada cópia lendo `SecurityContextHolder` e
  chamando `UserRepository.findByEmail` por conta própria. "Criar usuário com papel" está
  duplicado entre `CompetitionLinkHandler.complete()` e `UserProvisioningService` (spec 05-026).
- **Acoplamento ao armazenamento.** Mudar uma query, um nome de método derivado do Spring Data
  ou o modelo de papéis de `user` hoje pode quebrar `competition`/`email` sem aviso, porque eles
  dependem do repositório, não de um contrato.

A Issue #95 considerou aceitável `login` acessar repositórios de `link` (direção permitida pela
spec 05-003). **Esta spec é mais estrita**: a regra vale para todo par de módulos, inclusive os
que a spec 05-003 permitia depender um do outro. A direção da dependência continua permitida; o
que muda é que ela passa por serviço, não por repositório.

## Inventário — chamadas a repositório de outro módulo (código de produção)

Levantado em 2026-10-03 contra o branch da [PR #106](https://github.com/lalgarve/jogo-acoes/pull/106)
(spec 05-027, `claude/spec-027-k9u43c` em `2f0a274`, ainda não mesclada), com `grep` de cada
`*Repository` fora do próprio pacote e leitura de cada chamada. Caminhos e linhas são os desse
branch.

| # | Arquivo:linha | Origem → dono | Chamada | Para quê |
|---|---|---|---|---|
| 1 | `competition/CompetitionsController.java:75` | `competition` → `user` | `userRepository.findByEmail(email)` | `currentUser()` — usuário logado |
| 2 | `competition/CompetitionService.java:156` | `competition` → `user` | `userRepository.findByEmail(email)` | `currentUser()` — usuário logado |
| 3 | `competition/CompetitionService.java:82` | `competition` → `user` | `userRepository.findByEmail(email).filter(User::isRegistered)` | vincular convidado já cadastrado à participação (criação de competição privada) |
| 4 | `competition/EntryRequestService.java:146` | `competition` → `user` | `userRepository.findByEmail(email)` | `currentUser()` — usuário logado |
| 5 | `competition/EntryRequestService.java:102` | `competition` → `user` | `userRepository.findByEmail(email)` | descobrir se quem pede entrada já existe/está cadastrado (escolhe template de e-mail, preenche `userId` do link) |
| 6 | `competition/PlayerManagementService.java:154` | `competition` → `user` | `userRepository.findByEmail(email)` | `currentUser()` — usuário logado |
| 7 | `competition/PlayerManagementService.java:70` | `competition` → `user` | `userRepository.findByEmail(email).filter(User::isRegistered)` | vincular convidado já cadastrado à participação (`invitePlayers`) |
| 8 | `competition/CompetitionLinkHandler.java:83` | `competition` → `user` | `userRepository.save(user)` | criar usuário ao concluir cadastro |
| 9 | `competition/CompetitionLinkHandler.java:130` | `competition` → `user` | `roleRepository.findByName(roleName)` | atribuir papel `PLAYER` ao usuário novo |
| 10 | `competition/CompetitionLinkHandler.java:137` | `competition` → `user` | `userRoleRepository.save(userRole)` | idem |
| 11 | `email/SentEmailRecorder.java:29` | `email` → `user` | `userRepository.findById(userId)` | anexar o `User` destinatário ao `SentEmail` |
| 12 | `loginsession/LoginController.java:71` | `loginsession` → `user` | `userRepository.findByEmail(email)` | pedido de link de login avulso — achar o usuário pelo e-mail |
| 13 | `loginsession/SessionsController.java:41` | `loginsession` → `user` | `userRepository.findByEmail(email)` | `currentUser()` — usuário logado (quinta cópia) |
| 14 | `loginsession/LoginLinkSessionService.java:75` | `loginsession` → `user` | `userRepository.findByEmail(auth.getName())` | `currentAuthenticatedUserId()` — id do usuário logado |
| 15 | `loginsession/LoginLinkSessionService.java:80` | `loginsession` → `user` | `userRepository.findById(userId)` | carregar usuário para montar a `Authentication` |
| 16 | `loginsession/LoginLinkSessionService.java:87` | `loginsession` → `user` | `userRoleRepository.findByUser_Id(userId)` | papéis do usuário → `GrantedAuthority` |
| 17 | `loginsession/LoginLinkHandler.java:61` | `loginsession` → `user` | `userRoleRepository.findByUser_Id(userId)` | `hasRole(userId, ADMINISTRATOR)` — destino padrão pós-login |
| 18 | `loginsession/LoginLinkSessionService.java:82` | `loginsession` → `link` | `linkRecordRepository.findByToken(token)` | achar o `LinkRecord` para gravar na `LoginSession` |

Em `master` (antes da 05-027) os itens 12–17 ainda não são chamadas entre módulos, porque
`User*Repository` e quem os usa estão todos em `login/`; em compensação `login` → `link` via
`LoginSessionRepository` é, e a 05-027 resolve essa movendo `LoginSession` para `loginsession`.
Mesma contagem de violações nas duas pontas, só redistribuída — o que confirma que esta spec deve
partir da estrutura da PR #106.

**Conferido e fora da lista** (acesso dentro do próprio módulo): `AuditLogService` →
`LogRepository`; `CompetitionService`/`CompetitionViewService`/`EntryRequestService`/
`PlayerManagementService`/`CompetitionLinkHandler` → `CompetitionRepository`/
`ParticipationRepository`; `LinkService` → `LinkRecordRepository`; `SentEmailRecorder` →
`SentEmailRepository`; `UserProvisioningService` → `User*`/`RoleRepository` (vai junto para
`user` na 05-027). `common/logging/RepositoryLoggingAspect.java` só cita `CompetitionRepository`
num comentário.

## Operações sugeridas nos serviços

Detalhe de assinatura, pacote e motivo de cada uma em `plan.md`. Resumo:

**`user` — `UserService` (novo, só leitura) + `UserProvisioningService` (já existe):**

| Operação | Substitui os itens |
|---|---|
| `Optional<User> findByEmail(String email)` | 5, 12 |
| `Optional<User> findRegisteredByEmail(String email)` | 3, 7 |
| `User getById(Long id)` | 11, 15 |
| `List<String> roleNamesOf(Long userId)` | 16 |
| `boolean hasRole(Long userId, String roleName)` | 17 |
| `UserProvisioningService.createUser(email, name, List.of(RoleName.PLAYER))` (existente) | 8, 9, 10 |

**`loginsession` — `CurrentUserService` (novo):**

| Operação | Substitui os itens |
|---|---|
| `User currentUser()` | 1, 2, 4, 6, 13 |
| `Optional<Long> currentUserId()` | 14 |
| `boolean currentUserIsAdministrator()` | `CompetitionsController.isAdministrator()` (não é repositório, mas é a mesma duplicação de "ler o `SecurityContextHolder` fora do módulo de sessão") |

**`link` — mudança de contrato em vez de operação nova:**

| Mudança | Substitui o item |
|---|---|
| `LinkSessionService.establish(Long userId, LinkRecord linkRecord)` no lugar de `establish(Long userId, String token)` — `LinkService` já tem o `LinkRecord` em mãos quando chama `establish` | 18 |

## Cenários (comportamento esperado)

Refatoração sem mudança de comportamento observável — nenhum `.feature` novo. Os cenários
existentes em `app/src/test/resources/features` (login, sessões ativas, convite, pedido de
entrada, gerência de jogadores, conclusão de cadastro) são o critério de aceite e devem continuar
passando sem alteração.

## Requisitos funcionais

- Nenhuma classe de produção (`app/src/main/java`) referencia um `*Repository` de outro módulo.
- Cada substituição do inventário usa a operação de serviço correspondente listada acima.
- As cinco cópias de `currentUser()` (e `CompetitionsController.isAdministrator()`) somem; quem
  precisa do usuário logado injeta `CurrentUserService`.
- `CompetitionLinkHandler.complete()` cria o usuário via `UserProvisioningService` e perde o
  `assignRole` próprio.
- `ArchitectureTest` ganha uma regra que falha o build se um `*Repository` for acessado por classe
  de outro módulo.

## Requisitos não-funcionais

- Mesma API HTTP, mesmas rotas, mesmo schema, mesmos testes passando.
- Nenhum ciclo novo de dependência entre módulos introduzido pelos serviços novos (`user` continua
  sem depender de ninguém; `CurrentUserService` em `loginsession` depende de `user`, nunca o
  contrário).

## Fora de escopo

- **Referências entre entidades JPA de módulos diferentes** (`Participation.user`,
  `SentEmail.user`, `Log.actor`, `LoginSession.linkRecord`). Elas continuam como estão: um módulo
  pode receber e repassar a entidade de outro (obtida por serviço), só não pode buscá-la pelo
  repositório. Trocar essas relações por id é outra discussão.
- **Código de teste** (`app/src/test/java`): fixtures e steps continuam podendo usar repositórios
  de qualquer módulo para montar e conferir dados (ver decisão em `plan.md`).
- A divisão de `login` em três módulos — é a spec 05-027.
- Dependências entre módulos que não passam por repositório (`EmailSender`, `AuditLogService`,
  `LinkService`, enums) — já são serviço/tipo, estão de acordo com a regra.

## Decisões em aberto

Nenhuma de requisito. As decisões técnicas (onde mora `CurrentUserService`, se a regra vale para
testes, nome e granularidade dos serviços) estão em `plan.md`, com o default escolhido marcado.
