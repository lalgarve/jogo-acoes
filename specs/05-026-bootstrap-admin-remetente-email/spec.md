# Spec: Bootstrap do primeiro administrador e do remetente de e-mail via variável de ambiente

**Status:** implementada (tabela conferida contra o `master` em 2026-10-06; ver `tasks.md`)
**Issue:** parte da [Issue #84](https://github.com/lalgarve/jogo-acoes/issues/84) — substitui a
spec [05-024](../05-024-extrair-blackbox-data-seeder/spec.md) (descartada)
**Iteração:** iteration-5 (Etapa 2 antecipada)

## Resumo

Duas funcionalidades reais de produto, não mais scaffolding de teste, seguindo o mesmo padrão de
bootstrap que o próprio Postgres já usa (`POSTGRES_USER`/`POSTGRES_PASSWORD`/`POSTGRES_DB`,
lidos uma vez na primeira subida):

1. **Primeiro administrador**: `app/` ganha um pacote novo, `bootstrap/` — mesmo jar, mesmo
   processo, só que um pacote próprio em vez de dentro de `blackbox/` (convenção da disciplina:
   cada pacote de domínio/funcionalidade é um "módulo", mesmo sentido de `login`/`captcha`/
   `link`/`email` já existentes). Lê `ADMIN_EMAIL` do ambiente, em **qualquer profile**; se
   nenhum administrador existe ainda no sistema, cria um com esse e-mail. Substitui
   `BlackboxDataSeeder` (que só existia sob `@Profile("blackbox")`, e-mail fixo) — e resolve de
   quebra um buraco real que não era sobre teste nenhum: hoje **não existe nenhum jeito** de
   colocar um administrador num deploy de staging/production, porque não há API pra isso (de
   propósito) e o único seeder existente nunca roda fora do profile de teste. Pacote próprio (não
   dentro de `login/`) porque esse bootstrap deve ganhar pelo menos mais uma responsabilidade no
   futuro — definir a API-KEY do "Serviço de E-mail" (`deployo-api-key`,
   `docs/context/iteracao-5.md` seção 3.1) —, então o nome/local não deve soar exclusivo de
   "administrador".
2. **Remetente de e-mail**: o endereço remetente (`email.sender-address`), hoje hardcoded em
   `email-lambda/application.properties` e duplicado à mão em
   `docker/localstack/init/02-verify-ses-sender.sh`, passa a vir de uma única variável de
   ambiente, lida nos dois lugares — elimina a duplicação e torna o remetente configurável por
   ambiente, mesmo espírito do item 1.

## Motivação

**Por que isso não é mais um problema de teste:** investigação da Issue #84 (specs 05-023/
05-024) tratava as três classes do pacote `blackbox/` como scaffolding a extrair/apagar. Mas
`BlackboxDataSeeder` resolve um problema que todo sistema com conceito de administrador precisa
resolver pelo menos uma vez — "quem é o primeiro admin, antes de existir qualquer admin pra criar
outro" — e a resposta até agora ("só existe sob o profile `blackbox`") deixava staging/production
sem solução nenhuma, não porque fosse um não-problema lá, mas porque ninguém tinha olhado pra
isso fora do contexto de teste.

**Por que os dois itens andam juntos:** o administrador só consegue entrar no sistema por link
mágico (não há senha, de propósito) — e o link mágico só chega se o remetente de e-mail estiver
verificado junto ao provedor (SES, real ou emulado). Resolver o bootstrap do admin sem resolver
o do remetente deixaria o primeiro administrador de um ambiente novo trancado pra fora do
próprio sistema.

**Precedente já aceito no projeto:** `docker-compose.yml`'s `db` já faz exatamente esse tipo de
bootstrap via `POSTGRES_USER`/`POSTGRES_PASSWORD`/`POSTGRES_DB` — variável de ambiente lida uma
vez, efeito só na primeira vez que o estado inicial (banco vazio) é verdade. O mesmo padrão
resolve os dois problemas desta spec.

## Requisitos funcionais

### Bootstrap do administrador

