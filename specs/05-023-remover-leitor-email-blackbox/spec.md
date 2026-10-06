# Spec: Remover a leitura de e-mail de `app/` — Python lê o LocalStack direto

**Status:** implementada (tabela conferida contra o `master` em 2026-10-06; ver `tasks.md`)
**Issue:** parte da [Issue #84](https://github.com/lalgarve/jogo-acoes/issues/84) (a fatia
`blackbox/` do escopo)
**Iteração:** iteration-5 (Etapa 2 antecipada — o desenho ficou pequeno o suficiente pra caber
aqui em vez de esperar)

## Resumo

Apaga `BlackboxController.java` e `BlackboxSecurityConfigContributor.java` de `app/` —
**zero código Java substituto**. A leitura do link do último e-mail enviado durante um teste
blackbox (hoje via `GET /blackbox/last-email`) passa a ser feita inteiramente do lado Python
(`blackbox-tests/`), lendo o endpoint de debug do próprio LocalStack (`GET /_aws/ses`) e
extraindo o link do corpo HTML via regex — a mesma fonte de dados que a verificação manual da
Issue #87 já usa, só que consumida programaticamente em vez de por um `curl` manual.

`BlackboxDataSeeder` (a terceira classe do pacote `blackbox/`) **não muda nesta spec** — ver
"Fora de escopo".

## Motivação

A Issue #84 propunha originalmente extrair o pacote `blackbox/` inteiro de `app/` pra um módulo
próprio, espelhando `blackbox-proxy/` (spec 05-020) — a regra da `memory/constitution.md`
("código de teste/dev nunca dentro da aplicação, mesmo atrás de profile/flag") não abre exceção
pra nenhuma das três classes.

