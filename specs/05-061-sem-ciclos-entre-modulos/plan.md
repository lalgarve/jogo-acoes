# Plan: Nenhum ciclo de dependência entre módulos

Traduz `spec.md` em decisões técnicas. Valida contra `memory/constitution.md`.

## Contexto técnico

- ArchUnit já é dependência de teste dos quatro módulos Maven; `app` e `email-service` já têm
  `ArchitectureTest` com `@AnalyzeClasses`.
- `app/src/test/java/.../common/ArchitectureTest.java` já tem condições próprias
  (`ArchCondition`) que percorrem `JavaClass.getDirectDependenciesFromSelf()`, por exemplo
  `onlyBeAccessedFromTheirOwnModule()`.
- Ciclo existente: `competition.RequestType` usado por `email.EmailRequest` e
  `email.EmailServiceEmailSender`; `competition.{CompetitionService, EntryRequestService,
  PlayerManagementService}` usam `email.{EmailRequest, EmailSender, EmailTemplate}` e
  `email.exception.RecipientDomainRejectedException`.

## Decisões de arquitetura

| Pergunta | Decisão | Status | Raciocínio |
|---|---|---|---|
| De onde vêm as referências | `JavaClasses` importadas pelo ArchUnit (só produção, `ImportOption.DoNotIncludeTests`); para cada classe, `getDirectDependenciesFromSelf()` | adotada | O bytecode pega tudo o que a spec chama de referência, inclusive nome qualificado sem `import`. |
| Como montar o grafo (passos 1 e 2) | Nó = primeiro segmento de pacote depois do pacote base. Aresta `A → B` quando uma dependência sai de `A` e chega em `B`, com `A ≠ B`; cada aresta guarda uma referência de exemplo (`origem → destino`). Dependências para fora do pacote base (JDK, Spring) são ignoradas | adotada | Módulo novo entra sozinho. |
| Algoritmo (passo 3) | Tarjan para componentes fortemente conexos: todo componente com mais de um nó contém ciclo. Dentro de cada componente, uma busca em profundidade extrai um ciclo concreto para a mensagem | adotada (Leila: algoritmo próprio) | Tarjan é linear no tamanho do grafo e encontra todos os ciclos de uma vez (cenário 4), não só o primeiro. O componente mostra todos os módulos emaranhados; o ciclo concreto mostra o caminho a cortar. |
| Onde fica o código | Uma classe `ModuleDependencyGraph` (montagem + Tarjan) por serviço, no pacote de teste do `ArchitectureTest`, e um `@Test` em cada `ArchitectureTest`. O `app` e o `email-service` não compartilham código Java (spec 05-034, nem em teste), então a classe é duplicada | em aberto | Alternativa: um módulo Maven de teste compartilhado só com essa classe. Recomendação: duplicar, são poucas dezenas de linhas e não cria dependência entre serviços. |
| Teste do próprio algoritmo | Teste unitário de `ModuleDependencyGraph` com grafos montados à mão: sem ciclo, ciclo de 2, ciclo de 3, dois ciclos separados, auto-referência ignorada | adotada | Os cenários 2 a 4 da spec não podem ficar no código de produção; testar o algoritmo isolado cobre isso sem plantar ciclos no `app`. |
| Alternativa considerada | `slices().matching("dev.leilaalgarve.jogoacoes.(*)..").should().beFreeOfCycles()` do ArchUnit faz o mesmo em uma linha | descartada | A Leila pediu o grafo e o algoritmo explícitos. Fica registrado como alternativa se o código próprio der trabalho de manutenção. |
| Quebra do ciclo `competition ↔ email` | Ver "Decisões em aberto" na `spec.md` | em aberto | — |

## Estrutura de módulos/pacotes

- `app/src/test/java/dev/leilaalgarve/jogoacoes/common/ModuleDependencyGraph.java` (nova) e
  teste unitário dela; `ArchitectureTest.java` com o teste novo.
- `email-service/src/test/java/dev/leilaalgarve/jogoacoes/emailservice/common/` idem.
- `app/src/main/java/dev/leilaalgarve/jogoacoes/email/` e `competition/`: quebra do ciclo.
- `memory/constitution.md`, seção "Fronteira entre módulos e serviços".

## Riscos e trade-offs

- **O pacote `api` gerado** pode depender de classes de módulos (por exemplo, por
  `importMappings` do gerador OpenAPI). Se isso criar ciclo, a decisão é tratar `api` como
  módulo comum e corrigir o mapeamento, não excluí-lo do grafo.
- **`common`** depende de vários módulos (`ApiExceptionHandler` conhece as exceções de
  `competition`). Hoje nenhum módulo depende de `common` de volta; se passar a depender, o teste
  acusa, que é o comportamento desejado.

## Critério de aceite: exceções

Nenhuma classe de exceção nova. A falha do teste é um `AssertionError` do JUnit/ArchUnit.
