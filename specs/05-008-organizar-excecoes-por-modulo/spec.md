# Spec: Organizar exceções por módulo em subpacote `exception/`

**Status:** implementado (sessão 2026-09-18 — ver `tasks.md` para o checklist)
**Issue:** [#56](https://github.com/lalgarve/jogo-acoes/issues/56)
**Iteração:** iteration-5

## Resumo

Aplica, aos módulos que já qualificam hoje, a convenção `exception/` já documentada (sessão da
spec 05-002, `docs/context/iteracao-5.md`): um módulo com duas ou mais exceções próprias move
essas classes para um subpacote `exception/` dentro do próprio módulo; um módulo com uma
exceção só continua com ela na raiz do pacote. Mudança puramente mecânica, sem alteração de
comportamento.

## Motivação

A convenção já existe desde a spec 05-002, mas sua aplicação foi decidida como **incremental**
("módulo a módulo, à medida que o trabalho avança"), não um requisito upfront daquela spec — na
prática, nunca chegou a ser aplicada a nenhum módulo. Hoje dois módulos já acumularam exceções
suficientes pra a regra valer a pena: `competition` (5 classes soltas na raiz do pacote) e
`link` (2). Sem o subpacote, a raiz de `competition/` mistura controllers, services,
repositories e exceções no mesmo nível, dificultando achar o que é o quê à medida que o módulo
cresce.

## Cenários (comportamento esperado)

Não aplicável — mudança estrutural, sem comportamento novo observável. Critério de aceite: a
suíte de testes existente continua passando 100%, sem nenhum cenário `.feature` alterado e
nenhuma asserção de teste unitário/integração mudando — só pacote e imports.

## Requisitos funcionais

- `competition/` (5 exceções: `CompetitionNotFoundException`, `CompetitionValidationException`,
  `EntryRequestValidationException`, `PlayerNotFoundException`, `PlayerValidationException`)
  ganha o subpacote `competition/exception/` — as 5 classes movem pra lá, pacote Java
  `dev.leilaalgarve.jogoacoes.competition.exception`.
- `link/` (2 exceções: `LoginLinkInvalidException`, `LoginLinkUsedOnAnotherDeviceException`)
  ganha o subpacote `link/exception/`, pacote `dev.leilaalgarve.jogoacoes.link.exception`.
- `captcha/` (1 exceção: `CaptchaInvalidException`) **não muda** — continua na raiz do pacote,
  por ter só uma exceção própria, conforme a regra já documentada.
- Todos os pontos que hoje importam essas classes são atualizados para o novo pacote: dentro do
  próprio módulo (services/controllers/handlers que lançam a exceção), em `common/ApiExceptionHandler`
  (que trata todas via `@ExceptionHandler`), e nos testes correspondentes
  (`CompetitionLinkHandlerTest`, `LinkServiceTest`).
- Nenhuma classe muda de nome, de mensagem, de hierarquia (`RuntimeException`) ou de mapeamento
  HTTP em `ApiExceptionHandler` — só o pacote.

## Requisitos não-funcionais

Nenhum além de manter build e suíte de testes verdes — não é mudança de comportamento,
performance ou segurança.

## Fora de escopo

- `captcha/` e qualquer outro módulo com uma exceção só (ou nenhuma) — regra não se aplica.
- O `dto/`/`client/` da mesma convenção — esta spec cobre só a parte `exception/`.
- **Não move nem renomeia `common/ApiExceptionHandler`**, mesmo a convenção original (05-002)
  ter documentado um `exception/` global na raiz de `{base}` com um `GlobalExceptionHandler` —
  o handler existente já cobre esse papel, só com nome/pacote diferentes do que ficou registrado
  na época. Divergência sinalizada em "Decisões em aberto", não corrigida aqui.
- Nenhuma exceção nova é criada, nenhuma existente é removida ou dividida.

## Decisões em aberto

- `common/ApiExceptionHandler` diverge do que a convenção de 05-002 registrou (`exception/`
  global na raiz de `{base}`, classe `GlobalExceptionHandler`) — mover/renomear pra alinhar
  também é trabalho válido, mas é uma decisão maior (pacote `common/` existe por outro motivo,
  ver spec 05-002) e não foi pedida nesta rodada. Fica para uma spec própria, se decidido fazer.
