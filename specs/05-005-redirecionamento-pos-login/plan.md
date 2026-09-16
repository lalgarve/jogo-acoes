# Plan: Redirecionamento pós-login para o destino original

Traduz `spec.md` em decisões técnicas. Valida contra `memory/constitution.md`.

## Contexto técnico

Reaproveita o mecanismo de link genérico da spec 05-003 (`LinkService`/`LinkRouter`/
`LinkHandler`/`LinkPayload.extra`) — nenhuma migração de banco nova, `extra` já é o bag opaco
próprio pra dado específico de cada `LinkHandler`. Toda a mudança fica em `login/`
(`LoginController`, `LoginLinkHandler`) mais o schema `docs/openapi.yaml`.

## Decisões de arquitetura

| Pergunta | Decisão | Status | Raciocínio |
|---|---|---|---|
| Nome do campo novo em `POST /login-requests` | `returnTo` (string, opcional) em
`RequestLoginLinkRequest` — carregado pra `LinkPayload.extra["returnTo"]` no `create("login", ...)`, mesma chave usada de volta na leitura. | resolvida | Segue o padrão já usado por `CompetitionLinkHandler` (`participationId` dentro de `extra`) — nome descritivo, sem introduzir um campo novo em `LinkRecord`. |
| Onde a validação de "só caminho relativo do próprio site" acontece | Em `LoginController.requestLoginLink`, antes de montar o `LinkPayload` — regex simples (`^/(?!/)...`, começa com uma barra só, sem `//` logo depois pra não permitir `//evil.com` estilo protocol-relative). Valor inválido é tratado como ausente (`extra` vazio), não gera erro pro chamador. | resolvida | Barreira de segurança (ver "Requisitos não-funcionais" em spec.md) o mais cedo possível, antes do dado entrar no link; falhar silenciosamente (em vez de rejeitar o pedido) evita expor a validação como oráculo, e não é informação crítica — o pior caso é cair no redirecionamento padrão. |
| `LoginLinkHandler.redirectFor` honra o destino | Passa a receber o `LinkPayload` inteiro (hoje só recebe `Long userId`) e checa `payload.extra().get("returnTo")` antes de calcular o padrão por papel — usado tanto em `consume` quanto em `alreadyAuthenticated`. | resolvida | Único ponto que já decide o destino hoje; menor mudança possível — não duplica a lógica de "por papel" em outro lugar. |
| Contrato de `LoginResult.redirectTo` | Muda de `enum [competition-page, competitions-list, admin-page]` pra `string` (caminho, ex. `/admin`, `/competitions/mine`, `/competitions/42`) — servidor já resolve o caminho completo, cliente só navega até ele. Campo `competitionId` (hoje separado) é removido — o id já vem embutido no caminho quando aplicável. | resolvida | Um destino arbitrário do próprio site (`returnTo`) não cabe num enum fechado de 3 valores; gerar o caminho completo no servidor evita o cliente ter que remontar URL a partir de um símbolo + id solto. **Muda o contrato de 05-003** — ver "Riscos" abaixo. |
| Caminhos padrão (sem `returnTo`) usam esse mesmo formato | Sim — `admin-page` vira `/admin`; `competitions-list` vira `/competitions/mine` (o novo caminho da spec 05-004); `competition-page` vira `/competitions/{id}` (mesmo `id`, agora embutido no caminho, spec 05-004). | resolvida | Consistência — um único formato de resposta (caminho), não um enum pros casos padrão e string livre só pro `returnTo`. |

## Estrutura de módulos/pacotes

Nenhum pacote novo — mudança contida em `login/` (`LoginController`, `LoginLinkHandler`) e
`docs/openapi.yaml` (`RequestLoginLinkRequest.returnTo`, `LoginResult.redirectTo` de enum pra
string, remoção de `LoginResult.competitionId`). `CompetitionLinkHandler.redirectFor` também
muda seus valores literais de `redirectTo` pro novo formato de caminho (`/competitions/{id}`),
sem ganhar o mecanismo de `returnTo` em si (fora de escopo, ver spec.md).

## Riscos e trade-offs

- **Muda um contrato já implementado (spec 05-003, PR #48 mergeado)** — `LoginResult.redirectTo`
  deixa de ser um enum fechado. Impacto conhecido: `LoginController.toLoginResult`,
  `LoginLinkHandlerTest`, `CompetitionLinkHandlerTest`, e os passos Cucumber que hoje leem
  `redirectTo`/`competitionId` do resultado (`LoginSteps`, ver `common/testsupport/`) precisam
  de ajuste — nenhum deles verifica valor exato do enum hoje ao ponto de quebrar
  silenciosamente, mas revisar cada um é tarefa própria em `tasks.md`.
- **Open redirect se a validação de `returnTo` falhar ou for removida sem querer** — mitigado
  pela validação descrita acima; um teste dedicado (`LoginControllerTest` ou equivalente) deve
  cobrir explicitamente um `returnTo` malicioso (`https://evil.com`, `//evil.com`) caindo no
  padrão, não sendo aceito.
