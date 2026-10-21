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
  uma fila Amazon SQS e envia o e-mail via Amazon SES. Quem publica na fila é o Serviço de
  E-mail (`email-service/`); o sistema principal só chama a API dele e nunca fala com a fila nem
  com o SES diretamente.
- **Especificação de domínio (BDD)**: cada fluxo (login, criação de competição, gerência de
  jogadores, pedido de entrada) tem cenários Gherkin cobrindo caminho feliz e casos de erro,
  executados a cada mudança.
- **Modelo de dados** em [`docs/diagrams/der.md`](docs/diagrams/der.md), com diagrama de
  classes e de sequência complementares em
  [`docs/diagrams/classes.md`](docs/diagrams/classes.md) e
  [`docs/diagrams/sequencia.md`](docs/diagrams/sequencia.md).
- **Integração contínua** (`.github/workflows/ci.yml`): suítes de testes do `app` (com piso de
  cobertura de linha, JaCoCo, 80%), do `email-service` e do `email-lambda` rodando a cada
  *pull request* para `master` ou `development` (e a cada push nesses branches) contra
  infraestrutura real (Postgres, SQS e SES no LocalStack) via Docker, não contra simulação em
  memória.

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
dos módulos — cada um mantém seu próprio *parent*/BOM):

| Módulo | Framework | O quê |
|---|---|---|
| `app/` | Spring Boot | O sistema principal (API, persistência, regras de negócio) |
| `email-lambda/` | Quarkus | AWS Lambda que consome a fila de e-mail e envia via SES — ver "Pipeline de e-mail ponta a ponta em desenvolvimento" abaixo pra rodar como processo vivo localmente |
| `email-service/` | Spring Boot | Serviço de E-mail (specs 05-025 e 05-031) — cadastro de templates sincronizado com o SES e envio de e-mail por template; ver "Cadastro de templates do Serviço de E-mail" e "Envio de e-mail pelo Serviço de E-mail" abaixo |
| `config-server/` | Spring Cloud Config Server | Entrega a configuração de cada ambiente do `app` e do `email-service`, lida de `config-repo/` (spec 05-042); ver "Ambientes" abaixo |

`mvn verify` na raiz builda todos. Pra rodar só um: `mvn -pl app -am verify`,
`mvn -pl email-lambda -am verify`, `mvn -pl email-service -am verify` ou
`mvn -pl config-server -am verify`.

### Módulos do sistema principal

Dentro do `app/`, o código é organizado por domínio, um pacote por módulo em
`dev.leilaalgarve.jogoacoes.<módulo>`:

| Módulo | Responsabilidade | Depende de |
|---|---|---|
| `user` | Identidade: usuários, papéis e os serviços de leitura e criação de usuário | — |
| `link` | Mecanismo genérico de link mágico (criar, consumir, invalidar) | — |
| `captcha` | Verificação de prova de trabalho (ALTCHA) | — |
| `loginsecurity` | Monta o `SecurityFilterChain` a partir das regras que cada módulo declara | — |
| `log` | Log de auditoria imutável | `user` |
| `email` | Cliente do Serviço de E-mail (envio e sincronização de templates) | `user`, `competition` |
| `loginsession` | Pedido e consumo do link de login, sessões ativas, usuário logado | `user`, `link`, `loginsecurity`, `email`, `log` |
| `competition` | Competições, participações, convites, pedidos de entrada, gerência de jogadores | `user`, `link`, `loginsession`, `loginsecurity`, `email`, `captcha`, `log` |
| `bootstrap` | Cria o primeiro administrador ao subir | `user` |
| `common` | Tradução das exceções de negócio em respostas HTTP | `captcha`, `competition`, `email`, `link`, `loginsecurity` |

Os módulos só se falam por serviços, interfaces, entidades e enums; nenhum usa o repositório de
outro (regra verificada pelo `ArchitectureTest`). O diagrama completo, com o que atravessa cada
seta, está em [`docs/diagrams/modulos.md`](docs/diagrams/modulos.md).

**Exemplo de dependência entre módulos.** Ao convidar um jogador para uma competição privada, o
`competition` cria o link de convite pelo `LinkService` (módulo `link`) e pede o envio do convite
pela interface `EmailSender` (módulo `email`). O `competition` não conhece a implementação do
envio: o `email` é quem chama o Serviço de E-mail. No sentido contrário, o `link` não importa nada
do `competition`. Quando o convidado abre o link, o `link` chama o `CompetitionLinkHandler`, que o
`competition` registra como implementação da interface `LinkHandler`, declarada no `link`.

### Serviço independente: o Serviço de E-mail

O candidato a serviço independente identificado na Etapa 1 foi o envio de e-mail, e ele virou o
`email-service/` na Etapa 2. A separação se justifica por quatro motivos:

