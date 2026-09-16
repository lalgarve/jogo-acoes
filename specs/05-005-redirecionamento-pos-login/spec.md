# Spec: Redirecionamento pós-login para o destino original

**Status:** rascunho
**Issue:** [#51](https://github.com/lalgarve/jogo-acoes/issues/51)
**Iteração:** iteration-5

## Resumo

Quando o jogador tenta acessar uma página que exige sessão (ex. o detalhe de uma competição,
ou a lista de "minhas competições" da spec [05-004](../05-004-visao-do-jogador-sobre-competicoes/spec.md))
sem estar logado, o link de login que ele recebe por e-mail passa a levá-lo direto pra essa
página, em vez do destino padrão genérico (lista de competições ou página de administração).

## Motivação

Hoje `requestLoginLink` sempre manda pro mesmo lugar, dependendo só do papel do usuário — não
existe conceito de "eu estava tentando chegar em algum lugar específico". Sem isso, todo fluxo
que exige login no meio do caminho (por exemplo, um jogador clicando num link de competição
pública que descobriu através da spec 05-004, sem sessão) larga o jogador de volta na lista
genérica, obrigando-o a navegar de novo até onde queria ir.

## Cenários (comportamento esperado)

- `app/src/test/resources/features/redirect_after_login.feature`

## Requisitos funcionais

- **Pedido de login carrega o destino pretendido**: `POST /login-requests` passa a aceitar um
  campo opcional com a página que o jogador estava tentando acessar quando não estava logado.
  Quando ausente (comportamento de hoje, ex. jogador que clica em "entrar" sem ter tentado
  acessar nada específico antes), o redirecionamento cai no padrão já existente por papel
  (administrador → página de administração; jogador → lista de competições).
- **Consumo do link honra o destino pretendido**: se o link carrega um destino válido, o
  jogador é redirecionado pra ele ao consumir o link — tanto no caminho normal quanto no atalho
  de "já autenticado neste dispositivo" (`alreadyAuthenticated`, ver spec 05-003).
- **Um novo pedido de login substitui o destino do pedido anterior**: já existe a regra de só
  um link de login avulso ativo por vez por usuário (spec 05-003, "Requesting a new login link
  invalidates the previous one") — o destino pretendido acompanha essa mesma invalidação, sem
  regra nova de expiração/substituição própria.

## Requisitos não-funcionais

- **Segurança — só destino do próprio site**: o destino pretendido só pode ser um caminho
  relativo do próprio site (começa com `/`, sem esquema/host embutido, ex. nunca
  `https://...`). Qualquer valor que não seja esse formato é descartado silenciosamente,
  caindo no redirecionamento padrão por papel — evita que o mecanismo vire um open redirect
  (`POST /login-requests` não exige sessão, então o valor vem de quem quer que faça o pedido,
  não necessariamente do próprio jogador).

## Fora de escopo

- Links de competição (`CompetitionLinkHandler`) não ganham esse mecanismo — eles já sabem seu
  próprio destino (a competição que os originou), não precisam de um destino externo.
- Qualquer UI/frontend que efetivamente capture "a página que o jogador tentava acessar" e
  monte o pedido — esta spec cobre só o back-end aceitar, validar e honrar esse destino.
- Persistir o destino pretendido além da validade normal do link (7 dias, spec 05-003) — usa o
  mesmo `LinkRecord`/`extraJson` já existente, sem mecanismo de expiração próprio.

## Decisões em aberto

Nenhuma decisão de requisito em aberto — resolvida em conversa antes de escrever este
documento (ver `docs/context/iteracao-5.md`). Decisões técnicas (nome do campo, formato da
resposta, onde a validação acontece) estão em `plan.md`.
