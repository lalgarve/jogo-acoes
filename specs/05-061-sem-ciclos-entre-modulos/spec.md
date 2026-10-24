# Spec: Nenhum ciclo de dependência entre módulos

**Status:** rascunho (decisões em aberto abaixo)
**Issue:** —
**Iteração:** iteration-5

## Resumo

Um teste de arquitetura monta, em cada serviço, um grafo direcionado em que cada nó é um módulo
e cada aresta `A → B` diz que alguma classe de `A` referencia alguma classe de `B`. No fim, um
algoritmo de grafos procura ciclos. Se houver algum, o build falha e a mensagem mostra cada ciclo
com os módulos envolvidos e uma referência de exemplo para cada aresta.

## Motivação

- **Pedido da Leila (2026-10-24):** verificar se há referências circulares entre módulos, nos
  três passos: montar o grafo, criar uma aresta para cada referência de um módulo a outro e
  aplicar um algoritmo que encontre ciclos.
- Os `ArchitectureTest` de hoje proíbem dependências específicas (por exemplo, `user`,
  `loginsecurity` e `link` não dependem entre si), mas nenhum olha o grafo inteiro. Um ciclo
  novo entre dois módulos que não estão nessas regras passa sem ser notado.
- **Já existe um ciclo no `app`:** `competition → email` (os serviços de competição montam
  `EmailRequest` e chamam `EmailSender`) e `email → competition` (`EmailRequest` e
  `EmailServiceEmailSender` usam o enum `competition.RequestType`). Conferido em 2026-10-24 pelos
  `import` de `origin/development`. O teste nasce vermelho até esse ciclo ser quebrado.

## O que é um módulo

O pacote de primeiro nível abaixo do pacote base de cada serviço, com tudo o que estiver abaixo
dele:

| Serviço | Pacote base | Módulos hoje |
|---|---|---|
| `app` | `dev.leilaalgarve.jogoacoes` | `api` (gerado do OpenAPI), `bootstrap`, `captcha`, `common`, `competition`, `email`, `link`, `log`, `loginsecurity`, `loginsession`, `user` |
| `email-service` | `dev.leilaalgarve.jogoacoes.emailservice` | `api` (gerado), `auth`, `common`, `disposable`, `domain`, `send`, `template` |

Um módulo novo entra no grafo sozinho, sem mudar o teste.

## Cenários (comportamento esperado)

Não há `.feature` novo: a mudança é na forma de testar, não no comportamento dos serviços.
Verificação:

1. **Sem ciclo:** com o ciclo `competition ↔ email` quebrado, o teste passa no `app` e no
   `email-service`.
2. **Ciclo de dois módulos:** uma classe de `user` que referencie `log` (já existe `log → user`)
   faz o teste falhar, e a mensagem mostra `user → log → user`.
3. **Ciclo longo:** um ciclo de três ou mais módulos (por exemplo `email → loginsession`, com
   `loginsession → email` e `competition → email` já existentes) também é encontrado e
   mostrado inteiro.
4. **Vários ciclos:** se houver mais de um ciclo, a mensagem mostra todos, não só o primeiro.
5. **Referência dentro do mesmo módulo:** classes de subpacotes do mesmo módulo (por exemplo
   `competition.exception` e `competition`) não geram aresta.

## Requisitos funcionais

- **Referência** é qualquer dependência que aparece no bytecode compilado: `import`, nome
  totalmente qualificado no código, herança, campo, parâmetro, retorno, anotação, chamada de
  método ou exceção lançada. Não só `import`.
- Só classes de produção (`src/main`) entram no grafo; classes de teste ficam de fora.
- Um teste por serviço com mais de um módulo: `app` e `email-service`. A `email-lambda` e o
  `config-server` têm um pacote só e não ganham o teste.
- A mensagem de falha lista cada ciclo como sequência de módulos (`A → B → C → A`) e, para cada
  aresta, ao menos uma referência concreta (`classe de origem → classe de destino`), para quem
  for corrigir saber onde mexer.
- O ciclo `competition ↔ email` que já existe é quebrado nesta spec (ver "Decisões em aberto").
- **Constitution:** a seção "Fronteira entre módulos e serviços" passa a dizer que não há ciclo
  de dependência entre módulos de um serviço, garantido por teste de arquitetura.

## Requisitos não-funcionais

- O teste roda na suíte normal (`mvn test`) junto dos outros `ArchitectureTest`, sem
  infraestrutura de pé.

## Fora de escopo

- Ciclos entre módulos Maven: o Maven já recusa um reator com ciclo.
- Ciclos entre serviços (`app`, `email-service`): eles só se falam por contrato, e os
  `ArchitectureTest` já proíbem dependência Java entre eles (spec 05-034).
- Regra de camadas dentro de cada módulo (controller → service → repository): fica para uma
  spec própria.

## Decisões resolvidas

- **Algoritmo próprio sobre o grafo** (Leila, 2026-10-24): o teste monta o grafo e aplica o
  algoritmo, nos três passos que ela descreveu. Detalhe técnico em `plan.md`.

## Decisões em aberto

1. **Como quebrar o ciclo `competition ↔ email`.** Recomendação: o módulo `email` ganha o próprio
   enum para a origem do convite (por exemplo `EmailOrigin { INVITE, REQUEST }`) e a
   `competition` converte `RequestType` nele ao montar o `EmailRequest`. Assim `email` fica um
   módulo de baixo nível, usado por `competition` e `loginsession`, sem saber de competição.
   Alternativa: mover `RequestType` para `email`; descartada na recomendação porque
   `RequestType` é um conceito de participação, persistido em `Participation`.
