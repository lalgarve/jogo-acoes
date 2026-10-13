# Plan: Spring Cloud Config Server

Traduz `spec.md` em decisões técnicas. Valida contra `memory/constitution.md`.

## Contexto técnico

- Reator Maven com `app`, `email-lambda` e `email-service`; `app` e `email-service` em Spring
  Boot 4.1.0, Java 21. O `app` já importa o BOM `spring-cloud-dependencies` 2025.1.3 (OpenFeign,
  spec 05-034), validado sobre o Boot 4.1.
- Configuração hoje: `application.yml` comum + `application-docker.yml`,
  `application-staging.yml`, `application-production.yml` dentro de cada jar; o Compose sobrescreve
  valores por variável de ambiente. `spring.profiles.default: docker` nos dois serviços (05-035).
- As suítes de teste de cada serviço têm o próprio `src/test/resources/application.yml`, que
  substitui o principal (registrado nas specs 05-011 e 05-035).
- CI (`.github/workflows/ci.yml`) sobe `db`, `db-email-service`, `localstack` e `email-service`
  pelo Compose e roda `mvn verify` por módulo.

## Decisões de arquitetura

| Pergunta | Decisão | Status | Raciocínio |
|---|---|---|---|
| O `app` também migra? | Sim: `app` e `email-service` são clientes do Config Server | resolvida (2026-10-13) | A ressalva de `iteracao-5.md` era de quando o Config Server seria só dos componentes novos (Serviço de E-mail, Admin). O Admin não vai existir; deixar o `app` de fora manteria dois jeitos de configurar ambiente no mesmo repositório, e o requisito da disciplina fala em centralizar a configuração dos serviços. |
| Módulo | Novo módulo `config-server` no reator, Spring Boot 4.1.0, `spring-cloud-config-server` + `spring-boot-starter-security`, `@EnableConfigServer`. Pacote `dev.leilaalgarve.jogoacoes.configserver`. `Dockerfile` próprio (`config-server/Dockerfile`), no mesmo padrão do `email-service` | resolvida | É um serviço com deploy próprio (constitution, "Fronteira entre módulos e serviços"): nenhuma dependência Java com `app`/`email-service`, e nada de código de produção deles dentro dele. |
| Backend | `native`: lê `config-repo/` (raiz do repositório), montado no container em `/config-repo` (somente leitura); local configurável por `CONFIG_REPO_LOCATION` | resolvida (2026-10-13) | Sem rede nem credencial de GitHub para subir o Compose; o que sobe é exatamente o que está no checkout (inclusive num branch). Alternativa: backend `git` apontando para este repositório (`search-paths: config-repo`, `default-label: master`) — é o uso "canônico" e versiona por *label*, mas exige acesso ao GitHub em todo ambiente, e no `docker` serviria o `master`, não o branch em teste. Para staging/production o deploy entrega o mesmo diretório; trocar para `git` depois é só configuração do `config-server`. |
| Nomes dos arquivos | `{spring.application.name}-{perfil}.yml`: `jogo-acoes-*.yml`, `email-service-*.yml`. Sem `application.yml` compartilhado em `config-repo/` | resolvida | Os nomes de aplicação já existem. Um `application.yml` em `config-repo/` seria servido aos dois serviços e criaria configuração compartilhada entre serviços, o que a fronteira da 05-034 evita. |
| O que sai do jar | Todo `application-docker.yml`, `application-staging.yml` e `application-production.yml` do `app` e do `email-service`, com valores e comentários, para `config-repo/`. Fica no jar o `application.yml` comum (context-path, schema/Flyway, nome, `profiles.default`, Feign, springdoc) e o `spring.config.import` | resolvida (2026-10-13) | Fica no jar o que é do código e não muda por ambiente; vai para o Config Server o que muda. Mover só parte dos arquivos deixaria duas fontes para o mesmo perfil. `application-blackbox.yml` fica no jar (spec, "Fora de escopo"). |
| Como o cliente importa | `spring-cloud-starter-config` + `spring.config.import: configserver:${CONFIG_SERVER_URL:http://localhost:8888}` no `application.yml`, **sem** `optional:`; `spring.cloud.config.fail-fast: true`; usuário/senha de `CONFIG_SERVER_USERNAME`/`CONFIG_SERVER_PASSWORD` | resolvida | Sem `optional:` o serviço não sobe se o Config Server não responder — "infraestrutura fora do ar é erro" (constitution). `fail-fast` deixa o erro explícito na inicialização. |
| Retry do cliente | `spring-retry` com as tentativas padrão do Config Client (6 tentativas, intervalo inicial de 1 s, multiplicador 1,1) | resolvida (2026-10-13) | `GET /{aplicação}/{perfil}` é leitura, idempotente pelo contrato — retry permitido (constitution, "Repetição de chamadas síncronas"). Cobre a corrida de inicialização no Compose sem esconder um Config Server de fato fora do ar (depois das tentativas, falha). |
| Segredos | Nunca valor de segredo em `config-repo/`: só placeholder `${VAR}`, resolvido no cliente. Credenciais/chaves de teste já versionadas continuam só nos arquivos `-docker.yml` | resolvida | O Config Server devolve o valor bruto; o `${...}` é resolvido no `Environment` do cliente, com as variáveis de ambiente dele. Segredo de staging/production nunca passa pelo Config Server nem pelo repositório. Verificar isso de verdade (T004). |
| Autenticação do Config Server | HTTP Basic, um usuário, de `CONFIG_SERVER_USERNAME`/`CONFIG_SERVER_PASSWORD`. No Compose, valor de teste versionado (`config`/`config-docker-not-a-secret`); em staging/production, sem padrão: o `config-server` não sobe sem elas | resolvida | O que ele serve não tem segredo, mas descreve a infraestrutura (hosts, nomes de banco). Basic é o mecanismo documentado do Config Server e o cliente o suporta nativamente. |
| Porta | `8888` (padrão do Config Server), publicada no Compose como `8888:8888` | resolvida | Padrão do Spring Cloud Config; não colide com `8080` (app), `8081` (adminer), `8082` (email-service), `3005` (ses-viewer). |
| Ordem no Compose | Healthcheck do `config-server` por `/dev/tcp/localhost/8888` (como o `email-service`, sem actuator); `app` e `email-service` com `depends_on: config-server: condition: service_healthy` | resolvida | Mesmo padrão já usado entre `app` e `email-service`. |
| Memória | `JAVA_TOOL_OPTIONS: -Xmx128m` no `config-server` do Compose (ajustar na implementação se não bastar, registrando o valor medido) | resolvida (2026-10-13) | Memória é custo importante no início (constitution, "Banco de dados: um schema por serviço"). O Config Server `native` não tem banco nem cache de git; heap pequena basta. |
| Testes dos clientes | A suíte de cada serviço continua com o próprio `src/test/resources/application.yml` e `spring.cloud.config.enabled: false` (sem import). Um teste de integração por serviço (`ConfigServerClientIntegrationTest`) liga o import contra o `config-server` real do Compose e confere que uma propriedade do `-docker.yml` chegou | resolvida (2026-10-13) | Os testes já não leem `application-docker.yml` (o arquivo de teste substitui o principal), então tirar esses arquivos do jar não muda a suíte. Fazer toda a suíte depender do Config Server só trocaria a fonte de valores que o teste já define, sem testar nada novo. O teste de integração prova o caminho real; o `config-server` sobe no CI como os demais serviços. |
| Testes do `config-server` | No próprio módulo, contra o `config-repo/` real do repositório: serve `jogo-acoes/docker` e `email-service/docker` com as propriedades esperadas; `401` sem credencial; nenhum valor servido parece segredo de staging/production (todo valor de senha/chave nesses perfis é placeholder) | resolvida | Testa o arquivo que vai para produção, não uma cópia de teste. |
| Verificação de que saiu do jar | Teste em cada cliente: nenhum recurso `application-docker.yml`, `application-staging.yml`, `application-production.yml` no classpath principal | resolvida | Teste de verificação primeiro (falhando), depois a mudança — preferência registrada para `tasks.md`. Evita que um perfil volte para o jar e passe a valer em silêncio junto do Config Server. |
| ArchitectureTest | `config-server` ganha `ArchitectureTest` (nenhuma classe de `..jogoacoes.emailservice..` nem do `app`); `app`/`email-service` passam a proibir `..jogoacoes.configserver..`; `bannedDependencies` do enforcer nos três `pom.xml` | resolvida | Mesma regra de fronteira entre serviços da constitution e da 05-034. |