- **Responsabilidade sem relação com o jogo.** Cadastrar templates, validar o pedido de envio e
  entregar pelo SES não depende de nenhuma regra de competição; qualquer aplicação pode ser cliente,
  autenticada por API-Key.
- **Infraestrutura própria.** Só ele fala com a fila SQS e com o SES; o `app` não conhece nenhum
  dos dois.
- **Dados próprios.** Templates e envios ficam no banco e no schema
  do `email-service`, sem compartilhar entidades, repositórios nem migrations com o `app`.
- **Ritmo de mudança diferente.** O serviço pode ser publicado e escalado sem republicar o jogo.

O `app` chama o `email-service` de forma síncrona, por REST, com um cliente OpenFeign gerado do
contrato [`docs/openapi-email-service.yaml`](docs/openapi-email-service.yaml). Nenhum dos dois
depende do código Java do outro, regra verificada pelo `ArchitectureTest` de cada serviço.

## Como rodar os testes

Os testes rodam contra a infraestrutura real do Docker Compose, que precisa estar de pé antes
(`memory/constitution.md`, "Testes exigem a infraestrutura de pé"). São duas etapas, na mesma
ordem da CI (`.github/workflows/ci.yml`). Cada suíte roda sem nenhuma variável de ambiente,
porque `docker` é o perfil padrão (spec 05-035).

**1. Suíte do `app`**, que chama o `email-service` de verdade, por isso com ele no ar:

```bash
docker compose up -d --wait db db-email-service localstack dns
docker compose up -d --wait config-server
./scripts/test-api-key.sh restore
docker compose up -d --wait email-service
./scripts/test-api-key.sh restore
mvn -pl app -am verify
```

