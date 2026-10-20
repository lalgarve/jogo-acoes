# Spec: Os testes não sobem containers

**Status:** rascunho
**Issue:** [#193](https://github.com/lalgarve/jogo-acoes/issues/193)
**Iteração:** iteration-5

## Resumo

Nenhum teste automatizado sobe container. Os containers (Postgres, LocalStack, DNS de teste,
`config-server`, os serviços) são levantados antes, fora do Maven, e o teste usa o que já está
rodando. O último lugar que ainda sobe container é a suíte da `email-lambda`, pelo Quarkus Dev
Services (Testcontainers); ela passa a usar o LocalStack já de pé. Um teste continua podendo
iniciar o próprio serviço dentro da JVM (`@SpringBootTest`, `@QuarkusTest`): isso não é
container.

## Motivação

- **Regra da Leila (2026-10-20): os testes não sobem containers.** A spec
  [05-028](../05-028-testes-exigem-docker-real/spec.md) já tirou o Testcontainers do `app` e do
  `email-service`, porque deixar o Maven subir containers dava problema. A `email-lambda` ficou
  de fora daquela spec.
- **A `email-lambda` ainda sobe um LocalStack próprio:** os testes usam `@QuarkusTest` sem
  endereço de SES configurado, e o Quarkus Dev Services sobe um LocalStack por Testcontainers
  durante o `mvn test` (conferido em 2026-10-19: o log mostra `Testcontainers version: 2.0.5` e o
  container sendo criado). No CI, a suíte da `email-lambda` roda depois de os containers do
  Compose serem derrubados, justamente porque traz o próprio LocalStack.
- **Nenhum teste deve depender de URL fora do nosso controle.** A migration
  `V6__create_disposable_domain_tables.sql` (spec 05-048) cadastra, em todos os ambientes, a lista
  `disposable-email-domains` do GitHub como repositório de listas de e-mail temporário. A suíte
  Cucumber do `email-service` a apaga antes dos cenários; um teste que importasse todos os
  repositórios sem apagá-la baixaria a lista da internet.

## Cenários (comportamento esperado)

Não há `.feature` novo: a mudança é na forma de testar, não no comportamento dos serviços.
Verificação:

1. **LocalStack de pé:** com o ambiente levantado antes, `mvn -pl email-lambda -am test` passa
   sem criar nenhum container (nenhuma linha `org.testcontainers` no log, `docker ps` sem
   container novo).
2. **LocalStack fora do ar:** a suíte da `email-lambda` falha com erro de conexão, sem subir um
   LocalStack no lugar e sem pular teste (constitution, "Testes exigem a infraestrutura de pé").
3. **Regra:** um teste que use Testcontainers, em qualquer módulo, faz o build falhar.
4. **Ambiente de teste sem o repositório do GitHub:** no `docker`, `GET /disposable-domain-sources`
   num banco novo responde a lista vazia; em `staging`/`production`, o repositório continua
   cadastrado.

## Requisitos funcionais

- **`email-lambda`:** os testes usam o SES e o SQS do LocalStack levantado antes
  (`http://localhost:4566`), configurados nas propriedades de teste; o Dev Services fica
  desligado. O `@QuarkusTest` continua (inicia a função dentro da JVM, não um container).
- **Regra contra Testcontainers** em todos os módulos: nenhuma classe de teste usa
  `org.testcontainers`, e o Dev Services do Quarkus fica desligado nos testes da `email-lambda`.
- **CI:** a suíte da `email-lambda` roda com o LocalStack do Compose de pé, antes de os
  containers serem derrubados.
- **Repositório inicial das listas** (spec 05-048): o cadastro da lista do GitHub passa a existir
  só em `staging` e `production`. No `docker` e nos testes, nem é criado.
- **Constitution:** "Testes exigem a infraestrutura de pé" passa a dizer que nenhum teste sobe
  container, em nenhum módulo, nem por Testcontainers nem por Dev Services.

## Requisitos não-funcionais

- Os testes da `email-lambda` não podem interferir nos outros usos do LocalStack compartilhado:
  filas e remetentes próprios dos testes, como a suíte do `email-service` já faz.

## Fora de escopo

- Os testes que iniciam o próprio serviço dentro da JVM (`@SpringBootTest`, `@QuarkusTest`,
  `SpringApplicationBuilder`): continuam como estão.
- A ordem das suítes no CI entre `app` e `email-service` (conflito de duas instâncias do
  `email-service` com a spec 05-046): tratada na T020 da 05-046.

## Decisões resolvidas

- **Escopo: só containers** (Leila, 2026-10-20). Uma primeira versão desta spec proibia também
  iniciar o próprio serviço no teste e levava para Python os testes de configuração especial; foi
  um mal-entendido, descartado. Iniciar o serviço dentro da JVM continua permitido.
- **Nenhum teste depende de URL fora do nosso controle** (Leila, 2026-10-20). As listas de
  e-mail temporário dos testes são servidas pelo S3 do LocalStack, publicadas pelo próprio teste.
- **No ambiente de teste, o repositório do GitHub nem é criado** (Leila, 2026-10-20). Efeitos:
  - a V6 muda de conteúdo: um banco já criado precisa ser recriado (`docker compose down -v`); o
    sistema está em pré-produção (constitution), não há dado a preservar;
  - o caderno de testes do Serviço de E-mail, parte do documento já entregue, conta com esse
    repositório como o de número `1`; rodado de novo no `docker`, o resultado muda. O caderno
    ganha uma nota, como a da spec 05-049, sem reescrever os roteiros;
  - a spec 05-048 ganha uma nota de que o repositório inicial passou a ser só de
    `staging`/`production`.

## Decisões em aberto

Nenhuma.
