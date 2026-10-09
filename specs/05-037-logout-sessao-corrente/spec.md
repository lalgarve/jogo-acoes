# Spec: Logout da sessão corrente

**Status:** rascunho
**Issue:** —
**Iteração:** iteration-5
**Depende de:** [`05-010-gestao-sessoes-ativas`](../05-010-gestao-sessoes-ativas/spec.md) (implementada)

## Resumo

O jogador (ou o administrador) autenticado pode encerrar a sessão do dispositivo que está usando,
com uma única chamada, sem precisar saber o id dessa sessão.

## Motivação

Hoje não existe logout na API. O único jeito de sair é `DELETE /sessions/{sessionId}` (spec
05-010), que já aceita revogar a sessão da própria requisição — mas o cliente precisa antes
chamar `GET /sessions`, achar a linha com `current: true` e só então mandar o `DELETE` com o id.
Para a ação mais comum ("sair deste dispositivo") isso são duas chamadas e um passo de lógica no
cliente que não deveriam existir.

## Cenários (comportamento esperado)

- `app/src/test/resources/features/manage_active_sessions.feature` — três cenários novos (logout
  do dispositivo atual, sessão encerrada some da lista vista por outro dispositivo, logout sem
  estar logado). Texto exato em `plan.md`, a escrever no `.feature` antes do código.

## Requisitos funcionais

- `DELETE /sessions/current` encerra a sessão autenticada que fez a requisição: a próxima
  requisição desse dispositivo recebe `401`.
- O efeito é o mesmo de revogar a própria sessão por `DELETE /sessions/{sessionId}`: a
  `LOGIN_SESSION` correspondente ganha `ended_at` e a sessão HTTP (Spring Session JDBC) é
  apagada.
- As outras sessões do mesmo usuário (outros dispositivos) continuam válidas.
- Sem sessão autenticada: `401`, como em qualquer operação de `[PLAYER, ADMINISTRATOR]`.
- Resposta de sucesso: `204`, sem corpo.

## Requisitos não-funcionais

- Nenhuma sessão de outro usuário pode ser afetada — a operação não recebe id nenhum, então
  isso decorre do desenho, não de uma checagem.

## Fora de escopo

- "Sair de todos os dispositivos" de uma vez.
- Qualquer UI/frontend — só o back-end (API-first).
- Mudar `DELETE /sessions/{sessionId}`: continua existindo e continua aceitando a própria
  sessão.

## Decisões em aberto

Nenhuma de requisito. O nome do caminho (`current` em vez de `this`) está registrado no
`plan.md`.
