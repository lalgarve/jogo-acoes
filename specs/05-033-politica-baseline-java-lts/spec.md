# Spec: Política da baseline Java e dos upgrades de LTS

**Status:** implementada  
**Issue:** [#115](https://github.com/lalgarve/jogo-acoes/issues/115)  
**Iteração:** iteration-5

## Resumo

Registrar Java/JDK 21 como a baseline suportada pelo projeto e impedir que tarefas não
relacionadas proponham ou iniciem, por conta própria, uma migração para outro LTS. Uma
migração de Java continua permitida quando for solicitada explicitamente ou quando uma
incompatibilidade concreta exigir uma decisão.

## Motivação

O projeto já usa Java 21 de forma consistente nos módulos Maven, na CI e nas imagens Docker.
Mesmo assim, uma tarefa comum pode ser interrompida por uma recomendação recorrente para
atualizar a versão LTS. Isso mistura manutenção de versão com mudanças sem relação e pode
introduzir trabalho ou alterações de ambiente que não foram solicitados.

A regra precisa estar registrada tanto na orientação operacional lida pelo agente quanto na
constituição do projeto, para sobreviver à troca de sessão, ferramenta ou pessoa. A
configuração existente deve ser verificada e documentada, não substituída por uma versão LTS
mais nova.

## Cenários (comportamento esperado)

### T001 — Tarefa comum não sugere upgrade de LTS

**Dado** que Java/JDK 21 é a baseline registrada do projeto  
**Quando** uma tarefa de documentação, teste, correção ou funcionalidade que não pede mudança
de Java é iniciada  
**Então** a orientação do projeto não deve sugerir nem iniciar uma atualização para outro LTS  
**E** a tarefa deve continuar usando Java 21.

### T002 — Pedido explícito continua permitindo upgrade

**Dado** que a baseline do projeto é Java/JDK 21  
**Quando** a pessoa pede explicitamente uma atualização para outra versão  
**Então** o fluxo de upgrade pode ser iniciado normalmente  
**E** deve avaliar build, testes, CI, imagens Docker e compatibilidade das dependências.

### T003 — Incompatibilidade concreta é reportada

**Dado** que uma dependência, framework, ferramenta ou ambiente exige uma versão Java
diferente  
**Quando** essa incompatibilidade for encontrada durante uma tarefa  
**Então** ela deve ser reportada com a causa concreta e o componente afetado  
**E** nenhuma atualização deve ser iniciada sem decisão explícita sobre a mudança de
baseline.

## Requisitos funcionais

- Java/JDK 21 é a baseline suportada para `app`, `email-service`, `email-lambda` e
  `blackbox-proxy`.
- A orientação de trabalho do repositório deve dizer que upgrades de LTS exigem pedido
  explícito.
- A constituição deve registrar a baseline, o motivo da política e a exceção para
  incompatibilidade concreta.
- A documentação deve apontar os locais que precisam permanecer alinhados: POMs, CI e
  Dockerfiles.
- A política não deve impedir uma solicitação explícita de upgrade.
- Uma incompatibilidade real não deve ser escondida por um fallback silencioso; deve ser
  apresentada como decisão pendente.

## Requisitos não funcionais

- Nenhuma versão de Java deve ser atualizada por esta spec.
- Nenhuma mudança de comportamento da API, banco, fila ou regras de negócio.
- A regra deve ser clara para uso por outra sessão, ferramenta ou pessoa.
- A verificação deve confirmar os quatro módulos, a CI e as imagens Docker.

## Fora de escopo

- Migrar Java/JDK 21 para outro LTS.
- Atualizar Spring Boot, Quarkus, Maven, dependências ou imagens base.
- Escolher uma ferramenta de seleção de JDK local (`.java-version`, SDKMAN ou configuração
  específica do VS Code).
- Alterar a spec 05-027 ou qualquer outra feature de produto.
- Criar um mecanismo de atualização automática de dependências.

## Decisões em aberto

- Nenhuma de requisito.