Investigação desta sessão: ao contrário de `blackbox-proxy/` (um processo genuinamente
independente, sem estado compartilhado com `app/`), `BlackboxController` e
`BlackboxSecurityConfigContributor` só existem porque nunca houve outra forma de ler o link de
um e-mail enviado durante um teste (`POST /login-requests` nunca revela o link no corpo, de
propósito). Mas o único consumidor desse endpoint é sempre o `blackbox-tests/` (Python) — que já
fala diretamente com a infraestrutura real do ambiente de teste pra outras coisas (fila SQS via
`awslocal`, e agora, desde a Issue #87, o próprio LocalStack hospeda a função Lambda real).
Não há razão pra `app/` expor HTTP só pra isso: o Python pode ler a mesma informação
(`GET /_aws/ses`, já verificado nesta sessão que devolve o e-mail renderizado por completo,
`Destination`/`Subject`/`Body.html_part`/`Timestamp`) direto da fonte.

Resultado: em vez de mover duas classes pra um módulo novo (custo: mais um módulo Maven, mais um
processo pra subir), elas simplesmente deixam de existir — solução mais simples encontrada
depois da investigação inicial (que ainda cogitava um módulo Spring Boot próprio) ter sido
descartada pelo usuário nesta sessão.

## Cenários (comportamento esperado)

Não aplicável — infraestrutura/ferramenta de teste, sem `.feature` novo (mesmo padrão de
specs 05-014/05-018/05-020). O comportamento observável pelos testes Python não muda: todo
cenário/fluxo que hoje chama `last_email`/`last_email_link` continua funcionando, só que lendo
de uma fonte diferente por baixo.

## Requisitos funcionais

- Apagar `app/src/main/java/.../blackbox/BlackboxController.java` e
  `BlackboxSecurityConfigContributor.java`.
- Atualizar `BlackboxProfileIntegrationTest.java`: remover os dois testes que cobriam
  `GET /blackbox/last-email` (`lastEmailReturnsTheMostRecentLinkSentToAnAddress`,
  `lastEmailReturnsNotFoundWhenNothingWasSentToTheAddress`) e a infraestrutura só usada por eles
  (`SentEmailRepository` injetado, helper `saveSentEmail`, imports órfãos); manter os testes de
  captcha e do seeder como estão.
- `blackbox-tests/common/blackbox_fixtures.py`: `last_email`/`last_email_link` passam a chamar
  `GET {LOCALSTACK_URL}/_aws/ses` (padrão `http://localhost:4566`, sobrescrevível por variável
  de ambiente, mesmo padrão já usado por `API_BASE_URL`), filtrar as mensagens por
  `Destination.ToAddresses` (o filtro nativo `?email=` da própria API filtra pelo *remetente*,
  não serve pra achar por destinatário — confirmado contra a spec oficial do LocalStack nesta
  sessão), pegar a mensagem mais recente (`messages` vem em ordem de envio) e extrair o link via
  regex (`href="...login-links..."`) do `Body.html_part`.
- Remover o parâmetro `base_url`/`api_base_url` de `last_email`/`last_email_link` — não faz mais
  sentido pra essas funções: o endereço do LocalStack é fixo pro ambiente de teste inteiro, não
  varia por dispositivo/cliente simulado como a URL da API varia. Atualizar todos os call sites:
  `seed/flows.py` (4 ocorrências), `seed/__main__.py` (1), `features/steps/
  manage_active_sessions_steps.py` (3), `features/steps/public_competition_entry_steps.py` (2),
  `tests/test_mailbox.py` (1).
- `last_email` apaga a mensagem que encontrou (`DELETE {LOCALSTACK_URL}/_aws/ses?id=<Id>`,
  pelo `Id` exato devolvido no `GET`) antes de devolver o resultado — ler um e-mail passa a
  significar consumi-lo. Evita que a caixa do LocalStack cresça sem limite ao longo de uma
  sessão de teste longa, sem recorrer a um `DELETE /_aws/ses` geral (sem filtro) em
  `@BeforeEach`/`@AfterEach`, que apagaria mensagens de outros testes rodando ao mesmo tempo
  contra o mesmo LocalStack compartilhado — como os endereços já são únicos por teste
  (`success+<qualificador>-<uuid>@...`, convenção da spec 05-016), apagar só o `Id` encontrado
  nunca risca a mensagem de outro teste concorrente.
- Campo `template` de `LastEmail` é removido — nenhum call site lê esse campo hoje (confirmado
  por busca no código desta sessão); o par `Template`/`TemplateData` que a API do LocalStack
  devolve se refere ao recurso nativo de templates do SES (não usado por `EmailSendHandler`,
  que manda `Subject`/`Body` já renderizados), não ao enum `EmailTemplate` deste projeto — não
  dá pra recuperar essa informação da resposta do LocalStack mesmo se algum caller precisasse.
- Atualizar documentação que menciona `GET /blackbox/last-email`: `README.md`,
  `blackbox-tests/README.md`.

## Requisitos não-funcionais

- Comportamento observado pelos testes/cenários Python não muda (mesmos links devolvidos,
  mesmo erro pra endereço nunca usado) — troca de mecanismo por baixo, não de resultado:
  `NoEmailSentError` continua sendo levantado quando nada foi enviado ainda pro endereço (antes:
  404 do endpoint Java; agora: lista vazia depois do filtro por destinatário).
- Nenhuma rota nova em `docs/openapi.yaml` — a rota apagada nunca fez parte do contrato, e não
  está sendo substituída por nenhuma outra.
- Seguro para testes concorrentes contra o mesmo LocalStack: nenhuma operação de leitura/limpeza
  desta spec apaga ou interfere em mensagens que não foram enviadas para o endereço que o
  próprio teste está consultando.

## Fora de escopo

- **`BlackboxDataSeeder`** — continua em `app/`, atrás de `@Profile("blackbox")`, sem nenhuma
  mudança nesta spec. Ver "Decisões em aberto": tecnicamente ainda viola a regra da constitution
  ("nunca código de teste em módulo de produção, mesmo atrás de flag"), mas mover exigiria um
  módulo novo com conexão própria ao Postgres e entidades JPA duplicadas de `User`/`Role`/
  `UserRole` (não existe API de criação de administrador, de propósito — é por isso que esse
  seeder existe). Custo e desenho não avaliados aqui.
- **Issue #92** (link enviado no e-mail é um caminho relativo, não uma URL absoluta) — resolvida
  em separado; o regex desta spec captura o valor do `href` como estiver, relativo ou absoluto,
  sem se importar com qual dos dois é.
- Qualquer mudança em `docs/openapi.yaml`.

## Decisões em aberto

1. ~~**`BlackboxDataSeeder` fica em `app/` como exceção documentada, ou ganha spec própria**~~
   — **Resolvida:** ganha spec própria. Ver
   [`specs/05-024-extrair-blackbox-data-seeder/spec.md`](../05-024-extrair-blackbox-data-seeder/spec.md).
   A Issue #84 só fecha de vez depois que as duas specs (esta e a 05-024) estiverem
   implementadas.
