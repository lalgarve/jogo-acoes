# Jogo de Ações

Um jogo de simulação de investimentos em bolsa: administradores criam competições
(públicas ou privadas), jogadores entram via link de login enviado por e-mail e negociam
ações com dados de mercado reais, competindo pela melhor evolução de portfólio.

## O que o sistema faz

Um administrador cria uma competição — pública (qualquer jogador pode pedir entrada) ou
privada (só quem é convidado por e-mail). Em ambos os casos o jogador entra via um link de
login enviado por e-mail, sem senha: pede o link, recebe, clica, está dentro. Antes de aceitar
qualquer e-mail (convite, pedido de entrada ou pedido de login), o sistema verifica se o
domínio tem registro MX válido e não está numa lista de domínios descartáveis/temporários —
reduz o volume de e-mails que nunca chegariam a lugar nenhum. Administradores também têm
visão e controle sobre os jogadores de cada competição: reenviar um convite, remover um
jogador.

- **Sistema principal** (`app/`, Spring Boot): API REST (gerada a partir de
  [`docs/openapi.yaml`](docs/openapi.yaml)), persistência JPA/PostgreSQL, autenticação por
  link de login, ALTCHA como captcha (prova de trabalho auto-hospedada, sem serviço
  terceirizado), log de auditoria.
- **`email-lambda/`**: AWS Lambda (Quarkus, com suporte a imagem nativa GraalVM) que consome
  uma fila Amazon SQS e envia o e-mail via Amazon SES — desacoplada do sistema principal, que
  só publica na fila e nunca fala com o SES diretamente.
- **Especificação de domínio (BDD)**: cada fluxo (login, criação de competição, gerência de
  jogadores, pedido de entrada) tem cenários Gherkin cobrindo caminho feliz e casos de erro,
  executados a cada mudança.
- **Modelo de dados** em [`docs/diagrams/der.md`](docs/diagrams/der.md), com diagrama de
  classes e de sequência complementares em
  [`docs/diagrams/classes.md`](docs/diagrams/classes.md) e
  [`docs/diagrams/sequencia.md`](docs/diagrams/sequencia.md).
- **Integração contínua** (`.github/workflows/ci.yml`): suíte de testes com piso de cobertura
  de linha (JaCoCo, 80%) rodando a cada *pull request* contra infraestrutura real (Postgres,
  fila SQS) via Docker, não contra simulação em memória.

## Arquitetura planejada

