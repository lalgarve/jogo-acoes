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
- `pom.xml` de cada módulo do reator (`app`, `email-lambda`): `groupId` muda junto com o
  pacote Java, de `io.deployo` para `dev.leilaalgarve` — decisão resolvida nesta sessão, ver
  "Decisões em aberto".
- `email-lambda` também é renomeado de `io.deployo.*` para `dev.leilaalgarve.*`, no mesmo
  escopo desta spec, não como trabalho separado — ver "Decisões em aberto".

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

- ~~`groupId` do `pom.xml` muda junto, ou fica como está?~~ **Resolvido (sessão 2026-09-16):
  muda junto.** O `groupId` acompanha o pacote Java (`io.deployo` → `dev.leilaalgarve`), mesmo
  não sendo publicado em nenhum repositório Maven — mantém coerência entre pacote e
  coordenada. As coordenadas dos módulos do reator (`app`, `email-lambda`) são revisadas em
  conjunto, na mesma mudança.
- ~~Confirmar o pacote Java atual do módulo `email-lambda` antes de aplicar.~~ **Resolvido
  (sessão 2026-09-16): muda pacote.** `email-lambda` também é renomeado de `io.deployo.*` para
  `dev.leilaalgarve.*`, junto com `app/` — presumido `io.deployo.*` como o resto do projeto,
  não verificado linha a linha nesta sessão; confirmar o pacote real ao implementar, mas o
  destino já está decidido, não é mais uma decisão em aberto.
- Ordem em relação à spec 05-002 (modularização): esta spec (renomear prefixo) deveria ser
  aplicada **antes** de 05-002 (mover para módulos), pra não editar o mesmo import duas vezes
  com duas mudanças em andamento simultaneamente — mas as duas são operações estruturalmente
  independentes, dá pra inverter se for mais conveniente na implementação.
