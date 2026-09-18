# Spec: Gestão de sessões ativas (listar e revogar)

**Status:** rascunho
**Issue:** #<número da Issue-épico, quando criada>
**Iteração:** iteration-5

## Resumo

O jogador autenticado passa a poder listar os dispositivos com sessão ativa (`GET`) e encerrar
(revogar) qualquer uma delas (`DELETE`), inclusive a partir de outro dispositivo.

## Motivação

`LOGIN_SESSION` já existe e já é usado pra aplicar o limite de dispositivos simultâneos
(`login.feature`), mas hoje é só um registro interno — o jogador não tem nenhuma visibilidade
nem controle sobre ele. Esta spec expõe essa informação e dá controle explícito, e serve como o
teste de ponta a ponta mais natural pra spec 05-009 (identificação de dispositivo): a melhor
forma de confirmar que o rótulo de Client Hints está funcionando é ver dois rótulos diferentes
na lista depois de simular login de dois dispositivos diferentes num teste.

## Cenários (comportamento esperado)

- `app/src/test/resources/features/manage_active_sessions.feature` (novo)

## Requisitos funcionais

- `GET /sessions` — lista as sessões ativas (`ended_at IS NULL`) do jogador autenticado: id,
  rótulo do dispositivo (spec 05-009), data de início, e uma flag indicando se é a sessão da
  própria requisição atual.
- `DELETE /sessions/{sessionId}` — encerra a sessão indicada, **se pertencer ao jogador
  autenticado**; `404` se não existir ou pertencer a outro usuário (não revela existência, mesmo
  padrão de `requestOrConfirmEntry`/`getCompetitionDetail`).
- Encerrar uma sessão a torna de fato inutilizável no próximo request daquele dispositivo (não
  só marca `ended_at` no nosso registro) — ver "Decisões de arquitetura" no `plan.md` sobre como
  isso se liga à sessão HTTP real (`spring.session.store-type: jdbc`).
- Encerrar a própria sessão atual (a que fez a requisição de `DELETE`) é permitido — o jogador é
  deslogado do dispositivo atual também.
- Cenário dedicado (a pedido explícito): logar em duas "sessões" com Client Hints/User-Agent
  diferentes simulando dois dispositivos, listar, e confirmar que os dois rótulos aparecem
  distintos e reconhecíveis — usa esta operação pra validar a spec 05-009 de ponta a ponta, além
  de validar esta própria spec.

## Requisitos não-funcionais

- Nenhuma sessão de outro usuário é visível ou revogável, sob nenhuma circunstância.

## Fora de escopo

- Notificar o dispositivo revogado (ex. e-mail "sua sessão foi encerrada") — só o efeito (sessão
  para de funcionar), sem notificação.
- Revogar todas as sessões de uma vez ("sair de todos os dispositivos") — só uma por vez, no
  escopo desta spec.
- Qualquer UI/frontend — só o back-end (API-first).

## Decisões em aberto

Nenhuma — decisões técnicas (como a revogação invalida a sessão HTTP de fato, onde vive o
controller) resolvidas em conversa antes de escrever este documento, ver `plan.md`.