As recomendações desta tabela foram aceitas pela autora em 2026-10-13, registradas no commit
`decision:` que as marcou como resolvidas. `email-service` em `staging` ganha arquivo próprio
(`spec.md`, "Decisões em aberto", item 4).

## Estrutura de módulos/pacotes

```
pom.xml                                   # + <module>config-server</module>
config-server/
  pom.xml                                 # Boot 4.1.0, spring-cloud-config-server, security, enforcer
  Dockerfile
  src/main/java/dev/leilaalgarve/jogoacoes/configserver/
    ConfigServerApplication.java          # @EnableConfigServer
    SecurityConfig.java                   # HTTP Basic, tudo autenticado
  src/main/resources/application.yml      # porta 8888, perfil native, search-locations, usuário
  src/test/java/.../configserver/          # ConfigServerTest, ArchitectureTest
config-repo/
  jogo-acoes-docker.yml                   # ex-app/src/main/resources/application-docker.yml
  jogo-acoes-staging.yml                  # ex-application-staging.yml
  jogo-acoes-production.yml               # ex-application-production.yml
  email-service-docker.yml                # ex-email-service/.../application-docker.yml
  email-service-staging.yml               # novo, mesmo formato do production (decisão de 2026-10-13)
  email-service-production.yml            # ex-email-service/.../application-production.yml
app/                                      # + spring-cloud-starter-config, spring-retry;
                                          #   - application-{docker,staging,production}.yml
email-service/                            # + BOM spring-cloud-dependencies 2025.1.3,
                                          #   spring-cloud-starter-config, spring-retry; idem
docker-compose.yml                        # + config-server; CONFIG_SERVER_* em app/email-service
.github/workflows/ci.yml                  # sobe config-server; mvn -pl config-server verify
```

