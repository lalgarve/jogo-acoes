# Plan: Política da baseline Java e dos upgrades de LTS

Traduz `spec.md` em decisões técnicas. Valida contra `memory/constitution.md`.

## Contexto técnico

- `app/pom.xml`, `email-service/pom.xml` e `blackbox-proxy/pom.xml` declaram
  `<java.version>21</java.version>`.
- `email-lambda/pom.xml` declara `<maven.compiler.release>21</maven.compiler.release>`.
- `.github/workflows/ci.yml` usa `actions/setup-java@v4` com Temurin `21`.
- `Dockerfile` usa `maven:3.9-eclipse-temurin-21` no build e `eclipse-temurin:21-jre` no
  runtime.
- `email-service/Dockerfile` usa as mesmas linhas Java 21.
- O POM da raiz é apenas agregador; não existe uma propriedade Java compartilhada que possa
  substituir as propriedades dos quatro módulos.
- `.vscode/settings.json` não fixa um JDK local; esta spec não introduz essa configuração.

## Decisões de arquitetura e processo

| Pergunta | Decisão | Status | Raciocínio |
|---|---|---|---|
| Qual é a baseline suportada? | Java/JDK 21 | resolvida | É a versão já declarada nos quatro módulos, na CI e nas imagens Docker. |
| Quando um upgrade pode ser iniciado? | Só após pedido explícito | resolvida | Evita que uma tarefa não relacionada mude a plataforma por iniciativa própria. |
| O que fazer diante de incompatibilidade real? | Reportar a causa, o componente e a versão exigida; aguardar decisão explícita | resolvida | Uma exigência concreta é diferente de uma recomendação genérica para acompanhar o LTS. |
| Onde registrar a política? | `CLAUDE.md` e `memory/constitution.md` | resolvida | O primeiro orienta o trabalho cotidiano; o segundo é a fonte de verdade do processo. |
| Como evitar divergência de versões? | Manter as declarações atuais e verificar POMs, CI e Dockerfiles | resolvida | A baseline já está tecnicamente aplicada; a mudança necessária é tornar a intenção explícita e verificável. |
| Fixar o JDK local do VS Code? | Fora de escopo | resolvida | A equipe pode usar diferentes mecanismos locais; a política do repositório não deve impor uma
  ferramenta sem decisão específica. |

## Alterações previstas

### `CLAUDE.md`

Adicionar uma seção curta, próxima às regras de processo, com:

- Java/JDK 21 como baseline;
- proibição de sugestão ou início de upgrade em tarefas não relacionadas;
- exceção para pedido explícito;
- tratamento de incompatibilidade concreta como informação a ser reportada, não como
  autorização automática.

### `memory/constitution.md`

Adicionar uma subseção na parte de stacks/build, ou uma seção própria próxima de
`"Nunca começar a implementar sem pedido explícito"`, contendo a política duradoura e seus
limites. A redação deve deixar claro que mudar a baseline é uma decisão de projeto, não uma
atualização automática.

### Configuração existente

Não alterar os valores de versão. Conferir e, se necessário, apenas ajustar comentários para
explicar que Java 21 é intencional nos seguintes arquivos:

- `app/pom.xml`;
- `email-service/pom.xml`;
- `email-lambda/pom.xml`;
- `blackbox-proxy/pom.xml`;
- `.github/workflows/ci.yml`;
- `Dockerfile`;
- `email-service/Dockerfile`.

Qualquer divergência encontrada durante a implementação deve ser corrigida para Java 21 ou
registrada como bloqueio; não deve ser resolvida escolhendo um LTS mais novo por padrão.

## Estratégia de verificação

1. Procurar declarações de versão Java fora de `target/` e confirmar que os quatro módulos
   declaram 21.
2. Confirmar que a CI seleciona Temurin 21.
3. Confirmar que os dois Dockerfiles usam imagens Java 21.
4. Revisar o texto final de `CLAUDE.md` e `memory/constitution.md` contra os cenários T001,
   T002 e T003.
5. Não executar uma migração de Java nem alterar dependências como parte desta spec.

## Riscos e trade-offs

- **Ferramentas fora do repositório:** uma IDE ou gerenciador local ainda pode selecionar
  outro JDK. Isso não muda a política do projeto e fica fora desta spec.
- **Dependência que eleva o requisito:** manter Java 21 pode deixar uma atualização bloqueada.
  Nesse caso, a incompatibilidade deve aparecer explicitamente e gerar uma decisão separada.
- **Documentação duplicada:** a regra aparece em dois arquivos por motivos diferentes. A
  constituição é a fonte de verdade; `CLAUDE.md` deve conter apenas o resumo operacional e
  apontar para ela.