- Negociação de ações com cotações de mercado reais via [Brapi](https://brapi.dev).
- Contabilidade da competição modelada como partida dobrada (lançamentos insert-only,
  débito/crédito em contas separadas), no estilo de sistemas financeiros reais.
- Acessibilidade de gráficos com dois modos: descrição textual por pontos relevantes da
  série e sonorização (grave/agudo por valor, estéreo por tempo), com a parte mais pesada
  implementada em Rust compilado para WebAssembly.

Detalhes de cada etapa estão em [`docs/roadmap.md`](docs/roadmap.md).

## Módulos

Reator Maven multi-módulo (`pom.xml` na raiz é só um agregador, não é *parent* de nenhum
dos dois — cada módulo mantém seu próprio *parent*/BOM):

| Módulo | Framework | O quê |
|---|---|---|
| `app/` | Spring Boot | O sistema principal (API, persistência, regras de negócio) |
| `email-lambda/` | Quarkus | AWS Lambda que consome a fila de e-mail e envia via SES |

`mvn verify` na raiz builda os dois. Pra rodar só um: `mvn -pl app -am verify` ou
`mvn -pl email-lambda -am verify`.

## Ambientes

O perfil ativo do Spring é escolhido por `SPRING_PROFILES_ACTIVE` (ou `sandbox`, se
nenhum for definido). Cada um tem seu arquivo `application-<nome>.yml` em
`app/src/main/resources`:

| Perfil | Banco | Quando usar |
|---|---|---|
| `sandbox` (padrão) | H2 embarcado, migrations em `db/migration-h2` | Rodar/testar sem precisar de Docker nem Postgres instalado |
| `docker` | PostgreSQL real em containers | Localmente via `docker-compose up`, ou CI |
| `docker,blackbox` | PostgreSQL real em containers | Testes de caixa-preta (Swagger UI, Selenium futuro, suíte Python) — ver "Ambiente de testes blackbox" abaixo |
| `staging` | PostgreSQL real, gerido por outra equipe | Pré-produção |
| `production` | PostgreSQL real, gerido por outra equipe | Produção |

`sandbox` e `docker` compartilham o mesmo modelo de dados, mas em pastas de migration
separadas (`db/migration-h2` e `db/migration`) — a versão para H2 não tem os comandos
`GRANT`/`REVOKE` de papéis de banco que só existem no Postgres real.

Para rodar localmente com Postgres real:

```
docker-compose up
```

## Ambiente de testes blackbox

`blackbox` (spec 05-014) é um perfil empilhado sobre `docker` — nunca usado sozinho — pensado
para exercitar a API só por fora (Swagger UI, Selenium no futuro, ou a
[suíte de testes Python](blackbox-tests/README.md), spec 05-015), sem as duas coisas que
normalmente exigem um cliente completo:

- **Captcha sempre aceito** — não existe ainda um frontend que resolva o desafio ALTCHA de
  verdade, então qualquer `captchaToken` (incluindo vazio) é aceito. Só neste perfil: qualquer
  outro (`sandbox`, `docker` sozinho, `staging`, `production`) continua exigindo um captcha
  resolvido de verdade.
- **Administrador já semeado** — como só um administrador pode criar competições e não existe
  via de API para criar um, a aplicação garante, de forma idempotente ao subir, um administrador
  com e-mail `success+admin@simulator.amazonses.com` (simulador de caixa de entrada do Amazon
  SES, spec 05-016). Não há senha em lugar nenhum do sistema — login é sempre
  por link mágico.
- **Leitura do link de um e-mail por HTTP** — `POST /login-requests` nunca devolve o link no
  corpo (deliberado, pra não revelar se o e-mail existe), e por padrão o link só é visível de
  dentro do processo Java. `GET /blackbox/last-email?email={endereço}` devolve o link mais
  recente enviado a um endereço (`404` se nada foi enviado ainda) — só existe neste perfil, e
  não faz parte do contrato (`docs/openapi.yaml`): é andaime de teste, não API de produto. Não é
  uma caixa postal completa (só o último e-mail por endereço, sem histórico) — suficiente para
  destravar um fluxo que depende de clicar num link.

Para subir:

```
docker compose -f docker-compose.yml -f docker-compose.blackbox.yml up
```

### Com ou sem cobertura JaCoCo da aplicação exercitada externamente

A suíte JUnit/Cucumber (`mvn test`/`mvn verify`) já gera seu próprio relatório JaCoCo em
`target/site/jacoco/` — isso não muda. O ambiente `blackbox` mede um tipo diferente de
cobertura: a da **aplicação rodando de verdade**, enquanto é exercitada de fora (Swagger UI ou a
suíte Python), via o agente de execução do JaCoCo anexado ao processo `java -jar app.jar` —
mecanismo diferente do `jacoco-maven-plugin` (que só instrumenta JVMs que o próprio Maven
lança), sempre disponível na imagem mas inerte fora deste perfil.

- **Com JaCoCo** (o comando acima já ativa o agente, `docker-compose.blackbox.yml` inclui
  `JAVA_TOOL_OPTIONS` com `-javaagent`): depois de exercitar a aplicação, gere o relatório:
  ```
  ./scripts/blackbox-coverage.sh
  ```
  Abre `target/site/jacoco-blackbox/index.html` — diretório próprio, nunca sobrescreve nem se
  mistura com `target/site/jacoco/` (suíte Java).
- **Sem JaCoCo**: `docker compose up` normal (sem `-f docker-compose.blackbox.yml`) — a imagem
  tem o agente embutido, mas ele nunca é ativado fora da sobreposição `blackbox`.

### Gerando dados de teste com uma data no passado

Às vezes é útil gerar dados de teste como se a aplicação estivesse rodando numa data passada
(ex.: uma competição que já começou/terminou há semanas, e-mails "enviados" há um tempo). Hoje
não existe um `Clock` injetável no código — datas vêm direto de `LocalDate.now()`/
`LocalDateTime.now()` — então a forma mais simples de conseguir isso é mudar o relógio que a
JVM enxerga, sem tocar em código. Como nenhuma migration usa `NOW()`/`CURRENT_TIMESTAMP` do
lado do Postgres (toda timestamp é calculada em Java antes de persistir), basta mexer no
relógio do container `app` — o `db` não importa.

Isso é feito manualmente, por fora, quando for gerar os dados — nenhum dos dois caminhos
abaixo está automatizado neste repositório:

- **Mudar o relógio do host** (mais simples, só em host/VM descartável dedicado a isso):
  containers Linux normalmente compartilham o relógio do host (sem *time namespace* próprio),
  então `sudo date -s "-30 days"` antes do `docker compose up` já muda o que a JVM enxerga em
  `Instant.now()`/`System.currentTimeMillis()`. Lembrar de voltar o relógio do host depois —
  isso afeta tudo que roda ali, TLS incluído.
- **Escopado só ao container `app`**, sem mexer no host: `libfaketime` (`LD_PRELOAD`) — por
  exemplo, sobrescrevendo o `entrypoint` do serviço `app` na hora de subir (sem alterar nenhum
  arquivo do repositório):
  ```
  docker compose -f docker-compose.yml -f docker-compose.blackbox.yml run --rm --service-ports \
    -e FAKETIME_OFFSET="-30 days" \
    --entrypoint "sh -c 'apt-get update -qq && apt-get install -y -qq faketime && faketime \"\$FAKETIME_OFFSET\" java -jar app.jar'" \
    app
  ```
  `--service-ports` é obrigatório aqui — sem ele, `docker compose run` não publica as portas
  do serviço (`8080`, e `6300` se a sobreposição `blackbox` estiver ativa), e nada rodando no
  host consegue alcançar `localhost:8080`.

Se algum dia isso não for mais suficiente (ex.: precisar que o "agora" avance de forma
controlada durante o teste, não só fique fixo no passado), a alternativa considerada foi
injetar um `Clock` de verdade (bean configurável por propriedade, com uma implementação real
para produção e uma deslocada em dias para teste) — mudança de código real, ainda não feita
porque a opção acima resolve o caso de uso atual (gerar dados) sem tocar em nada versionado.

## Licença

Este projeto está licenciado sob a GNU General Public License v3.0 (ou, a seu critério,
qualquer versão posterior) — veja o arquivo [LICENSE](LICENSE) para o texto completo.

```
Jogo de Ações
Copyright (C) 2026 Leila Algarve

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <https://www.gnu.org/licenses/>.
```
