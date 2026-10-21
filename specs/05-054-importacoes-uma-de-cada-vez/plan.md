# Plan: Importações das listas de e-mail temporário uma de cada vez

Traduz `spec.md` em decisões técnicas. Valida contra `memory/constitution.md`.

## Contexto técnico

- `email-service/.../disposable/DisposableDomainImportService.java`: `start(sourceId)` é
  `synchronized`, confere as execuções em andamento (`findRunningJobExecutions`) e chama
  `JobOperator.start` para cada repositório. `JobOperator.start` cria a execução (`STARTING`) na
  thread do pedido e entrega o job ao executor.
- `email-service/.../disposable/batch/ImportDisposableDomainsJobConfig.java`: o executor é um
  `SimpleAsyncTaskExecutor("disposable-domain-import-")` marcado com `@BatchTaskExecutor`, uma
  thread nova por job, sem limite.
- `email-service/.../disposable/DisposableDomainImportScheduler.java`: a importação diária chama
  `start(null)`.
- Spring Batch 6.0.4 (`AbstractJobRepositoryFactoryBean`): os métodos `create*` do
  `JobRepository` (`createJobInstance`, `createJobExecution`, `createStepExecution`) rodam com
  `isolationLevelForCreate`, padrão `ISOLATION_SERIALIZABLE`.
- Spring Boot 4.1 (`spring-boot-batch-jdbc`): a propriedade
  `spring.batch.jdbc.isolation-level-for-create` muda esse isolamento.
- Teste: `DisposableDomainSteps.the_import_of_every_repository_already_ran()` espera o fim de
  cada execução (`awaitEnd`) sem conferir o status.

## Decisões de arquitetura

| Pergunta | Decisão | Status | Raciocínio |
|---|---|---|---|
| Como rodar uma importação por vez | Opção A: o `@BatchTaskExecutor` passa a ser um `ThreadPoolTaskExecutor` com uma thread (`corePoolSize` = `maxPoolSize` = 1) e fila sem limite, prefixo `disposable-domain-import-` | adotada (Leila, 2026-10-21) | A fila roda os jobs na ordem em que chegam, um de cada vez, em todo o serviço: vale para "todos os repositórios", para pedidos separados e para a importação diária. `JobOperator.start` continua devolvendo na hora a execução já criada, então o contrato não muda. Não serve `SimpleAsyncTaskExecutor` com `concurrencyLimit` 1: ele bloqueia a thread do pedido até a vaga liberar. |
| Criação das execuções | `spring.batch.jdbc.isolation-level-for-create: read-committed` no `application.yml` do `email-service` | adotada (Leila, 2026-10-21) | Com A, ainda podem coincidir a criação de uma execução (thread do pedido) e a criação do registro de um passo do job que está rodando, as duas em `SERIALIZABLE`. Cada criação só insere registros novos dela mesma; `READ_COMMITTED` basta, e o "já em andamento" já é protegido pelo `synchronized` de `start`. |
| "Já em andamento" | Sem mudança | adotada | Uma execução na fila está `STARTING`, e `findRunningJobExecutions` já a conta como em andamento. |
| Teste da preparação | `the_import_of_every_repository_already_ran()` confere `status` `COMPLETED` de cada execução depois de `awaitEnd` | adotada | A falha passa a aparecer no passo em que acontece. |
| Teste da fila | Teste de integração (`@SpringBootTest`) com três repositórios servidos pelo S3 do LocalStack, como os da 05-048: pede todos, espera o fim e confere nas tabelas do Spring Batch (`batch_job_execution.start_time`/`end_time`) que as três terminaram `COMPLETED` e que nenhuma começou antes de a anterior terminar. Repetido algumas vezes no mesmo teste, porque a falha de hoje é intermitente | adotada | Prova o comportamento, não a configuração do executor. |

## Estrutura de módulos/pacotes

- `email-service/src/main/java/.../disposable/batch/ImportDisposableDomainsJobConfig.java`: o
  executor.
- `email-service/src/main/resources/application.yml`: o isolamento.
- `email-service/src/test/java/.../disposable/steps/DisposableDomainSteps.java`: o passo de
  preparação.
- `email-service/src/test/java/.../disposable/`: o teste da fila.
- `specs/05-048-importacao-listas-email-temporario/plan.md`: nota na linha "Execução sob pedido".

## Riscos e trade-offs

- **Mais tempo para importar tudo:** com listas reais (dezenas de milhares de linhas, download
  de segundos), importar todos os repositórios leva a soma dos tempos, em vez do maior deles. Uma
  vez por dia, de madrugada, isso não pesa.
- **Execuções na fila quando o serviço para:** ficam `STARTING` no banco, e o "já em andamento"
  passa a recusar novas importações daqueles repositórios. Hoje o mesmo vale para um job que
  estava rodando quando o serviço parou; com a fila, mais execuções podem ficar nesse estado.
  Tratar execuções abandonadas fica fora desta spec; se for preciso, entra numa spec própria.
- **Uma instância só:** a fila é da instância. Ver "Requisitos não-funcionais" na spec.
