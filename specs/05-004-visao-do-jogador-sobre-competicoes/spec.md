# Spec: Visão do jogador sobre competições

**Status:** implementado (sessão 2026-09-16 — ver `plan.md` para achados feitos durante a
implementação e `tasks.md` para o checklist)
**Issue:** [#50](https://github.com/lalgarve/jogo-acoes/issues/50)
**Iteração:** iteration-5

## Resumo

Dá ao jogador uma forma de ver, sem precisar procurar e-mails antigos, quais competições ele
está participando, participou no passado, ou foi convidado/pediu entrada e ainda não
confirmou — mais um jeito de descobrir competições públicas abertas, independente de estar
logado. Complementa a documentação OpenAPI indicando, em cada operação, se ela é de
administrador, de jogador, ou de qualquer visitante.

## Motivação

Primeira feature desta iteração pensada do ponto de vista do jogador, não do administrador —
até aqui (specs 05-001/05-002/05-003) todo o trabalho foi reorganização interna, sem nenhum
comportamento novo visível. Hoje o jogador só descobre uma competição pelo link que recebe por
e-mail (convite ou confirmação de pedido de entrada); não existe uma tela/endpoint que reúna
"minhas competições" nem uma forma de navegar as competições públicas sem já ter um link em
mãos.

## Cenários (comportamento esperado)

- `app/src/test/resources/features/browse_public_competitions.feature`
- `app/src/test/resources/features/view_my_competitions.feature`

## Requisitos funcionais

- **Descoberta de competições públicas**: uma lista, sem exigir sessão, com as competições
  públicas abertas a pedido de entrada (`status = OPEN`) — competições privadas e competições
  já encerradas nunca aparecem nela. Cada item mostra só informação básica (ver "Informação
  básica" abaixo).
- **"Minhas competições" do jogador**: uma lista, exigindo sessão de jogador, agrupada em três
  categorias — participando (`IN_COMPETITION` numa competição `OPEN`), participado no passado
  (`IN_COMPETITION` numa competição `CLOSED`), convidado/pedido mas não confirmado (existe
  `Participation` para o jogador, mas o status ainda não é `IN_COMPETITION`, seja por convite
  privado ou pedido público). Cada item mostra informação básica.
- **Detalhe de uma competição**: nome e informação básica, mais o nível de acesso do
  solicitante:
  - **read-write**: o solicitante participa da competição (`IN_COMPETITION`) e ela está
    `OPEN`.
  - **read**:
    - o administrador não participa da competição, mas pode ver;
    - a competição já está `CLOSED` e o solicitante participou dela;
    - o solicitante foi convidado/pediu entrada mas ainda não confirmou — nesse caso a
      resposta também indica que dá pra confirmar a entrada a partir dessa mesma tela
      (reaproveita `POST /competitions/{competitionId}/entry-requests` já existente, sem
      corpo, autenticado — não é um mecanismo novo).
  - **negado**: o solicitante (jogador, não administrador) nunca participou nem foi convidado
    para aquela competição — `404`, não revela se a competição existe (mesmo padrão já usado
    em `EntryRequestService`).
- **Informação básica de uma competição**: nome, tipo, status, data de início, duração — não
  inclui taxas de compra/venda nem o id do criador nessa visão (ver plan.md para o formato
  exato de resposta).
- **Documentação OpenAPI por papel**: toda operação (novas e já existentes) marcada com quem
  pode chamá-la — administrador, jogador autenticado, ou qualquer visitante (ver plan.md para
  o formato da anotação).

## Requisitos não-funcionais

- **Direção de dependência**: esta feature não introduz nenhum mecanismo novo de sessão/link —
  reaproveita o que já existe (`SecurityContextHolder`, `LinkService` só indiretamente, via o
  que já cria/consome links hoje).

## Fora de escopo

- Qualquer ação de jogo em si (compra/venda de ações) — o app ainda não implementa isso; "acesso
  read-write" aqui só descreve a permissão, não uma funcionalidade de trading que ainda não
  existe.
- Paginação/ordenação/filtro nas listas — fica pra quando o volume de competições justificar.
- Mudar o mecanismo de convite/pedido de entrada em si (specs anteriores) — só consome o que já
  existe.
- Redirecionar o jogador de volta pra essa tela depois de logar quando ele tenta acessá-la sem
  sessão — é a spec [05-005](../05-005-redirecionamento-pos-login/spec.md), separada de
  propósito (ver motivação lá).

## Decisões em aberto

Nenhuma decisão de requisito em aberto — todas resolvidas em conversa antes de escrever este
documento (ver `docs/context/iteracao-5.md`). Decisões técnicas (formato exato da resposta,
onde vive a lógica de cálculo de acesso, formato da anotação de papel no OpenAPI) estão em
`plan.md`.
