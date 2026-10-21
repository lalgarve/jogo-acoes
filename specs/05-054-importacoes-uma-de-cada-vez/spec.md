# Spec: Importações das listas de e-mail temporário uma de cada vez

**Status:** rascunho
**Issue:** a criar
**Iteração:** iteration-5

## Resumo

As importações das listas de domínios de e-mail temporário (spec
[05-048](../05-048-importacao-listas-email-temporario/spec.md)) passam a rodar uma de cada vez.
Hoje, "importar todos os repositórios" dispara um job do Spring Batch por repositório, todos ao
mesmo tempo, e quando dois jobs gravam o registro de um passo no mesmo instante o PostgreSQL
cancela um deles: a importação daquele repositório termina `FAILED` e ninguém tenta de novo.

## Motivação

- **Falha observada no CI** (2026-10-20, push da PR #195 em `development`; 2026-10-21, PR #196):
  o cenário "Importing a list that gained one domain and lost another" falha às vezes. O log do
  `email-service` mostra uma das duas importações terminando `FAILED` com
  `CannotAcquireLockException ... INSERT INTO BATCH_STEP_EXECUTION_CONTEXT ... could not
  serialize access due to read/write dependencies among transactions`.
- **Causa** (análise de 2026-10-21):
  - `DisposableDomainImportService.start(null)` dispara um job por repositório, um logo depois do
    outro (3 ms de diferença no log). O executor (`SimpleAsyncTaskExecutor`) roda cada um numa
    thread própria, então eles rodam em paralelo.
  - Antes de cada passo (`downloadStep`, `importStep`, `pruneStep`), o Spring Batch grava o
    registro do passo numa transação curta (`JobRepository.createStepExecution`): `INSERT` em
    `batch_step_execution` e `INSERT` em `batch_step_execution_context`, cuja chave estrangeira
    o PostgreSQL confere com `SELECT ... FOR KEY SHARE`.
  - O Spring Batch roda todo método `create*` do repositório com isolamento `SERIALIZABLE`
    (`isolationLevelForCreate`, padrão `ISOLATION_SERIALIZABLE`, conferido no
    `spring-batch-core` 6.0.4). O projeto não muda esse padrão.
  - Com duas dessas transações ao mesmo tempo, o PostgreSQL vê uma dependência de leitura e
    escrita em ciclo entre elas e cancela uma. As tabelas do Spring Batch são pequenas e os
    registros novos ficam na mesma página do índice, então registros diferentes já bastam para o
    conflito (leitura de como o PostgreSQL faz esse controle, coerente com a mensagem
    `conflict out to pivot ..., during read`).
  - O job cancelado termina `FAILED` e o passo seguinte não roda. Nada tenta de novo.
- **Produção**: a importação diária (`email.disposable-domains.import-cron`, 04:00 em
  `staging`/`production`) usa o mesmo caminho. Com mais de um repositório cadastrado, um deles
  pode ficar sem atualizar até o dia seguinte.
- **Teste que não percebe**: o passo Cucumber "the import of every repository already ran" só
  espera as importações terminarem, sem conferir que terminaram `COMPLETED`. Na falha de
  2026-10-21, a importação da preparação falhou e o erro só apareceu depois, como uma contagem
  errada (`added` 2 em vez de 1).

## Cenários (comportamento esperado)

Os cenários de `email-service/src/test/resources/features/disposable_domains.feature` continuam
valendo. Muda o que acontece por baixo deles:

1. **Importar todos os repositórios:** com três repositórios cadastrados, o pedido responde `202`
   com as três execuções, como hoje. As três terminam `COMPLETED`, e nenhuma começa antes de a
   anterior terminar.
2. **Pedidos separados:** a importação de um repositório pedida enquanto outra está rodando
   (de outro repositório, sob pedido ou agendada) espera a primeira terminar e então roda.
3. **Repositório que falha:** uma importação que termina `FAILED` (lista vazia, download que
   falha) não impede a seguinte de rodar, como já diz o cenário "A repository that fails changes
   nothing and does not stop the others".
4. **Já em andamento:** pedir a importação de um repositório que está na fila ou rodando continua
   respondendo como hoje ("Importing a repository that is already being imported").

## Requisitos funcionais

- Em todo o `email-service`, no máximo um job de importação roda por vez. As execuções pedidas
  enquanto uma roda esperam na ordem do pedido.
- O contrato não muda (`docs/openapi-email-service.yaml`): `POST /disposable-domain-imports`
  continua respondendo `202` com todas as execuções criadas, e cada uma pode ser acompanhada por
  `GET /disposable-domain-imports/{executionId}` desde o primeiro momento.
- O passo de preparação "the import of every repository already ran" exige que cada importação
  termine `COMPLETED`.

## Requisitos não-funcionais

- Uma instância só do `email-service` é o que existe hoje em todos os ambientes. Com mais de uma
  instância, cada uma teria a própria fila; coordenar importações entre instâncias fica fora
  desta spec.

## Fora do escopo

- Tentar de novo automaticamente uma importação que falhou: a seguinte é a do dia seguinte ou um
  pedido de operações, como hoje.
- Mudar o modelo da spec 05-048 (uma execução por repositório, histórico por repositório).

## Decisões em aberto

1. **Como tirar o paralelismo** (ver `plan.md`):
   - **A.** Fila de uma importação por vez: as execuções continuam criadas na hora; o executor
     roda um job de cada vez. O contrato não muda.
   - **A + `READ_COMMITTED`:** A, e o Spring Batch cria execuções com `READ_COMMITTED` em vez de
     `SERIALIZABLE`. Fecha também o caso que A deixa: a criação de uma execução, na thread do
     pedido, coincidindo com a criação do registro de um passo do job que está rodando.
   - **B.** Disparar o próximo job só quando o anterior termina: o `202` deixa de trazer as
     execuções seguintes, que ainda não existem. Muda o contrato.
   - **C.** Um job só para todos os repositórios: muda o modelo da 05-048.

   Recomendação: **A + `READ_COMMITTED`**.
2. **Onde registrar:** esta spec nova, ou uma decisão nova dentro da 05-048.
