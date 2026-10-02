# Plan: Dividir `login`/`link` em `user`, `loginSession` e `loginSecurity`

Traduz `spec.md` em mudanças arquivo por arquivo. Valida contra `memory/constitution.md`.

## Decisões de arquitetura (resolve as duas decisões em aberto de `spec.md`)

| Pergunta | Decisão | Raciocínio |
|---|---|---|
| Nome dos pacotes: `loginSession`/`loginSecurity` (camelCase) ou minúsculo? | **Minúsculo**: `user`, `loginsession`, `loginsecurity` | Todo pacote existente no projeto é uma palavra minúscula sem camelCase (`login`, `link`, `competition`, `captcha`, `email`, `log`) — camelCase quebraria a única convenção de nomenclatura de pacote já estabelecida, sem ganho nenhum. |
| Atualizar `docs/diagrams/*.md` nesta spec ou depois? | **Nesta spec**, último passo, depois do código compilar/testes passarem | Documentar uma estrutura que ainda não existe no código (se adiado) ou deixar a documentação desatualizada por uma spec inteira é pior que incluir a atualização aqui — a mudança é mecânica (mover seções entre arquivos), não motivo pra outra spec. |

## Inventário completo (achado nesta sessão — `login/`/`link/` têm mais arquivos que os listados em `spec.md`)

`spec.md` listou as classes principais; a varredura completa de `app/src/main/java/.../login/`
e `.../link/` encontrou três a mais que também precisam de um pacote novo:

- `AcceptChFilter` (filtro global que pede Client Hints em toda resposta, spec 05-009) — serve
  só o propósito de dar ao link de login a melhor chance de já ter Client Hints; vai pra
  `loginsession`.
- `ReturnToValidator` (valida o parâmetro `returnTo` de `requestLoginLink`, spec 05-005) — vai
  pra `loginsession`, usado só por `LoginController`/`LoginLinkHandler`.
- `DeviceLabelResolver` (resolve o rótulo de dispositivo a partir de Client Hints, usado por
  `LoginLinkSessionService`) — vai pra `loginsession`.

## Mapeamento arquivo por arquivo

### `app/src/main/java/dev/leilaalgarve/jogoacoes/user/` (novo)

De `login/`, sem mudança de conteúdo (só `package` e imports de quem os usa):

- `User.java`
- `UserRepository.java`
- `Role.java`
- `RoleName.java`
- `RoleRepository.java`
- `UserRole.java`
- `UserRoleId.java`
- `UserRoleRepository.java`
- `UserProvisioningService.java` (achado pós-escrita deste `plan.md`: criado pela spec 05-026,
  implementada depois — não expõe endpoint, só cria usuário/papel, mesmo destino do resto deste
  grupo)

### `app/src/main/java/dev/leilaalgarve/jogoacoes/loginsession/` (novo)

De `login/`:

- `LoginController.java`
- `LoginLinkHandler.java`
- `LoginLinkSessionService.java`
- `SessionsController.java`
- `SessionsService.java`
- `LoginSecurityConfigContributor.java`
- `AcceptChFilter.java`
- `ReturnToValidator.java`
- `DeviceLabelResolver.java`

De `link/` (saem de lá — ver "Impacto em `link/`" abaixo):

- `LoginSession.java`
- `LoginSessionRepository.java`

### `app/src/main/java/dev/leilaalgarve/jogoacoes/loginsecurity/` (novo)

De `login/`:

- `SecurityConfig.java`
- `SecurityConfigContributor.java`

### Resultado: `login/` deixa de existir

As 20 classes acima são exatamente o conteúdo de `login/` hoje — depois do mapeamento, o
diretório fica vazio e é apagado junto com o pacote.

### Impacto em `link/` (não listado em `spec.md`, mas afetado)

`link/` perde `LoginSession.java`/`LoginSessionRepository.java`, mantém o resto como está:
`LinkRecord`, `LinkRecordRepository`, `LinkService`, `LinkRouter`, `LinkHandler`,
`LinkSessionService`, `LinkOutcome`, `LinkCreationResult`, `dto/LinkPayload`,
`exception/LoginLinkInvalidException`, `exception/LoginLinkUsedOnAnotherDeviceException`.

### Testes (`app/src/test/java/.../`)

| Arquivo hoje | Pacote hoje | Pacote novo | Por quê |
|---|---|---|---|
| `LoginLinkHandlerTest.java` | `login` | `loginsession` | Testa `LoginLinkHandler`, que migra. |
| `DeviceLabelResolverTest.java` | `login` | `loginsession` | Testa `DeviceLabelResolver`, que migra. |
| `ReturnToValidatorTest.java` | `login` | `loginsession` | Testa `ReturnToValidator`, que migra. |
| `DeviceIdentificationSteps.java` | `login` | `loginsession` | Importa `LoginSession`/`LoginSessionRepository` (migram) e fixtures de usuário. |
| `ManageActiveSessionsSteps.java` | `login` | `loginsession` | Mesmo caso acima — testa `SessionsController`/`SessionsService`. |
| `LoginSteps.java` | `link` | `loginsession` | Importa `LoginLinkHandler`, `User`, `UserRepository`, `LoginSession` — é sobre o fluxo de login, não sobre `link` genérico. |
| `RedirectAfterLoginSteps.java` | `link` | `loginsession` | Testa redirecionamento pós-login (spec 05-005), não mecanismo genérico de link. |
| `SpringSessionSmokeTest.java` | `link` | `loginsession` | Prova a infraestrutura de sessão HTTP que `SessionsService.revoke()` depende — nenhuma dependência de domínio, mas conceitualmente é sobre sessão de login. |
| `LinkRouterKeyUniquenessTest.java` | `link` | `link` (sem mudança) | Testa `LinkRouter`, que não migra. |
| `LinkServiceTest.java` | `link` | `link` (sem mudança) | Testa `LinkService`, que não migra. |