- Pacote novo em `app/`, `dev.leilaalgarve.jogoacoes.bootstrap` — mesmo jar/processo de sempre,
  substituindo `BlackboxDataSeeder` (que saía junto com o resto do pacote `blackbox/`, spec
  05-023). Não entra dentro de `login/` porque esse pacote deve ganhar uma segunda
  responsabilidade no futuro (API-KEY do Serviço de E-mail) sem relação com `login`.
- `bootstrap/` **não manipula `UserRepository`/`RoleRepository`/`UserRoleRepository`
  diretamente** — chama um serviço novo dentro de `login/` que faz isso (ver "Serviço de criação
  de usuário com papéis" abaixo). `bootstrap/` só decide *quando* (lendo `ADMIN_EMAIL`,
  checando se já existe algum administrador), nunca *como* um usuário com papel é persistido —
  essa lógica já existe duplicada hoje (`BlackboxDataSeeder` e
  `CompetitionLinkHandler.complete()`, achado nesta sessão) e não deveria crescer uma terceira
  cópia.
- Roda em **todo profile** — nada de `@Profile("blackbox")` nem qualquer outro profile.
- Lê `ADMIN_EMAIL` do ambiente. Em `staging`/`production`, sem valor padrão — se não estiver
  setada, não faz nada (mesmo comportamento de hoje: nenhum admin criado automaticamente; exigir
  configuração explícita evita criar, sem ninguém pedir, um administrador cujo e-mail nenhum
  operador controla — o que bloquearia pra sempre a criação do administrador de verdade depois,
  já que o bootstrap só cria o primeiro).
- **Decisão de sessão (2026-10-02)**: nos profiles `docker` e `sandbox` (ambientes de
  desenvolvimento/teste, nunca staging/production), `ADMIN_EMAIL` ganha um valor padrão —
  `success+admin@simulator.amazonses.com` — definido no arquivo de propriedades do próprio
  profile (`application-docker.yml`/`application-sandbox.yml`), não hardcoded no Java. O admin
  passa a ser criado automaticamente num `docker compose up` simples, sem precisar da
  sobreposição `blackbox` nem de configurar nada à mão — mesmo endereço que `blackbox` já usa,
  então nenhum comportamento muda pra quem já definia a variável. O risco de "admin fantasma"
  citado acima não se aplica aqui: `success+admin@simulator.amazonses.com` é o mailbox simulado
  do próprio SES (spec 05-016), sempre alcançável em dev/teste.
- Se já existe pelo menos um usuário com papel `ADMINISTRATOR`, não faz nada — idempotente,
  inclusive entre restarts (mesmo espírito do Postgres: o bootstrap só importa enquanto o estado
  inicial, "nenhum admin", ainda é verdade; diferente do `BlackboxDataSeeder` antigo, que
  checava por aquele e-mail específico).
- Se não existe nenhum administrador e `ADMIN_EMAIL` está setada (ou tem valor padrão no profile),
  pede ao serviço de `login/` pra criar um usuário com esse e-mail e o papel `ADMINISTRATOR`. Sem
  senha em lugar nenhum (login continua só por link mágico).
- `docker-compose.blackbox.yml` continua setando `ADMIN_EMAIL=success+admin@simulator.amazonses.com`
  explicitamente como variável de ambiente do serviço `app` — redundante com o valor padrão do
  profile `docker` agora, mas inofensivo (mesmo endereço) e mantém a configuração do overlay
  auto-contida. `docker-compose.yml` base não define `ADMIN_EMAIL` — o padrão vem do profile, não
  do compose.

### Serviço de criação de usuário com papéis (`login/`, por enquanto)

**Decisão de sequenciamento (sessão 2026-10-01): a 05-026 é implementada antes da
[05-027](../05-027-dividir-pacote-login/spec.md)** (divisão de `login/` em `user`/`loginsession`/
`loginsecurity`). Por isso este serviço nasce em `login/` — pacote onde `User`/`Role`/`UserRole*`
ainda moram neste momento — e migra pra `user/` como parte da implementação da 05-027 depois,
sem mudança de conteúdo, só de pacote.

- Serviço novo em `login/` (nome a decidir — ver "Decisões em aberto"), com um método que recebe
  e-mail, nome e **uma lista de papéis** (`List<RoleName>`, não um papel só) — o desenho do
  sistema já permite um usuário ter mais de um papel ao mesmo tempo (`UserRole` é uma tabela de
  associação própria, não uma coluna única em `User`), então a assinatura precisa refletir isso
  mesmo que o bootstrap do administrador só use um papel por enquanto.
