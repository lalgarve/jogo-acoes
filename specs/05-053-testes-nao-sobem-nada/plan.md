# Plan: Os testes não sobem nada

Traduz `spec.md` em decisões técnicas. Valida contra `memory/constitution.md`.

## Contexto técnico

- **Como os testes sobem coisas hoje** (`spec.md`, tabela da motivação):
  - `app` e `email-service`: suítes Cucumber com `cucumber-spring` e
    `@CucumberContextConfiguration @SpringBootTest(webEnvironment = RANDOM_PORT)`; os passos usam
    RestAssured contra a porta aleatória e `@Autowired` em repositórios e serviços da aplicação
    para preparar e conferir o estado. Testes JUnit com `@SpringBootTest`, `@DataJpaTest` e
    `SpringApplicationBuilder` (estes últimos para subir o serviço com outra configuração).
  - Cada suíte tem um `src/test/resources/application.yml` que substitui o do serviço.
  - `email-lambda`: `@QuarkusTest`; o Quarkus Dev Services sobe um LocalStack por Testcontainers
    porque os testes não configuram endpoint.
  - `config-server`: `@SpringBootTest` e `SpringApplicationBuilder`.
- **O ambiente já levantado antes** existe: `docker-compose.yml` (+ overlay `blackbox`) no
  WSL/CI e `scripts/cloud-up.sh` na sessão de nuvem (05-052). O LocalStack implanta a
  `email-lambda` pelos scripts de `docker/localstack/init/`.
- **Cobertura com o serviço rodando** já existe para o `app`: agente JaCoCo em modo
  `tcpserver` (porta 6300) no overlay `blackbox` e `scripts/blackbox-coverage.sh`, que faz o
  *dump* e gera o relatório. O piso de 80% hoje é verificado pelo `jacoco-maven-plugin` sobre a
  execução dentro do Maven.
- **CI** (`.github/workflows/ci.yml`): sobe infraestrutura, `config-server` e o container do
  `email-service`; roda `app`, `email-service`, `config-server`; derruba; roda a `email-lambda`
  com Dev Services.

## Decisões de arquitetura

| Pergunta | Decisão | Status | Raciocínio |
|---|---|---|---|
| Como o teste chama o serviço | RestAssured contra o endereço fixo do serviço rodando, lido de uma propriedade de sistema ou variável de ambiente (`APP_BASE_URL`, `EMAIL_SERVICE_BASE_URL`, `CONFIG_SERVER_URL`), com o padrão do ambiente local | proposta | Mesmo cliente HTTP que os passos já usam; muda só o destino. |
| Como o teste prepara e confere o estado | JDBC direto nos bancos (`localhost:5432` e `5433`, credenciais de teste versionadas), clientes da AWS SDK para SQS/SES/S3 no LocalStack, `GET /_aws/ses` para os e-mails enviados. Uma pequena camada de apoio por módulo (`testsupport/`), sem Spring | proposta | É o que o teste precisa enxergar sem passar pela aplicação. Os repositórios da aplicação não podem mais ser injetados. |
| Injeção nos passos do Cucumber | `cucumber-picocontainer` no lugar de `cucumber-spring` | proposta | Compartilha o estado do cenário entre classes de passos sem contexto Spring. |
| `email-lambda` | Sem `@QuarkusTest` e sem Dev Services (`quarkus.devservices.enabled=false` deixa de ser necessário, porque nada sobe). O teste publica na fila de envio do LocalStack e confere o e-mail em `/_aws/ses` ou a mensagem na DLQ. A classificação de erros do SES fica coberta também por teste unitário do classificador | proposta | É a função implantada de verdade, pelo mesmo caminho da produção (fila → Lambda → SES). |
| Testes que precisam de outra configuração | Vão para Python. Cada teste levanta os containers do cenário com a configuração dele (Compose com um arquivo ou variáveis próprias do cenário, num nome de projeto separado, para não tocar no ambiente das suítes Java), chama o serviço por HTTP, confere banco e LocalStack, e derruba o que levantou, também quando falha. A lista fechada sai da T003 | resolvida (Leila, 2026-10-20) | O teste controla o ambiente de que precisa; as suítes Java continuam sem subir nada. |
| Configuração do ambiente de teste | Ver `spec.md`, decisão em aberto 2. Proposta: o overlay `blackbox` ganha o que os testes precisam (ex.: esperas curtas no `email-service`) e o `cloud-up.sh` aplica o mesmo | em aberto | — |
| Cobertura | Ver `spec.md`, decisão em aberto 3. Proposta: agente JaCoCo `tcpserver` em `app` (6300, já existe), `email-service` (6301) e `config-server` (6302); depois das suítes, um script faz o *dump* de cada um e roda `jacoco:report` e `jacoco:check` sobre o arquivo do *dump*, com o mesmo piso de hoje. A `email-lambda` roda dentro do container do LocalStack: cobertura só informativa | em aberto | Mantém o gate do CI sem medir dentro do Maven. |
| Regra de arquitetura | ArchUnit em cada módulo: nenhuma classe de teste depende de `org.springframework.boot.test.context`, `org.springframework.boot.test.autoconfigure`, `org.springframework.boot.builder.SpringApplicationBuilder`, `io.quarkus.test`, `org.testcontainers` | proposta | Mesma forma das regras de arquitetura que o projeto já tem; impede a volta do formato antigo. |
| `application.yml` de teste | Sai de `src/test/resources` em `app` e `email-service`: o serviço roda com a configuração dele, vinda do `config-server` | proposta | É a configuração que roda de verdade. |
| Ordem da migração | `config-server` → `email-lambda` → `email-service` → `app`, um módulo por PR, cada uma com a regra de arquitetura do módulo | proposta | Do menor para o maior; o CI fica verde a cada passo. |
| CI | Um passo levanta tudo (infraestrutura, `config-server`, `email-service`, `app`, com o overlay `blackbox` e os agentes JaCoCo), depois as suítes, a cobertura e a derrubada | proposta | O ambiente sobe uma vez. Resolve, de passagem, o conflito de duas instâncias do `email-service` da 05-046. |

