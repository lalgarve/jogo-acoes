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
  `email.exception.RecipientDomainRejectedException`. `loginsession.LoginController` monta um
  `EmailRequest` com `EmailTemplate.LOGIN_LINK`.
- Hoje o `email` conhece os clientes em três lugares: o enum `EmailTemplate` (`INVITE`,
  `REGISTRATION_LINK`, `LOGIN_LINK`), `EmailServiceEmailSender.templateNameFor` (que escolhe entre
  os cinco templates usando `RequestType`) e `EmailTemplateSynchronizer.TEMPLATE_NAMES` (a lista
  dos cinco templates em `src/main/resources/email-templates/`, com o `layout.html` comum).

## Decisões de arquitetura

| Pergunta | Decisão | Status | Raciocínio |
|---|---|---|---|
| De onde vêm as referências | `JavaClasses` importadas pelo ArchUnit (só produção, `ImportOption.DoNotIncludeTests`); para cada classe, `getDirectDependenciesFromSelf()` | adotada | O bytecode pega tudo o que a spec chama de referência, inclusive nome qualificado sem `import`. |
| Como montar o grafo (passos 1 e 2) | Nó = primeiro segmento de pacote depois do pacote base. Aresta `A → B` quando uma dependência sai de `A` e chega em `B`, com `A ≠ B`; cada aresta guarda uma referência de exemplo (`origem → destino`). Dependências para fora do pacote base (JDK, Spring) são ignoradas | adotada | Módulo novo entra sozinho. |
| Algoritmo (passo 3) | Tarjan para componentes fortemente conexos: todo componente com mais de um nó contém ciclo. Dentro de cada componente, uma busca em profundidade extrai um ciclo concreto para a mensagem | adotada (Leila: algoritmo próprio) | Tarjan é linear no tamanho do grafo e encontra todos os ciclos de uma vez (cenário 4), não só o primeiro. O componente mostra todos os módulos emaranhados; o ciclo concreto mostra o caminho a cortar. |
| Onde fica o código | Uma classe `ModuleDependencyGraph` (montagem + Tarjan) por serviço, no pacote de teste do `ArchitectureTest`, e um `@Test` em cada `ArchitectureTest`. O `app` e o `email-service` não compartilham código Java (spec 05-034, nem em teste), então a classe é duplicada | em aberto | Alternativa: um módulo Maven de teste compartilhado só com essa classe. Recomendação: duplicar, são poucas dezenas de linhas e não cria dependência entre serviços. |
| Teste do próprio algoritmo | Teste unitário de `ModuleDependencyGraph` com grafos montados à mão: sem ciclo, ciclo de 2, ciclo de 3, dois ciclos separados, auto-referência ignorada | adotada | Os cenários 2 a 4 da spec não podem ficar no código de produção; testar o algoritmo isolado cobre isso sem plantar ciclos no `app`. |
| Alternativa considerada | `slices().matching("dev.leilaalgarve.jogoacoes.(*)..").should().beFreeOfCycles()` do ArchUnit faz o mesmo em uma linha | descartada | A Leila pediu o grafo e o algoritmo explícitos. Fica registrado como alternativa se o código próprio der trabalho de manutenção. |
| Interface para montar o e-mail | `email.OutgoingEmail` (interface): `recipient()`, `userId()` (opcional), `templateName()`, `templateData()`. `EmailSender.send(OutgoingEmail)`. Cada cliente tem os próprios records que a implementam (por exemplo `competition.InviteEmail`, `loginsession.LoginLinkEmail`) e escolhe ali o template, inclusive o `login-link-invite`/`login-link-request` pelo `RequestType`, que fica só na `competition` | adotada (Leila, 2026-10-25) | O `email` só envia e registra; não precisa saber nada de competição. `EmailRequest` e o enum `EmailTemplate` saem. Nomes das classes são sugestão, definidos na implementação. |
| Interface para definir os templates | `email.EmailTemplateSource` (interface, bean Spring): devolve os templates do cliente (nome, assunto, conteúdo). `competition` e `loginsession` têm uma implementação cada, lendo os arquivos de `email-templates/competition/` e `email-templates/loginsession/`. `EmailTemplateSynchronizer` recebe todas as implementações (`List<EmailTemplateSource>`), encaixa cada conteúdo no `layout.html` (que fica no `email`) e registra no `email-service` | adotada (Leila, 2026-10-25) | Cliente novo = um bean novo, sem mudar o `email`. Dois clientes com o mesmo nome de template fazem a aplicação falhar na subida, com mensagem que diz os dois. |
| `sent_email.template` | Passa a guardar o nome do template (`varchar`), não o enum. Migration nova em `db/migration-app` altera a coluna; sem conversão de linhas antigas | adotada | Pré-produção (constitution). O nome do template é mais preciso que o enum antigo (que juntava três templates em `LOGIN_LINK`). |

## Estrutura de módulos/pacotes

- `app/src/test/java/dev/leilaalgarve/jogoacoes/common/ModuleDependencyGraph.java` (nova) e
  teste unitário dela; `ArchitectureTest.java` com o teste novo.
- `email-service/src/test/java/dev/leilaalgarve/jogoacoes/emailservice/common/` idem.
- `app/src/main/java/dev/leilaalgarve/jogoacoes/email/`: `OutgoingEmail`, `EmailTemplateSource`,
  `EmailSender`, `EmailServiceEmailSender`, `StubEmailSender`, `SentEmail`, `SentEmailRecorder`,
  `client/EmailTemplateSynchronizer`; saem `EmailRequest` e `EmailTemplate`.
- `competition/` e `loginsession/`: os e-mails de cada um e a implementação de
  `EmailTemplateSource`.
- `app/src/main/resources/email-templates/`: `layout.html` fica; os cinco templates vão para
  `competition/` (`invite`, `registration-link`, `login-link-invite`, `login-link-request`) e
  `loginsession/` (`login-link`).
- `app/src/main/resources/db/migration-app/`: migration da coluna `sent_email.template`.
- `memory/constitution.md`, seção "Fronteira entre módulos e serviços".

## Riscos e trade-offs

- **O pacote `api` gerado** pode depender de classes de módulos (por exemplo, por
  `importMappings` do gerador OpenAPI). Se isso criar ciclo, a decisão é tratar `api` como
  módulo comum e corrigir o mapeamento, não excluí-lo do grafo.
- **`common`** depende de vários módulos (`ApiExceptionHandler` conhece as exceções de
  `competition`). Hoje nenhum módulo depende de `common` de volta; se passar a depender, o teste
  acusa, que é o comportamento desejado.

- **O `login-link` é usado pelos dois clientes?** Hoje a `competition` também manda
  `LOGIN_LINK` sem competição? Pelo código, não: sem competição só o `LoginController` manda. Se
  a implementação achar outro uso, o template fica com quem o usa e o outro cliente define o
  próprio, em vez de compartilhar.
- **Testes que montam `EmailRequest`** (unitários do `EmailServiceEmailSender`, da `competition`
  e do `loginsession`) mudam junto. A suíte de caixa-preta não muda: ela olha o e-mail entregue.

## Critério de aceite: exceções

Uma condição de erro nova: dois `EmailTemplateSource` com o mesmo nome de template. Se virar
classe de exceção própria, ganha um teste que a lança (constitution, "Critério de aceite:
exceções e caminhos de erro"); se for `IllegalStateException`, o teste exercita o caminho do
mesmo jeito. A falha do teste de ciclos é um `AssertionError` do JUnit.