- Consolida a lógica hoje duplicada entre `BlackboxDataSeeder` e
  `CompetitionLinkHandler.complete()` (criar `User`, e pra cada papel da lista, criar o
  `UserRole` correspondente, usando `RoleRepository.findByName`) — mesma duplicação já registrada
  na [Issue #95](https://github.com/lalgarve/jogo-acoes/issues/95). Se `CompetitionLinkHandler`
  passa a usar esse serviço também (consolidando a duplicação existente) é uma decisão de
  implementação, não bloqueia esta spec — mas vale avaliar no `plan.md`, já que o achado é
  direto desta investigação.
- Expõe também a checagem de idempotência que `bootstrap/` precisa ("existe algum usuário com
  este papel?") — mantém a lógica de "o que significa já existir um administrador" num lugar só,
  perto de `User`/`Role`/`UserRole`, não espalhada em quem consome.

### Bootstrap do remetente de e-mail

- `email-lambda/src/main/resources/application.properties`: `email.sender-address` passa a ler
  de uma variável de ambiente (`EMAIL_SENDER_ADDRESS`, mantendo o valor atual como padrão pra
  quem roda sem Docker: `${EMAIL_SENDER_ADDRESS:no-reply@jogo-acoes.example}`).
- `docker/localstack/init/02-verify-ses-sender.sh`: para de hardcodar o endereço, passa a ler a
  mesma variável de ambiente (repassada pelo `docker-compose.yml` pro serviço `localstack`),
  com o mesmo padrão como fallback — elimina a duplicação "mantida em sincronia à mão" que o
  próprio script já documenta como frágil.

## Requisitos não-funcionais

- Nunca cria um segundo administrador automaticamente — só o primeiro; nunca sobrescreve ou
  duplica.
- Nenhuma senha em lugar nenhum do sistema — login continua exclusivamente por link mágico,
  igual hoje.
- Em staging/production, sem as variáveis setadas, o comportamento é idêntico ao atual (nenhum
  admin criado automaticamente, remetente no valor padrão já em uso hoje).

## Fora de escopo

- **Endpoint/fluxo pra um administrador já logado criar outros usuários/admins** — fica pra uma
  spec futura. Esta spec resolve só o bootstrap do primeiro.
- **Verificação de remetente contra AWS real** — a decisão 7 (`docs/context/iteracao-4.md`)
  continua bloqueada por falta de acesso a uma conta AWS real. Esta spec só torna o endereço
  configurável de forma consistente; não desbloqueia a verificação em produção de verdade.
- **Implementar a bootstrap da API-KEY do `deployo-api-key`/"Serviço de E-mail"**
  (`docs/context/iteracao-5.md`, seção 3.1) — esse serviço ainda não existe no código (a spec
  05-025, de outra sessão em paralelo, só começou a desenhá-lo). Esta spec só garante que o
  pacote novo do administrador nasce com nome/escopo neutro o bastante pra essa segunda
  responsabilidade caber nele mais tarde — mas não implementa essa parte agora.
- `BlackboxController`/`BlackboxSecurityConfigContributor` — spec 05-023, não esta.

## Decisões resolvidas

As cinco perguntas que estavam aqui foram decididas em `plan.md` ("Decisões de arquitetura"):

1. **Nome do serviço novo:** `UserProvisioningService` (hoje no pacote `user`, depois da spec 05-027).
2. **`CompetitionLinkHandler.complete()`:** não migrou nesta spec (duplicação registrada na Issue
   #95). Migrou depois: hoje usa o `UserProvisioningService`, e a Issue #95 está fechada.
3. **Nome do administrador:** `ADMIN_NAME`, opcional, com `"Administrator"` quando ausente.
4. **`ADMIN_EMAIL` malformado:** loga `ERROR` e não cria ninguém, sem derrubar a subida.
5. **Nomes das variáveis:** `ADMIN_EMAIL`, `ADMIN_NAME` e `EMAIL_SENDER_ADDRESS`. A última saiu
   do `app` com a spec 05-031: o remetente passou a ser de cada cliente, no `email-service`.

## Decisões em aberto

Nenhuma.
