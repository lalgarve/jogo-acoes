# Spec: Extrair `BlackboxDataSeeder` de `app/` pra módulo próprio

**Status:** descartada — ver "Por que esta spec foi descartada" no final do arquivo
**Issue:** parte da [Issue #84](https://github.com/lalgarve/jogo-acoes/issues/84) (fecha de vez
a fatia `blackbox/` do escopo, junto com a spec
[05-023](../05-023-remover-leitor-email-blackbox/spec.md))
**Iteração:** iteration-5 (Etapa 2 antecipada)

## Resumo

`BlackboxDataSeeder` sai de `app/` pra um módulo próprio — última peça do pacote `blackbox/`
ainda dentro do artefato de produção depois que a spec 05-023 apagar `BlackboxController`/
`BlackboxSecurityConfigContributor`. Ao contrário daquelas duas classes (que simplesmente deixam
de existir), este seeder precisa continuar existindo em algum lugar: não há API pra criar um
administrador (de propósito — só um administrador pode criar competições), então algo precisa
inserir um no banco antes de qualquer fluxo admin ser exercitável num ambiente de teste.

## Motivação

Regra da `memory/constitution.md` ("código de teste/dev nunca dentro da aplicação, mesmo atrás
de profile/flag") não abre exceção pra esta classe. Hoje ela roda dentro do mesmo processo/JVM
de `app/` (um `ApplicationRunner` ativado só sob `@Profile("blackbox")`) — fisicamente dentro do
artefato de produção, mesmo que inerte fora desse profile.

**Diferença importante em relação a `blackbox-proxy/` (spec 05-020) e à spec 05-023:** aquelas
duas extrações não têm estado compartilhado com `app/` (o proxy só mexe em headers HTTP; a
leitura de e-mail passa a vir do LocalStack, não do Postgres). `BlackboxDataSeeder` lê e escreve
nas mesmas tabelas (`users`, `roles`, `user_roles`) que `app/` já usa pra tudo — usando o mesmo
Hibernate/JPA, as mesmas entidades, a mesma lógica de negócio (`UserRepository`,
`RoleRepository`, `UserRoleRepository`). Extrair isso de verdade é uma mudança de desenho mais
séria do que as duas anteriores, não só mover arquivo de lugar.

## Requisitos funcionais

- Novo módulo Maven, processo/aplicação separada de `app/` (nome a decidir — ver "Decisões em
  aberto").
- Na subida do ambiente de teste, garante idempotentemente que existe um usuário administrador
  com e-mail fixo `success+admin@simulator.amazonses.com`, papel `ADMINISTRATOR` atribuído —
  mesmo comportamento observável de hoje, só rodando em outro lugar.
- Nunca duplica o administrador se ele já existir (mesma checagem de hoje:
  `userRepository.findByEmail(ADMIN_EMAIL)` antes de inserir).
- Acessa o mesmo banco Postgres que `app/` usa (`jogo_acoes`) — não é um banco próprio, é uma
  segunda conexão à mesma base.

## Requisitos não-funcionais

- Nunca roda em `staging`/`production` — mesma confinação já aplicada a `blackbox-proxy/`/
  `blackbox-tests/`.
- Não introduz nenhuma migração de schema própria — o schema (tabelas `users`/`roles`/
  `user_roles`, incluindo os papéis já semeados por `V5__seed_roles.sql`) continua sendo
  propriedade exclusiva das migrations Flyway de `app/`.

## Fora de escopo

- `BlackboxController`/`BlackboxSecurityConfigContributor` — spec 05-023, não esta.
- Qualquer mudança no schema do banco ou nas migrations Flyway de `app/`.
- Criar uma API real de administração de usuários (seria uma mudança de produto, não uma
  refatoração estrutural) — continua não existindo, de propósito.

## Decisões em aberto

1. **Nome do módulo.**
2. **Mecanismo de acesso ao banco: entidades JPA próprias (mínimas, duplicando o shape de
   `User`/`Role`/`UserRole` só com os campos que o seeder usa) ou JDBC puro (SQL direto, sem
   Hibernate)?** Entidades duplicadas correm o risco de ficarem dessincronizadas se `app/` mudar
   o schema (sem o compilador acusando, já que são classes diferentes); JDBC puro evita trazer
   toda a dependência do Hibernate pra um módulo que só faz um `INSERT` condicional, mas é SQL
   escrito à mão (sem checagem de tipo). Nenhuma das duas é claramente melhor sem saber o
   apetite do projeto por esse tipo de duplicação — mesma pergunta já registrada (sem resposta)
   na investigação da spec 05-023.
3. **Processo "vivo" (aplicação Spring Boot completa, como hoje) ou job *one-shot* (roda, insere
   se precisar, sai — mesmo padrão do `email-lambda-builder` já adotado em `docker-compose.yml`
   pela Issue #84/#87)?** Um seeder não precisa ficar de pé depois de rodar uma vez — um job
   *one-shot* é mais simples (sem porta, sem health check próprio) e já tem precedente direto
   neste mesmo `docker-compose.yml`.
4. **Ordem de inicialização: como garantir que o schema (migrations Flyway de `app/`) já existe
   antes deste seeder tentar inserir?** Hoje isso nunca foi um problema porque o seeder roda
   *dentro* do mesmo processo de `app/`, depois que o próprio Spring Boot já rodou o Flyway e
   terminou de subir o contexto. Separado em outro processo, isso vira uma dependência de
   ordem entre contêineres que `docker-compose.yml` precisa expressar — mas `app/` hoje **não
   tem health check nenhum** declarado no `docker-compose.yml` (só `db`/`localstack` têm), então
   não existe ainda um jeito de um `depends_on: condition: service_healthy` saber que o Flyway
   já terminou. Isso precisa ser resolvido no `plan.md` — não é um detalhe menor, é o que decide
   se essa extração é tão simples quanto a da spec 05-023 ou não.

## Por que esta spec foi descartada

O usuário reformulou o problema (sessão 2026-10-01): seedar o primeiro administrador não é uma
necessidade só do ambiente de teste blackbox — é um problema de bootstrap que qualquer deploy
real (staging/production) também tem, e que hoje não tem solução nenhuma fora do profile
`blackbox`. A analogia trazida foi o próprio Postgres (`POSTGRES_USER`/`POSTGRES_PASSWORD`,
lidos uma vez na primeira subida) — o mesmo padrão resolve o admin do `jogo-acoes`.

Isso elimina a premissa inteira desta spec: `BlackboxDataSeeder` não precisa de um módulo de
teste separado porque deixa de ser código de teste — vira uma funcionalidade real de `app/`
(bootstrap do primeiro administrador via variável de ambiente, rodando em qualquer profile), o
que já satisfaz a regra da constitution sem precisar extrair nada. Ver
[`specs/05-026-bootstrap-admin-remetente-email/spec.md`](../05-026-bootstrap-admin-remetente-email/spec.md),
que também resolve, pelo mesmo raciocínio, o bootstrap do remetente de e-mail (hoje hardcoded e
duplicado à mão entre `email-lambda/application.properties` e
`docker/localstack/init/02-verify-ses-sender.sh`) — o administrador recém-criado precisa do
e-mail funcionando pra conseguir logar pela primeira vez.

Este arquivo fica como registro histórico do raciocínio que levou a essa mudança de direção —
não apagado, mesmo descartado (mesmo padrão da spec 05-022).
