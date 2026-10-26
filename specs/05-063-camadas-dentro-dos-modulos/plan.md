# Plan: Camadas dentro dos módulos

Traduz `spec.md` em decisões técnicas. Valida contra `memory/constitution.md`.

## Contexto técnico

- `app` e `email-service` já têm `ArchitectureTest` com ArchUnit (`@AnalyzeClasses`, só
  produção).
- O `app` já tem `repositoriesAreOnlyAccessedFromTheirOwnModule`, que identifica repository por
  `assignableTo(org.springframework.data.repository.Repository)`.
- Controllers implementam as interfaces geradas do OpenAPI (`api`), anotados com
  `@RestController`. Não há `@Controller` hoje.
- `email-service/.../disposable/` e `disposable/batch/` usam `JdbcTemplate` em cinco classes e
  `DataSource` em `ImportDisposableDomainsJobConfig`; a entidade `DisposableDomainSource` não mapeia
  `content_sha256`.

## Decisões de arquitetura

| Pergunta | Decisão | Status | Raciocínio |
|---|---|---|---|
| Como identificar as camadas | Predicados do ArchUnit: controller = `annotatedWith(RestController)` ou `annotatedWith(Controller)`; repository = `assignableTo(Repository)` do Spring Data ou `annotatedWith(org.springframework.stereotype.Repository)`; service = o resto | adotada | Os módulos não têm subpacote por camada; anotação e tipo não dependem de nome de classe. |
| Forma das regras | Quatro `@ArchTest`/`@Test` em cada `ArchitectureTest`, uma por requisito (só repository usa API de banco; controller sem repository; ninguém depende de controller; repository sem service/controller), em vez de `layeredArchitecture()` | adotada | `layeredArchitecture()` define camadas por pacote. Com predicados, regras separadas dão mensagem mais clara de qual regra quebrou. |
| Classes geradas (`api`) | Excluídas das três regras (`resideOutsideOfPackage("..api..")` no lado de quem depende) | adotada | O controller implementa a interface gerada; o código gerado não é nosso. |
| API de banco | Uma regra a mais: classes fora da camada repository não dependem de `JdbcTemplate`, `NamedParameterJdbcTemplate`, `JdbcClient`, `EntityManager` nem `DataSource` | adotada (Leila, 2026-10-26) | Só repository acessa o banco. |
| Refatoração do `disposable` | `DisposableDomainRepository` e `ImportExecutionRepository` novos (`@Repository`, `JdbcClient`); `DisposableDomainSourceRepository` com `@Modifying @Query` para os três `UPDATE`; `content_sha256` mapeado; `batchWriter` devolve `ItemWriter<String>`, então o `ImportDisposableDomainsJobConfig` não vê nem `DataSource` nem `JdbcBatchItemWriter` | adotada (Leila, 2026-10-26) | Diagramas e tabela por classe na `spec.md`. O SQL é copiado como está, sem reescrever. |
| Código | Duplicado no `app` e no `email-service`, como na 05-061 | adotada (Leila, 2026-10-26, mesma decisão da 05-061) | Os dois serviços não compartilham código Java, nem em teste (spec 05-034). |
| Testar a regra | Para cada regra, um teste com classes de exemplo dentro de `src/test` (um controller com repository, um service com controller, um repository com service), importadas com `ClassFileImporter` só para esse teste, confere que a regra falha | adotada | Os cenários 2 a 5 não podem ficar no código de produção; sem isso, uma regra que nunca falha passaria despercebida. |

## Estrutura de módulos/pacotes

- `app/src/test/java/dev/leilaalgarve/jogoacoes/common/ArchitectureTest.java` e exemplos de
  violação num subpacote de teste próprio (fora do `@AnalyzeClasses` principal).
- `email-service/src/test/java/dev/leilaalgarve/jogoacoes/emailservice/common/` idem.
- `memory/constitution.md`, seção "Fronteira entre módulos e serviços".

## Riscos e trade-offs

- **Refatoração da importação em lote**, que a spec 05-054 acabou de estabilizar. Mitigação: o
  SQL é movido sem mudar, a suíte do `email-service` cobre a importação (Cucumber), e a
  verificação manual roda uma importação de verdade no ambiente local.
- **`@StepScope` do writer:** hoje o writer é um bean `@StepScope` que recebe `sourceId` e o id
  da execução por `@Value`. Continua assim no `ImportDisposableDomainsJobConfig`, que só troca o
  corpo por uma chamada a `batchWriter(sourceId, executionId)`.

- **Classes de exemplo em `src/test`** violam as regras de propósito. Elas não podem entrar no
  `@AnalyzeClasses` dos outros testes: o import principal já usa `DoNotIncludeTests`, e os
  exemplos ficam num pacote que só o teste da regra importa.
- **Camada "service" larga:** tudo o que não é controller nem repository cai nela (entidades,
  filtros, configuração). É de propósito: a regra só quer impedir atalhos entre as três
  camadas, não classificar cada classe.

## Critério de aceite: exceções

Nenhuma classe de exceção nova. A falha do teste é um `AssertionError` do JUnit/ArchUnit.