## Repositório inicial das listas de e-mail temporário

| Pergunta | Decisão | Status | Raciocínio |
|---|---|---|---|
| Como o repositório do GitHub deixa de existir no ambiente de teste | O `INSERT` sai de `V6__create_disposable_domain_tables.sql` e vai para uma migration de dados numa pasta própria (`db/seed-email-service/`), que só `config-repo/email-service-staging.yml` e `-production.yml` acrescentam a `spring.flyway.locations`. Histórico do Flyway igual ao de hoje (`email_service_schema_history`) | proposta (a decisão de não criar é da Leila, 2026-10-20) | O ambiente `docker` e os testes nunca têm o repositório, sem precisar apagar. Mesmo mecanismo de configuração por ambiente que o projeto já usa (`config-repo`). |

## Estrutura de módulos/pacotes

- `*/src/test/java/.../testsupport/`: clientes HTTP, JDBC e AWS de cada módulo.
- `*/src/test/java/.../ArchitectureTest.java`: a regra "nada sobe".
- `*/pom.xml`: `cucumber-picocontainer` no lugar de `cucumber-spring`; dependências de teste do
  Spring Boot e do Quarkus saem quando a última classe que as usa sair.
- `docker-compose.blackbox.yml`, `scripts/cloud-up.sh`: agentes JaCoCo e configuração de teste.
- `scripts/test-coverage.sh` (novo): *dump* e relatório de todos os serviços.
- `.github/workflows/ci.yml`: novo fluxo.
- `memory/constitution.md`: a regra.
- `specs/05-028-.../spec.md`: nota de que esta spec a estende.

## Riscos e trade-offs

- **Testes Python que sobem containers no CI e na sessão de nuvem:** no CI, as imagens são
  montadas pelo Compose como hoje. Na sessão de nuvem da Claude, os containers da rede do Compose
  não alcançam o proxy (05-052): as imagens precisam já estar prontas ou ser montadas a partir dos
  jars do host. Resolver na T004.
- **Tempo:** cada cenário que levanta e derruba containers leva dezenas de segundos. Agrupar os
  cenários que usam a mesma configuração.

- **Testes dependentes de estado compartilhado:** o serviço e o banco são os mesmos para toda a
  suíte. Cada cenário usa dados próprios (endereços e nomes únicos, como já faz a suíte Python) e
  limpa o que criou.
- **Processos em segundo plano dos serviços** (publicador da 05-046, importação agendada da
  05-048) agem sobre os dados dos testes. Os testes conferem o efeito final (o e-mail no SES, o
  status no banco) com espera (Awaitility), não estados intermediários que o serviço muda sozinho.
- **Cobertura menor no começo:** caminhos que hoje só são exercitados com o serviço subido com
  outra configuração deixam de contar até a decisão em aberto 1 ser aplicada.
- **Trabalho grande:** dezenas de classes de passo e de teste. Por isso a migração é por módulo,
  em PRs separadas.
- **Mais lento para depurar:** o erro aparece no log do serviço, não na pilha do teste. O
  visualizador de logs (05-043) e os logs do `cloud-up.sh` ajudam.
