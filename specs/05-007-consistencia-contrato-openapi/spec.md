# Spec: Consistência entre contrato OpenAPI e implementação

**Status:** rascunho
**Issue:** #<número da Issue-épico, quando criada>
**Iteração:** iteration-5

## Resumo

Testes automatizados que comparam `docs/openapi.yaml` — paths/método de cada operação e a
extensão `x-roles` — contra o que está de fato registrado em runtime: as rotas reais (via
`RequestMappingHandlerMapping`, mesmo mecanismo da spec 05-006) e a autorização real aplicada
pelo `SecurityFilterChain` (via `WebInvocationPrivilegeEvaluator`, resultado dos
`SecurityConfigContributor` por módulo também introduzidos na 05-006).

## Motivação

Desde a spec 05-004, `x-roles` é uma extensão vendor do OpenAPI — documentação pura, sem
nenhuma aplicação em runtime; o `plan.md` daquela spec já sinalizava o risco de divergir da
regra real com o tempo, e a 05-006 reafirmou o mesmo risco como trabalho futuro. O projeto segue
API-first (decisão de processo desde as specs 05-004/05-005 — contrato antes de código) — faz
sentido garantir automaticamente que o contrato continua sendo a fonte de verdade, nas duas
direções: nenhuma rota é exposta sem estar no contrato, e nenhum papel documentado diverge do
que o `SecurityConfig`/contributors realmente aplicam. A 05-006 já constrói a peça que faltava
pra isso ser barato: `SecurityConfigContributor` por módulo dá um ponto único de leitura da
autorização real, e o teste de `RequestMappingHandlerMapping` da mesma spec já sabe extrair rota
real por módulo.

## Cenários (comportamento esperado)

Verificação estrutural/de contrato, não comportamento de usuário — sem `.feature` novo, mesmo
padrão da 05-006. O critério de aceite são os dois testes dedicados descritos em "Requisitos
funcionais", verdes contra o estado atual do `docs/openapi.yaml` e do código.

## Requisitos funcionais

- **Consistência de rotas**: teste dedicado lê `docs/openapi.yaml` (path + método de cada
  operação) e compara contra as rotas reais obtidas via `RequestMappingHandlerMapping` (mesmo
  mecanismo da spec 05-006, filtrado ao pacote-base do projeto). Falha se existir rota
  implementada sem operação correspondente no contrato — o caso que realmente importa, ver
  `plan.md` sobre por que a direção oposta é estruturalmente quase garantida pelo próprio
  processo de geração de código — ou vice-versa.
- **Consistência de papéis**: para cada operação do contrato, o `x-roles` declarado é comparado
  contra o resultado real de autorização (`WebInvocationPrivilegeEvaluator`) para esse
  path+método, testado com três autenticações representativas: anônimo, `ROLE_PLAYER`,
  `ROLE_ADMINISTRATOR`. Falha se o resultado documentado (`x-roles`) e o resultado real
  divergirem para qualquer uma das três.

## Requisitos não-funcionais

- **Nenhuma mudança de comportamento**: puramente testes novos — nem rota, nem regra de
  autorização, nem `docs/openapi.yaml` mudam nesta spec; só a verificação automática de que os
  três já batem entre si hoje.
- **Sem dependência nova pesada**: parsing de `docs/openapi.yaml` usa SnakeYAML (já presente no
  classpath via Spring Boot, confirmado nesta sessão via `mvn dependency:tree`), sem adicionar
  um parser OpenAPI completo (`swagger-parser` ou similar) só pra ler três campos por operação.

## Fora de escopo

- Não implementa nenhuma correção nos dois testes se algo divergir ao rodar contra o estado
  atual — se acontecer, essa spec entrega os testes vermelhos e a correção (do lado que estiver
  errado, contrato ou código) fica como tarefa própria dentro desta mesma spec, não uma spec
  nova.
- Não valida nada além de rota (path+método) e `x-roles` — não compara schema de
  request/response, nem `summary`/`description`, nem os demais campos do contrato.
- Não substitui a suíte Cucumber como critério de aceite comportamental — ela continua sendo o
  teste de comportamento; esta spec testa só a fidelidade contrato↔implementação.

## Decisões em aberto

Nenhuma — decisões técnicas (mecanismo de comparação de rotas, mecanismo de comparação de
papéis, onde vivem os testes) resolvidas em conversa antes de escrever este documento, ver
`plan.md`.
