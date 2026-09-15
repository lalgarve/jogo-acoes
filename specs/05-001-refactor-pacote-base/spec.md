# Spec: Refatorar pacote base `io.deployo` → `dev.leilaalgarve`

**Status:** rascunho
**Issue:** —
**Iteração:** iteration-5

## Resumo

Renomear o pacote Java raiz de todo o código do projeto (`app/` e, presumivelmente,
`email-lambda/`) de `io.deployo` para `dev.leilaalgarve`. Mudança puramente mecânica, sem
alteração de comportamento.

## Motivação

O pacote `io.deployo` foi escolhido presumindo posse do domínio `deployo.io` (convenção de
nomear pacotes Java pelo domínio invertido). Esse domínio não está mais disponível — manter o
pacote assim não reflete mais a titularidade real. `dev.leilaalgarve` passa a ser o pacote
base.

## Cenários (comportamento esperado)

Não aplicável — mudança estrutural, sem comportamento novo observável. Critério de aceite: a
suíte `.feature` existente (`app/src/test/resources/features`) continua passando 100%, sem
nenhum cenário alterado, e nenhum teste unitário/integração muda de asserção — só de pacote.

## Requisitos funcionais

- Todo pacote que hoje começa com `io.deployo` passa a começar com `dev.leilaalgarve`,
  preservando a subestrutura existente (ex.: `io.deployo.jogoacoes.domain` →
  `dev.leilaalgarve.jogoacoes.domain`).
- Diretórios de código-fonte (`src/main/java`, `src/test/java`) refletem a nova estrutura de
  pastas correspondente ao novo pacote.
- Todas as referências textuais ao pacote antigo são atualizadas: imports, javadoc/comentários
  que citem o pacote por nome, configuração que referencia classes por nome totalmente
  qualificado (ex.: `apiPackage`/`modelPackage` do `openapi-generator-maven-plugin`, se
  configurado com `io.deployo`).
- `pom.xml` de cada módulo: `groupId`, se estiver alinhado ao pacote Java, também é revisado
  (ver "Decisões em aberto").

## Requisitos não-funcionais

Nenhum além de manter o build e a suíte de testes verdes — não é uma mudança de performance,
segurança ou comportamento.

## Fora de escopo

- Renomear artefatos que não sejam pacote Java (nome do banco de dados, filas SQS, nomes de
  containers no `docker-compose.yml`) — só o pacote Java está no escopo desta spec.
- Qualquer mudança de comportamento, endpoint ou schema de banco.
- Reorganização por módulo de domínio (`link`/`competition`/`login`/`log`/`email`) — isso é a
  spec seguinte (05-002); esta spec só troca o prefixo do pacote, mantendo a estrutura de
  camadas atual.

## Decisões em aberto

- `groupId` do `pom.xml` (a confirmar se hoje usa `io.deployo`) muda junto, ou fica como está
  por não ser publicado em nenhum repositório Maven? Se mudar, as coordenadas dos módulos do
  reator (`app`, `email-lambda`) precisam ser revisadas em conjunto.
- Confirmar o pacote Java atual do módulo `email-lambda` antes de aplicar — presumido também
  `io.deployo.*`, não verificado linha a linha nesta sessão.
- Ordem em relação à spec 05-002 (modularização): esta spec (renomear prefixo) deveria ser
  aplicada **antes** de 05-002 (mover para módulos), pra não editar o mesmo import duas vezes
  com duas mudanças em andamento simultaneamente — mas as duas são operações estruturalmente
  independentes, dá pra inverter se for mais conveniente na implementação.
