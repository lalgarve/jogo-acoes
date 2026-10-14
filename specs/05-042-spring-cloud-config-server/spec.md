# Spec: Spring Cloud Config Server

**Status:** implementada; falta a suíte `blackbox-tests` (T015, ver `tasks.md`)
**Issue:** [#165](https://github.com/lalgarve/jogo-acoes/issues/165)
**Iteração:** iteration-5 (Etapa 3 da disciplina)

## Resumo

Um serviço novo, `config-server` (Spring Cloud Config Server), passa a entregar a configuração de
ambiente do `app` e do `email-service`. Cada serviço, ao subir, busca no `config-server` a
configuração do próprio nome e do perfil ativo (`docker`, `staging`, `production`), em vez de ler
os `application-<perfil>.yml` empacotados no próprio jar.

## Motivação

É requisito da Etapa 3 da disciplina ("Spring Cloud Config Server"), hoje marcado como **não
atendido** em `docs/disciplina/alinhamento-projeto-disciplina.md` e no rascunho de entrega.
`docs/context/iteracao-5.md` (Etapa 3) e `docs/roadmap.md` já previam o Config Server e deixavam
em aberto se o `app` também migraria para ele; esta spec fecha essa decisão (ver "Decisões em
aberto", item 1).

Além do requisito, o ganho concreto: a configuração que muda por ambiente (endereços de banco,
LocalStack, `email-service`, base dos links, níveis de log, Flyway ligado ou não) sai de dentro
dos artefatos e fica num lugar só, versionado, separada do código. Mudar um valor de ambiente
deixa de exigir rebuild da imagem.

## Requisitos funcionais

- Existe um serviço `config-server`, com deploy próprio (imagem Docker própria), que serve a
  configuração por `{aplicação}/{perfil}` no protocolo HTTP padrão do Spring Cloud Config.
- A configuração servida fica num diretório versionado neste repositório (`config-repo/`), um
  arquivo por serviço e ambiente, com o mesmo nome de aplicação que cada serviço já declara em
  `spring.application.name`:

  | Serviço | `spring.application.name` | Arquivos |
  |---|---|---|
  | `app` | `jogo-acoes` | `jogo-acoes-docker.yml`, `jogo-acoes-staging.yml`, `jogo-acoes-production.yml` |
  | `email-service` | `email-service` | `email-service-docker.yml`, `email-service-staging.yml`, `email-service-production.yml` |

- O `app` e o `email-service` buscam a configuração no `config-server` ao subir, em todos os
  ambientes (`docker`, `staging`, `production`). O endereço do `config-server` vem de variável de
  ambiente, com padrão `http://localhost:8888` no `docker`.
- **`config-server` fora do ar é erro**: o serviço cliente não sobe (falha rápida na
  inicialização), em vez de subir com uma configuração parcial. Mesma regra de "Testes exigem a
  infraestrutura de pé" da constitution, aplicada à execução.
- O conteúdo de cada `application-<perfil>.yml` de ambiente (`docker`, `staging`, `production`)
  sai do jar e passa para `config-repo/`, com os mesmos valores e os mesmos comentários
  autodescritivos (constitution, "Nomenclatura de ambientes"). No jar fica só o que não muda por
  ambiente (context-path, schema, pasta de migrations, nome da aplicação, timeouts do Feign,
  `spring.config.import` do Config Server).
- **Nenhum segredo em `config-repo/`.** Senhas, API-Keys e o pepper de HMAC continuam vindo de
  variável de ambiente do serviço cliente: o arquivo servido carrega só o placeholder
  (`${SPRING_DATASOURCE_PASSWORD}`), resolvido pelo cliente. Valores de teste já versionados hoje
  (credenciais do Postgres do `docker`, chave e pepper de teste) continuam como estão, no mesmo
  escopo `docker`.
- O `config-server` exige autenticação (HTTP Basic). Usuário e senha vêm de variável de ambiente;
  no `docker` há um valor de teste versionado, como as demais credenciais de teste.
- `docker-compose.yml` sobe o `config-server`, e `app` e `email-service` só sobem depois que ele
  estiver aceitando requisições.

## Requisitos não-funcionais

- Memória: o `config-server` é mais uma JVM no Compose. Deve ser enxuto (sem banco, sem
  dependência além do starter do Config Server e do Security), com limite de heap explícito no
  Compose (ver `plan.md`).
- Java 21 e o mesmo *release train* do Spring Cloud já usado no `app` (2025.1.3). Se o Config
  Server dessa versão não funcionar sobre o Spring Boot 4.1, parar e reportar (constitution,
  "Baseline Java e upgrades de LTS").
- Os testes continuam rodando contra a infraestrutura real do Compose, sem nada pulado
  (constitution, "Testes exigem a infraestrutura de pé").

## Critérios de aceite

- `docker compose up` sobe `config-server`, `email-service` e `app`; o `app` envia e-mail pelo
  `email-service` exatamente como hoje (link de login chega ao SES do LocalStack).
- `GET http://localhost:8888/jogo-acoes/docker` (com credenciais) devolve a configuração do `app`
  no `docker`; sem credenciais, devolve `401`.
- Com o `config-server` parado, `app` e `email-service` não sobem e o log diz que não conseguiram
  buscar a configuração.
- Nenhum `application-docker.yml`, `application-staging.yml` ou `application-production.yml`
  dentro dos jars do `app` e do `email-service`.
- CI verde, com o `config-server` de pé durante a suíte.

## Fora de escopo

- `email-lambda` (Quarkus, roda como Lambda): continua configurada por variável de ambiente da
  função. Não é cliente do Config Server.
- Perfil `blackbox` do `app` (`application-blackbox.yml`): é um overlay de teste, não um ambiente;
  fica no jar como está. O isolamento dele é assunto da Issue #117 (item 8, Iteração 6).
- Atualização de configuração em tempo de execução (`@RefreshScope`, `/actuator/refresh`, Spring
  Cloud Bus): mudar configuração continua exigindo reiniciar o serviço.
- Criptografia de valores no Config Server (`{cipher}`), Vault ou outro cofre de segredos.
- Service discovery (Eureka) e gateway de API.
- Centralização de log (VictoriaLogs), que a autora planeja para a mesma Etapa 3 em spec própria.

## Decisões em aberto

Nenhuma. Resolvidas na sessão de 2026-10-13 (detalhe em `plan.md`):

1. **O `app` também migra para o Config Server?** Sim, `app` e `email-service` juntos. A decisão
   em aberto de `iteracao-5.md` foi escrita quando o Config Server seria só para componentes novos
   (Serviço de E-mail, Admin); o Admin não vai existir, e manter o `app` fora deixaria dois jeitos
   de configurar ambiente no mesmo repositório.
2. **Onde o Config Server lê a configuração (backend)?** `native`: diretório `config-repo/` deste
   repositório, montado no container, em todos os ambientes.
3. **Os testes automatizados dos clientes buscam configuração no Config Server?** Não. A suíte de
   cada serviço continua com o próprio `src/test/resources/application.yml` (que já substitui o
   principal) e desliga o import; um teste de integração por serviço sobe contra o `config-server`
   real do Compose e prova que a configuração vem dele.
4. **`email-service` em `staging`:** hoje ele não tem `application-staging.yml`. Decisão da
   autora: criar `email-service-staging.yml` junto com o `email-service-production.yml`, no mesmo
   formato (tudo que é de infraestrutura e segredo vem de variável de ambiente, sem padrão).