O `Dockerfile` do `app` e o do `email-service` copiam o `pom.xml` de todos os módulos do reator
(o Maven precisa lê-los); ganham a linha do `config-server/pom.xml`.

## Riscos e trade-offs

- **Perfil padrão no cliente.** Com `spring.profiles.default: docker` e nenhum perfil ativo, o
  Config Client precisa pedir `/{aplicação}/docker`, não `/{aplicação}/default`. Verificar de
  verdade na implementação (T006/T009); se não pedir, o Compose e o CI passam a definir
  `SPRING_PROFILES_ACTIVE=docker` explicitamente, e isso volta para esta tabela.
- **Cucumber e testes rodados à mão.** Com a suíte desligando o import (decisão acima), rodar os
  testes não exige o `config-server`; só o `ConfigServerClientIntegrationTest`. Rodar o `app` à
  mão fora do Compose (`mvn spring-boot:run`) passa a exigir o `config-server` de pé, como já
  exige o banco. Documentar no `README.md`.
- **Mais uma JVM.** Custo de memória aceito por ser requisito da disciplina; mitigado pelo limite
  de heap.
- **Valores servidos e versão.** Com `native`, o Config Server serve o que está no disco do
  container; não há histórico por *label*. O histórico é o do git deste repositório.
- **Compatibilidade do Spring Cloud Config com o Boot 4.1.** O train 2025.1.3 foi validado só com
  OpenFeign (05-034). Se o Config Server/Client não subir, parar e reportar, sem trocar a versão
  do Boot por conta própria.
- **Documentação da disciplina.** O texto da Etapa 3 do rascunho de entrega não é editado por esta
  spec (ver `docs/disciplina/CLAUDE.md`, thread própria).