O `restore` roda duas vezes porque a segunda define o remetente do cliente `jogo-acoes`, numa
tabela que só existe depois que o `email-service` sobe (ver "Envio de e-mail pelo Serviço de
E-mail" abaixo).

**2. Suítes do `email-service` e da `email-lambda`**, com tudo derrubado e levantado de novo,
**sem** o container do `email-service`:

```bash
docker compose down -v
docker compose up -d --wait db-email-service localstack dns config-server
./scripts/test-api-key.sh restore
mvn -pl email-service -am verify
mvn -pl email-lambda -am verify
```

O container do `email-service` não pode estar no ar durante a suíte dele: o publicador em segundo
plano do serviço rodando lê o mesmo banco e publica na fila dele os envios que os testes criam,
e os testes falham (spec 05-046, T020). O `email-service` também precisa da biblioteca de
API-KEY no `~/.m2` (`./scripts/install-api-key-lib.sh`, uma vez).

O `config-server` roda em qualquer das duas etapas, ou sem nada de pé:

```bash
mvn -pl config-server -am verify
```

As suítes do `app` e do `email-service` usam a própria configuração de teste
(`src/test/resources/application.yml`), não a do `config-server`; só os testes que provam a
leitura real dele (`ConfigServerClientIntegrationTest` nos dois, e o teste do perfil
`production` no `app`) precisam do `config-server` de pé. O `config-server` testa os arquivos
reais de `config-repo/` e não precisa de infraestrutura.

O `email-lambda` usa o LocalStack do Compose, em `localhost:4566`, com fila de erros, remetentes e
templates próprios. Nenhum teste sobe container: o Dev Services do Quarkus está desligado (spec
05-053).

**Se a infraestrutura não estiver de pé, os testes que dependem dela falham** com erro de
conexão. Nenhum teste se pula por falta dela, e uma regra ArchUnit em cada módulo impede que um
teste use `Assumptions` do JUnit para decidir em tempo de execução se roda. No `app`, um contexto
que não sobe pode ainda derrubar em cascata testes de outras classes que não dependem da peça
que falta. Nesse caso, olhe primeiro o erro do primeiro contexto que falhou.

## Ambientes

O perfil ativo do Spring é escolhido por `SPRING_PROFILES_ACTIVE` (ou `docker`, se nenhum for
definido). A configuração de cada ambiente não fica dentro do jar: o `app` e o `email-service`
buscam no `config-server` (Spring Cloud Config Server, spec 05-042), ao subir, o arquivo
`config-repo/<aplicação>-<perfil>.yml` (`jogo-acoes-docker.yml`, `email-service-production.yml`
etc.):

| Variável | Para quê |
|---|---|
| `CONFIG_SERVER_URL` | Endereço do `config-server` (padrão `http://localhost:8888`) |
| `CONFIG_SERVER_USERNAME`, `CONFIG_SERVER_PASSWORD` | HTTP Basic do `config-server`, sem padrão; no `docker-compose.yml`, credenciais de teste versionadas |

Sem o `config-server`, o serviço não sobe: depois de algumas tentativas, falha na
inicialização. Por isso rodar o `app` ou o `email-service` fora do Compose (`java -jar`,
`mvn spring-boot:run`) exige o `config-server` de pé, como já exige o banco. Os arquivos de
`config-repo/` não têm segredo: fora do `docker`, senhas e chaves aparecem só como
`${VARIAVEL}`, resolvidas pelo próprio serviço com as variáveis de ambiente dele. Mudar um valor
de ambiente não exige rebuild da imagem, só reiniciar o serviço.

Perfis do `app`:

| Perfil | Banco | Quando usar |
|---|---|---|
| `docker` (padrão) | PostgreSQL real em containers | Localmente via `docker compose up`, testes e CI |
| `docker,blackbox` | PostgreSQL real em containers | Testes de caixa-preta (Swagger UI, Selenium futuro, suíte Python) — ver "Ambiente de testes blackbox" abaixo |
| `staging` | PostgreSQL real, gerido por outra equipe | Pré-produção |
| `production` | PostgreSQL real, gerido por outra equipe | Produção |

Todos os perfis usam as mesmas migrations (`db/migration-jogo-acoes`) e o mesmo schema
próprio do serviço (`jogo_acoes`; o `email-service` usa `email_service`) — nada fica no schema
`public` (spec 05-032, seção "Banco de dados: um schema por serviço" de
`memory/constitution.md`).

Para rodar localmente com Postgres real:

```
docker compose up
```

### Subir o ambiente inteiro com `scripts/docker-up.sh`

`docker compose up` sozinho não deixa o ambiente pronto: a chave de API de teste precisa ser
restaurada antes do `email-service` e de novo depois dele (senão o `email-service` responde 401
e o `app` não sobe). O script `scripts/docker-up.sh` (spec
[05-050](specs/05-050-perfil-localstack-pro/spec.md)) faz essa sequência na ordem certa, com as
opções lidas de um arquivo de configuração:

```
./scripts/docker-up.sh           # sobe tudo
./scripts/docker-up.sh --reset   # docker compose down -v antes: bancos vazios
```

Sem configurar nada, ele usa o modelo versionado `docker-up.conf.example`: overlay `blackbox`,
LocalStack Community (sem token), rebuild das imagens e as ferramentas web (Adminer,
`ses-viewer`, `log-viewer`). Para mudar alguma opção, copie o modelo para o arquivo local, que
não é versionado (`.gitignore`), e edite a cópia:

```
cp docker-up.conf.example docker-up.conf
```

| Chave | Padrão do modelo | O que faz |
|---|---|---|
| `BLACKBOX` | `true` | Empilha `docker-compose.blackbox.yml` (captcha aceito, administrador semeado, JaCoCo) |
| `LOCALSTACK_PRO` | `false` | Usa o LocalStack Pro (`docker-compose.localstack-pro.yml`) no lugar do Community |
| `LOCALSTACK_AUTH_TOKEN` | vazio | Token da licença do LocalStack, só com `LOCALSTACK_PRO=true`; vazio, vale o `LOCALSTACK_AUTH_TOKEN` exportado no shell |
| `BUILD` | `true` | `--build`: reconstrói as imagens a partir do checkout |
| `TOOLS` | `true` | Sobe também `adminer`, `ses-viewer` e `log-viewer` |

No fim, o script mostra o `export COMPOSE_FILE=...` com os mesmos arquivos, para os outros
comandos `docker compose` (`logs`, `exec`, `down`) no mesmo shell.

#### LocalStack Pro (opcional)

O `docker-compose.yml` usa o LocalStack Community (`localstack/localstack:4`), que sobe sem
conta nem token: quem não tem licença roda tudo normalmente, e o CI também. Quem tem licença
pode usar o LocalStack Pro (`localstack/localstack-pro:4`, mesma versão) com
`LOCALSTACK_PRO=true` e o token em `docker-up.conf` (o token vem do
[painel do LocalStack](https://app.localstack.cloud); nunca no `docker-up.conf.example`).

Sem o script, o overlay também funciona direto, com o token exportado:

```
export LOCALSTACK_AUTH_TOKEN=<seu token>
docker compose -f docker-compose.yml -f docker-compose.localstack-pro.yml up
```

Sem token, o Compose recusa o comando com esse overlay. Com token inválido, o LocalStack Pro para
na subida (`License activation failed!` no log dele); o `docker-up.sh` mostra essas linhas.

**Ao atualizar de uma versão anterior à spec 05-032**, recrie os volumes uma vez:

```
docker compose down -v
docker compose up
```

Os scripts de `docker/postgres*/init/` (que criam o schema, os papéis e o `search_path`) só rodam
num volume novo. Num volume antigo as tabelas continuam no `public` e o Flyway recriaria tudo no
schema novo, deixando as antigas órfãs. O sistema está em pré-produção, então não há dado a
preservar (a chave de API de teste se restaura com `./scripts/test-api-key.sh restore`).

### Numa sessão do Claude Code na nuvem

Numa sessão de nuvem da Claude, nem o `docker compose up` nem o `docker-up.sh` funcionam: a sessão
só sai para a internet por um proxy em `127.0.0.1`, que os containers da rede do Compose não
alcançam (o Maven dos `Dockerfile`s não baixa dependências e o LocalStack Pro não ativa a licença).
Lá, o ambiente sobe com outros scripts (spec 05-052):

```
./scripts/cloud-up.sh               # compila no host e sobe tudo do zero, com LocalStack Pro
./scripts/cloud-up.sh --no-build    # sobe de novo com os artefatos já compilados
./scripts/cloud-blackbox-tests.sh   # roda a suíte blackbox-tests/ (behave e pytest)
./scripts/cloud-down.sh             # derruba tudo, com os volumes
```

O `cloud-up.sh` compila no host, sobe `db`, `db-email-service` e `dns` pelo `docker-compose.yml`, o
LocalStack Pro num container com a rede do host e `config-server`, `email-service` e `app` (perfis
`docker,blackbox`) com `java -jar`, nos mesmos endereços da tabela abaixo. Precisa do
`LOCALSTACK_AUTH_TOKEN` nas variáveis do ambiente de nuvem; sem ele, para com erro (não troca para
o LocalStack Community). Logs e PIDs ficam em `/tmp/jogo-acoes-cloud` (ou em
`JOGO_ACOES_CLOUD_DIR`). Toda subida começa do zero, porque um LocalStack novo ao lado de um banco
do `email-service` já preenchido fica sem os templates no SES. Fora da nuvem, o script recusa
rodar e indica o `docker-up.sh`.

As variáveis dos serviços ficam copiadas no `cloud-up.sh`: ao mudar uma variável de `app`,
`email-service`, `config-server` ou `localstack` nos `docker-compose*.yml`, mude lá também.

## Endereços dos serviços

Com `docker compose up` (e o overlay `blackbox`, quando indicado), cada serviço fica publicado
no host em:

| Serviço | Endereço | Observação |
|---|---|---|
| `app` (API) | `http://localhost:8080/api` | Contrato em `docs/openapi.yaml` |
| Swagger UI do `app` | `http://localhost:8080/api/swagger-ui/index.html` | O pedido de login só passa sem captcha com o overlay `blackbox` |
| Swagger UI do `email-service` | `http://localhost:8082/api/swagger-ui.html` | Só no `docker` (spec 05-049). A tela abre sem chave; as chamadas pedem a `X-API-Key` no botão **Authorize** |
| `email-service` (API) | `http://localhost:8082/api` | Exige API-Key (exemplos em "Envio de e-mail pelo Serviço de E-mail") |
| `config-server` | `http://localhost:8888/{aplicação}/{perfil}` | Ex.: `/jogo-acoes/docker`; HTTP Basic `config` / `config-docker-not-a-secret` |
| `ses-viewer` | `http://localhost:3005` | E-mails enviados pelo SES do LocalStack |
| LocalStack | `http://localhost:4566` | SES em JSON: `GET /_aws/ses` |
| DNS de teste | `127.0.0.1:1053` (UDP e TCP) | Zona `.test` da verificação do domínio do destinatário (spec 05-047) |
| Adminer | `http://localhost:8081` | Servidor `db` (usuário/senha `jogo_acoes_admin`) ou `db-email-service` (`email_service_admin`) |
| PostgreSQL do `app` | `localhost:5432` | Banco `jogo_acoes` |
| PostgreSQL do `email-service` | `localhost:5433` | Banco `email_service` |
| JaCoCo (só `blackbox`) | `localhost:6300` | Usado por `scripts/blackbox-coverage.sh` |

## Ambiente de testes blackbox

`blackbox` (spec 05-014) é um perfil empilhado sobre `docker` — nunca usado sozinho — pensado
para exercitar a API só por fora (Swagger UI, Selenium no futuro, ou a
[suíte de testes Python](blackbox-tests/README.md), spec 05-015), sem as duas coisas que
normalmente exigem um cliente completo:

- **Captcha sempre aceito** — não existe ainda um frontend que resolva o desafio ALTCHA de
  verdade, então qualquer `captchaToken` (incluindo vazio) é aceito. Só neste perfil: qualquer
  outro (`docker` sozinho, `staging`, `production`) continua exigindo um captcha
  resolvido de verdade.
- **Administrador já semeado** — como só um administrador pode criar competições e não existe
  via de API para criar um, a aplicação garante, de forma idempotente ao subir, um administrador
  com e-mail `success+admin@simulator.amazonses.com` (simulador de caixa de entrada do Amazon
  SES, spec 05-016). Não há senha em lugar nenhum do sistema — login é sempre
  por link mágico.
- **Leitura do link de um e-mail** — `POST /login-requests` nunca devolve o link no corpo
  (deliberado, pra não revelar se o e-mail existe). Não há endpoint de `app/` pra isso (spec
  05-023 removeu o que existia antes): a [suíte Python](blackbox-tests/README.md) lê o link
  direto do LocalStack (`GET http://localhost:4566/_aws/ses`, mesmo mecanismo da seção
  ["Pipeline de e-mail ponta a ponta em desenvolvimento"](#pipeline-de-e-mail-ponta-a-ponta-em-desenvolvimento)
  abaixo), sem nenhum código Java no meio.

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
  ./scripts/blackbox-clock-offset.sh -30
  ```
  Automatiza exatamente este comando (script idempotente, sem tocar em nada versionado):
  ```
  docker compose -f docker-compose.yml -f docker-compose.blackbox.yml run --rm --service-ports \
    -e FAKETIME_OFFSET="-30 days" \
    --entrypoint "sh -c 'apt-get update -qq && apt-get install -y -qq faketime && faketime \"\$FAKETIME_OFFSET\" java -jar app.jar'" \
    app
  ```
  `--service-ports` é obrigatório aqui — sem ele, `docker compose run` não publica as portas
  do serviço (`8080`, e `6300` se a sobreposição `blackbox` estiver ativa), e nada rodando no
  host consegue alcançar `localhost:8080`.

Um `Clock` injetável (bean configurável por propriedade, com uma implementação real para
produção e uma deslocada em dias para teste) foi considerado e descartado como **último
recurso**, não como próxima etapa: teria que ser lido em todo lugar que hoje chama
`LocalDate.now()`/`LocalDateTime.now()` diretamente, e um único ponto esquecido (código novo,
uma biblioteca, um cantinho não migrado) misturaria hora real com hora deslocada de forma
silenciosa — um bug sutil e difícil de notar. Deslocar o relógio que o processo `app` inteiro
enxerga (as duas opções acima) não tem esse risco: todo `now()` vê o mesmo deslocamento, sem
precisar manter nenhum código sincronizado com isso.

### Testando rótulo de dispositivo (Client Hints) pelo Swagger UI

`consumeLoginLink`/`completeRegistration` (spec 05-009) montam o rótulo de dispositivo mostrado
em `GET /sessions` a partir dos headers `Sec-CH-UA*`, e o Swagger UI do próprio `app/` mostra um
campo pra preenchê-los — mas nenhum navegador deixa uma página mandar um header começando com
`Sec-` de propósito (é assim que ele impede que a página falsifique esses hints), então clicar
em "Execute" nunca manda o valor digitado.

Preencher os campos `Sec-CH-UA*` normalmente no "Try it out" do
Swagger UI e copiar o comando `curl` que ele já monta ao lado (com os headers preenchidos
certinho) — rodar esse `curl` direto no terminal em vez de clicar "Execute" contorna a
restrição, já que ela só existe pra scripts de página, não pra um cliente HTTP de linha de
comando:

```
curl -X 'GET' \
  'http://localhost:8080/api/login-links/{token}' \
  -H 'accept: */*' \
  -H 'Sec-CH-UA: "Chromium";v="131"' \
  -H 'Sec-CH-UA-Platform: "Windows"' \
  -H 'Sec-CH-UA-Platform-Version: "15.0.0"' \
  -H 'Sec-CH-UA-Mobile: ?0'
```

(exemplo — o Swagger UI gera o comando exato pra rota e valores preenchidos no momento; o
cookie de sessão, se precisar, pode ser copiado das ferramentas de desenvolvedor do navegador)

## Pipeline de e-mail ponta a ponta em desenvolvimento

`email-lambda/` existe e é testado (`EmailSendHandlerTest`), mas nunca é implantado numa conta
AWS real (isso continua bloqueado). `docker compose up` sozinho já fecha o pipeline completo pra
uma sessão normal de desenvolvimento — nada de sobreposição separada:

```
docker compose up
```

Por baixo, três coisas acontecem em ordem: o serviço `email-lambda-builder` builda o artefato
real do módulo (`function.zip`) e sai; o LocalStack sobe só depois disso (`depends_on:
condition: service_completed_successfully`) com o serviço `lambda` habilitado; e um script de
inicialização faz o deploy desse artefato como uma função Lambda de verdade dentro do
LocalStack, com um *event source mapping* real ligado na mesma fila em que o `email-service`
publica (o `app/` pede o envio ao `email-service` por `POST /emails`) — o mesmo mecanismo de
disparo que a AWS real usaria em produção, sem
nenhum consumidor customizado no meio (verificado de ponta a ponta, ver Issue
[#87](https://github.com/lalgarve/jogo-acoes/issues/87)).

O `email-lambda-builder` só roda o Maven quando precisa: se `email-lambda/dist/function.zip` já
existe e é mais novo que o `pom.xml` da raiz, o `email-lambda/pom.xml` e tudo em
`email-lambda/src/`, ele sai na hora sem buildar. Pra forçar o rebuild, apague
`email-lambda/dist/`.

Pra confirmar que uma mensagem foi processada, direto na fila (deve zerar depois que a função
processa o que estava pendente):

```
docker compose exec localstack awslocal sqs get-queue-attributes \
  --queue-url http://localhost:4566/000000000000/jogo-acoes-email-commands \
  --attribute-names ApproximateNumberOfMessages
```

Ou lendo o e-mail simulado que o SES do LocalStack guardou:

```
curl http://localhost:4566/_aws/ses
```

Ou, pra não ler JSON cru, `docker compose up` também sobe uma interface web sobre esses mesmos
dados ([`ses-viewer`](https://github.com/lalgarve/localstack-aws-ses-email-viewer), fork do
[original](https://github.com/veertech/localstack-aws-ses-email-viewer) com suporte a e-mails
de template, Issues [#88](https://github.com/lalgarve/jogo-acoes/issues/88) e
[#156](https://github.com/lalgarve/jogo-acoes/issues/156) — lista, renderiza e exporta (`.eml`) os
e-mails enviados):

```
http://localhost:3005
```

Ferramenta de conveniência só de dev, mesmo espírito do `adminer` — nunca entra em
`staging`/`production`, nunca é exigida por `mvn test`/CI.

Para acompanhar os logs de `app`, `email-service` e `localstack` no navegador, juntos e com busca
por texto, `docker compose up` também sobe o visualizador de logs (Dozzle, spec
[05-043](specs/05-043-visualizador-de-logs/spec.md)), mesmo espírito do `ses-viewer`:

```
http://localhost:8083
```

Ele não guarda nada: mostra o que o Docker tem, então `docker compose down` apaga os logs.

## Cadastro de templates do Serviço de E-mail

Primeira feature do Serviço de E-mail (spec
[05-025](specs/05-025-servico-email-templates/spec.md), Issue
[#93](https://github.com/lalgarve/jogo-acoes/issues/93)): cadastro de templates, sincronizado
de verdade com o SES (`CreateTemplate`/`UpdateTemplate` — é essa chamada que valida a sintaxe) e
uma pré-visualização (`TestRenderTemplate`) sem enviar e-mail nenhum. Sobe junto com
`docker compose up`, num container/banco próprios (`db-email-service`), reaproveitando o mesmo
LocalStack de `email-lambda`.

**O header `X-API-Key` é validado de verdade** (spec
[05-030](specs/05-030-validacao-api-key-servico-email/spec.md)) pela biblioteca
`api-key-validation` do projeto [`lalgarve/api-key`](https://github.com/lalgarve/api-key):
formato, hash, expiração e revogação. O dono dos templates é o cliente para o qual a chave foi
emitida, não o texto da chave. Qualquer chave rejeitada recebe o mesmo `401`; o motivo exato só
aparece no log do serviço.

A biblioteca não está em nenhum repositório Maven que o build lê. Antes do primeiro build local
do `email-service`, instale-a no `~/.m2` (baixa da release do GitHub só na primeira vez; o
`Dockerfile` faz o mesmo sozinho):

```
./scripts/install-api-key-lib.sh
```

Em `docker` existe uma chave de teste fixa, do cliente `jogo-acoes`. Ela mora no
schema `api_key` do banco `db-email-service` e precisa ser restaurada num volume novo (a suíte
de testes do módulo também precisa dela):

```
docker compose up -d --wait db-email-service
./scripts/test-api-key.sh restore
```

Detalhes da chave e do pepper de teste em
[`docker/postgres-email-service/test-data/README.md`](docker/postgres-email-service/test-data/README.md).

Cadastrar um template:

```
curl -X POST http://localhost:8082/api/templates \
  -H "X-API-Key: dak_IpfF8aaAizW6r1rSC59yi6BwMs4ox3GQDPiAWamRucU" \
  -H "Content-Type: application/json" \
  -d '{"name": "welcome", "subject": "Bem-vindo!", "body": "Olá {{name}}, bem-vindo!"}'
```

Pré-visualizar (não envia e-mail, só devolve o texto renderizado):

```
curl -X POST http://localhost:8082/api/templates/welcome/preview \
  -H "X-API-Key: dak_IpfF8aaAizW6r1rSC59yi6BwMs4ox3GQDPiAWamRucU" \
  -H "Content-Type: application/json" \
  -d '{"variables": {"name": "Ada"}}'
```

Contrato completo: [`docs/openapi-email-service.yaml`](docs/openapi-email-service.yaml).

## Envio de e-mail pelo Serviço de E-mail

Spec [05-031](specs/05-031-servico-email-envio/spec.md): `POST /emails` pede o envio de um
e-mail a partir de um template já cadastrado pelo próprio cliente. O serviço confere o pedido,
grava o envio (`email_send`), publica uma mensagem na fila `jogo-acoes-email-commands` e
responde `202` na hora, com o `id` do envio. Quem envia de fato é a Lambda de e-mail
(`email-lambda`), com `SendTemplatedEmail` do SES: o SES renderiza o template no envio, e o `id`
vai junto como *message tag* `correlationId`. O `202` quer dizer "na fila", não "entregue".

**O remetente é fixo por cliente e definido por operações**, não pela API — o pedido não tem
campo `from`. Enquanto o cliente não tiver remetente, `POST /emails` responde `409`. O endereço
só é conferido quanto ao formato; ele precisa ser uma identidade verificada no SES, senão o
envio falha depois, na Lambda. Para definir ou trocar o remetente de um cliente (o
`email-service` precisa já ter subido uma vez, para o Flyway criar a tabela):

```
./scripts/set-email-sender.sh jogo-acoes no-reply@jogo-acoes.example
```

Em `docker`, `./scripts/test-api-key.sh restore` já faz isso para o cliente de teste
`jogo-acoes`, com o endereço que o LocalStack verifica na subida
(`EMAIL_SENDER_ADDRESS` em `docker-compose.yml`). Sem o container `db-email-service` rodando, o
script usa o `psql` local; `--host`/`--port`, `EMAIL_SERVICE_DB_USER` e `PGPASSWORD` apontam
para outro banco.

Enviar um e-mail a partir do template `welcome` cadastrado acima:

```
curl -X POST http://localhost:8082/api/emails \
  -H "X-API-Key: dak_IpfF8aaAizW6r1rSC59yi6BwMs4ox3GQDPiAWamRucU" \
  -H "Content-Type: application/json" \
  -d '{"templateName": "welcome", "recipientEmail": "success@simulator.amazonses.com", "templateData": {"name": "Ada"}}'
```

Resposta: `202 {"id": "<uuid>", "status": "QUEUED"}`. Template inexistente (ou de outro
cliente) dá `404`, pedido malformado dá `400`, domínio do destinatário que não recebe e-mail dá
`422` (ver abaixo) e falha ao publicar na fila dá `503` (nada é enviado nem gravado; pode
repetir). Repetir o mesmo pedido gera dois e-mails: idempotência fica
para a Etapa 4.

A Lambda só aceita mensagens com template.

Os testes do módulo usam uma fila própria no LocalStack (`email-service-send-test`, criada pela
própria suíte), para ler de volta a mensagem publicada antes que a Lambda a consuma.

### Verificação do domínio do destinatário

Spec [05-047](specs/05-047-verificacao-dominio-destinatario/spec.md): antes de gravar o envio,
`POST /emails` confere se o domínio do destinatário recebe e-mail. Vale o registro MX (o MX nulo,
`MX 0 .`, recusa); sem MX, vale um registro A ou AAAA. Domínio aprovado entra na lista branca
(`email_service.approved_domain`) e não é consultado de novo; domínio sem nenhum desses registros
dá `422` e nada é gravado nem enviado. Se o DNS não responder, o envio segue sem entrar na lista
branca. `simulator.amazonses.com`, o domínio do simulador do SES, já vem na lista pela migration.
No `app`, o `422` vira `422` no link de login e nos envios individuais; nos envios em grupo o
jogador recusado é pulado e fica com o status que tinha.

No Compose, o `email-service` consulta o serviço `dns` (CoreDNS, `docker/dns/`), publicado no
host em `127.0.0.1:1053`, que é onde os testes do `email-service` o consultam. Ele responde só
pelas zonas de `docker/dns/Corefile`: a zona `.test` (`docker/dns/test.zone`) tem um domínio
para cada caso (`with-mx.test`, `only-a.test`, `only-aaaa.test`, `null-mx.test`, `no-mail.test`,
`missing.test` sem registro nenhum e `unavailable.test`, que não responde), e
`docker/dns/mail-domain.zone` dá MX aos domínios que os outros testes já usam (`example.com`,
`exemplo.com`, `acme.com`, `client-a.example`, `jogo-acoes.example`). Qualquer outro domínio é
recusado pelo servidor (`REFUSED`), o que conta como DNS sem resposta: o envio segue.

Para um teste usar um domínio novo que precisa ser aprovado ou recusado, inclua o domínio na zona
`.test` ou, se ele não terminar em `.test`, acrescente-o à lista de zonas do bloco
`mail-domain.zone` no `Corefile`, e reinicie o serviço:

```bash
docker compose restart dns
```

Em `staging` e `production`, `email.domain-check.dns-servers` fica vazio e o serviço usa o
resolvedor do sistema.

### Listas de domínios de e-mail temporário

Spec [05-048](specs/05-048-importacao-listas-email-temporario/spec.md): um lote do Spring Batch
dentro do `email-service` baixa listas públicas de domínios de e-mail temporário (Mailinator e
afins) e guarda os domínios na tabela `email_service.disposable_domain`. O `POST /emails` recusa
com `422` um destinatário desses domínios, ou de um subdomínio deles, antes da lista branca e do
DNS da spec 05-047. Cada lista vem de um repositório cadastrado (`disposable_domain_source`), com
endereço, descrição, hora do último download e hora da última atualização no banco; a migration
já cadastra a lista principal do
[`disposable-email-domains`](https://github.com/disposable-email-domains/disposable-email-domains).
Em `staging` e `production` o lote roda todo dia às 4h
(`email.disposable-domains.import-cron`); no `docker`, só quando operações pede.

**Permissões.** Desde a `api-key` 1.1.0, cada chave tem permissões, e cada rota exige a sua:
`email:send` para `/emails` e `/templates`, `disposable-email:admin` para as rotas das listas.
Uma chave sem permissão gravada (a do `jogo-acoes`, emitida antes da 1.1.0) recebe só
`email:send`. Operações usa uma chave só com `disposable-email:admin`, que não envia e-mail:

```
java -jar api-key-1.1.0.jar generate --client operations --permissions disposable-email:admin
```

No `docker`, `./scripts/test-api-key.sh restore` já restaura a chave de teste de operações,
`dak_D9CebHAQ6cq2vAnYIomxJ1nX56qn_5dDRv2J-aHrQFc`
([`docker/postgres-email-service/test-data/README.md`](docker/postgres-email-service/test-data/README.md)).

Cadastrar um repositório e pedir a execução do lote para todos os repositórios (sem corpo) ou
para um só (`{"sourceId": <id>}`):

```bash
OPS_API_KEY=dak_D9CebHAQ6cq2vAnYIomxJ1nX56qn_5dDRv2J-aHrQFc
curl -s -X POST http://localhost:8082/api/disposable-domain-sources \
  -H "X-API-Key: $OPS_API_KEY" -H 'Content-Type: application/json' \
  -d '{"url":"https://example.org/disposable.conf","description":"Minha lista"}'
curl -s -X POST http://localhost:8082/api/disposable-domain-imports -H "X-API-Key: $OPS_API_KEY"
```

O pedido responde `202` com o `executionId` de cada repositório; o lote roda em segundo plano.
`GET /disposable-domain-imports/{executionId}` mostra a situação e as contagens (linhas lidas,
ignoradas, domínios incluídos e removidos), e `GET /disposable-domain-sources` lista os
repositórios com o resultado da última execução de cada um. Um repositório que falha (fora do
ar, status diferente de `200`, maior que `email.disposable-domains.max-size`, lista vazia) não
muda nada e não impede os outros. O roteiro 8 do
[caderno de testes](docs/disciplina/caderno-de-testes.md) faz o caminho completo com uma lista
servida pelo S3 do LocalStack, que é também como os testes do módulo servem as listas.

### O `app/` envia pelo Serviço de E-mail

Desde a spec [05-034](specs/05-034-fronteira-sincrona-app-email-service/spec.md), o `app/`
depende do `email-service` para enviar e-mail (link mágico, convites): cada envio é uma chamada
síncrona a `POST /emails`, feita dentro da transação de negócio. Se o `email-service` estiver
fora do ar, a operação é desfeita e a API do `app/` responde `503`. Na subida, o `app/` também
cadastra no `email-service` os seus 5 templates Handlebars
(`app/src/main/resources/email-templates/`). Por isso o `email-service` precisa estar no ar antes
do `app/`; no `docker compose up` a ordem já é garantida (o `app` espera o `email-service` ficar
saudável).

A chave de teste do cliente `jogo-acoes` também precisa estar restaurada
(`./scripts/test-api-key.sh restore`, ver acima). Num volume novo, rode o script de novo depois
que o `email-service` tiver subido uma vez: só então a tabela de remetentes existe e o remetente
do cliente `jogo-acoes` é definido. Sem chave ou sem remetente, o `email-service` recusa os
envios do `app/`.

Nos testes e nas suítes Cucumber do `app/` nada disso é necessário: sem `email.sender`
configurado, o `app/` usa o `StubEmailSender`, que só grava o envio em `sent_email`.

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