Nenhum teste de `competition`/`email`/outros módulos muda de pacote — só os imports deles pra
`login.*`/`link.LoginSession`/`link.LoginSessionRepository` precisam apontar pro pacote novo
(ver "Imports a atualizar" abaixo).

## Imports a atualizar fora de `login/`/`link/`

Toda referência a `dev.leilaalgarve.jogoacoes.login.*` no resto do projeto muda pro pacote novo
correspondente (`user.*`, `loginsession.*` ou `loginsecurity.*`, conforme a tabela acima).
Achados nesta sessão que precisam de atenção (lista não exaustiva — confirmar com
`grep -rl "jogoacoes\.login\.\|jogoacoes\.link\.LoginSession"` na implementação antes de
considerar completo):

- `competition/CompetitionsController.java`, `CompetitionService.java`,
  `EntryRequestService.java`, `PlayerManagementService.java` — importam `login.User`/
  `login.UserRepository` (achado da Issue #95) → passam a importar `user.User`/
  `user.UserRepository`.
- `competition/CompetitionLinkHandler.java` — importa `login.Role`/`RoleName`/`RoleRepository`/
  `User`/`UserRepository`/`UserRole`/`UserRoleId`/`UserRoleRepository` → tudo migra pra `user.*`.
- `email/SentEmailRecorder.java` — importa `login.User`/`UserRepository` → `user.*`.
- `blackbox/BlackboxDataSeeder.java` — se a spec 05-026 (bootstrap do administrador) ainda não
  tiver sido implementada quando esta spec for, o import de `login.*` também precisa virar
  `user.*`; se 05-026 já estiver implementada, `BlackboxDataSeeder` já não existe mais e este
  item não se aplica (confirmado: já implementada, PR #101).
- `bootstrap/AdministratorBootstrap.java` — importa `login.UserProvisioningService` (spec 05-026)
  → passa a importar `user.UserProvisioningService`.
- Qualquer classe de `app/src/test/java/` fora de `login/`/`link/` que importe `User`/`Role`/
  `UserRole*`/`LoginSession`/`LoginSessionRepository` (ex. `common/testsupport/UserMother.java`,
  `common/testsupport/LoginLinkFixtures.java`) — confirmar e atualizar.

## Verificação estrutural (`ArchitectureTest`)

Candidato a regra nova, só depois que a divisão estiver completa e compilando (não bloqueia esta
spec, mas é o fechamento natural dela e da Issue #95):

```java
@Test
void userPackageHasNoOutgoingDependencyOnLoginsessionOrLoginsecurity() {
    ArchRuleDefinition.noClasses()
        .that().resideInAPackage("..user..")
        .should().dependOnClassesThat().resideInAnyPackage("..loginsession..", "..loginsecurity..")
        .check(importedClasses);
}
```

(Sintaxe exata do ArchUnit a confirmar contra a versão já usada pelo projeto — `ArchitectureTest`
atual usa `classes()`/`ArchRule`, não `noClasses()` ainda; conferir a API disponível antes de
escrever de verdade.) Registrar como item novo em `ArchitectureTest`, não uma classe separada
(mesma convenção já documentada lá: "one architecture test, not one class per rule").

## Abordagem de execução

Mudança mecânica (mover arquivo + trocar `package`/imports), mas com superfície grande (20
arquivos principais + ~10 arquivos de teste + N call sites externos). Ordem sugerida pra manter
o build compilável em cada passo, em vez de uma mudança gigante de uma vez:

1. Criar os três pacotes novos, mover `user/` primeiro (ninguém depende de `loginsession`/
   `loginsecurity` ainda, então só quem já importava `login.User`/`Role`/`UserRole*` precisa de
   ajuste de import nesse passo).
2. Mover `loginsecurity/` (igualmente sem dependência de `loginsession`).
3. Mover `loginsession/` por último (depende dos dois anteriores já existirem).
4. Atualizar os imports externos (`competition/`, `email/`, testes) na mesma leva do pacote que
   cada import referencia.
5. `mvn -pl app -am test` depois de cada passo — não só no final — pra isolar qual movimento
   quebrou algo, se quebrar.
6. Por último, atualizar `docs/diagrams/classes.md`/`modulos.md`/`sequencia.md`.

## Testes

Nenhum teste novo — esta spec não muda comportamento, só organização. Critério de sucesso:
`mvn -pl app -am test` com a mesma contagem de testes verdes de antes da mudança, e os diagramas
de `spec.md` batendo com a estrutura de pacotes real depois do passo 6.
