# Spec: Os testes não sobem nada

**Status:** rascunho
**Issue:** [#193](https://github.com/lalgarve/jogo-acoes/issues/193)
**Iteração:** iteration-5

## Resumo

Nenhum teste automatizado sobe nada: nem as dependências (banco, LocalStack, DNS,
`config-server`), nem o próprio serviço que ele testa. Tudo é levantado antes, fora do Maven, e
o teste só conversa com o que já está rodando: chama o serviço por HTTP (ou, na `email-lambda`,
pela fila) e confere o resultado no banco, no LocalStack ou na resposta. Vale para `app`,
`email-service`, `email-lambda` e `config-server`.

## Motivação

- **Regra da Leila (2026-10-20): "Os testes não sobem NADA."** A spec
  [05-028](../05-028-testes-exigem-docker-real/spec.md) tirou o Testcontainers do `app` e do
  `email-service`, porque deixar o Maven subir containers dava problema. Esta spec leva a mesma
  regra até o fim: o teste também não sobe o próprio serviço.
- **O que ainda sobe alguma coisa hoje** (levantado em `development`, 2026-10-20):

  | Módulo | Como sobe | Classes |
  |---|---|---|
  | `app` | `@SpringBootTest` (o próprio `app`, inclusive a suíte Cucumber), `@DataJpaTest` (parte do contexto) e `SpringApplicationBuilder` | 22 com `@SpringBootTest` e mais 3 só com `@DataJpaTest` (`LogRepositoryTest`, `CurrentUserServiceTest`, `UserServiceTest`) |
  | `email-service` | `@SpringBootTest` (inclusive a suíte Cucumber) e `SpringApplicationBuilder` | 7 |
  | `email-lambda` | `@QuarkusTest` (sobe a aplicação Quarkus) e o Quarkus Dev Services, que sobe um LocalStack por Testcontainers durante o `mvn test` | 1 (`EmailSendHandlerTest`) |
  | `config-server` | `@SpringBootTest` e `SpringApplicationBuilder` | 2 |

- **Problema concreto que isso resolve:** no branch da spec 05-046, a suíte do `email-service`
  sobe a própria instância enquanto o CI mantém outra no ar (o container, para os testes do
  `app`). As duas leem o mesmo banco, e o publicador do container publica na fila dele os
  envios criados pelos testes: o teste de 50 envios achou 28. Com um só `email-service`, o que
  está rodando, isso não acontece.
- **O teste passa a exercitar o que roda de verdade:** a configuração com que o serviço sobe, a
  imagem ou o jar empacotado, a ordem de subida. Hoje cada suíte tem um `application.yml` de
  teste que substitui o do serviço (`src/test/resources/application.yml`), e um teste pode passar
  com uma configuração que o serviço nunca usa.

## Cenários (comportamento esperado)

Não há `.feature` novo: a mudança é na forma de testar, não no comportamento dos serviços. Os
`.feature` existentes continuam sendo o contrato de aceite; muda a forma como os passos os
executam. Verificação:

1. **Ambiente de pé:** com tudo levantado antes (Compose ou `scripts/cloud-up.sh`), `mvn verify`
   roda as suítes dos quatro módulos e passa, sem subir nenhum contexto Spring, aplicação Quarkus
   ou container.
2. **Ambiente fora do ar:** sem o ambiente de pé, as suítes falham com erro de conexão; nenhuma é
   pulada (constitution, "Testes exigem a infraestrutura de pé").
3. **Nada sobe:** uma regra de arquitetura falha se uma classe de teste usar `@SpringBootTest`,
   `@DataJpaTest`, `@WebMvcTest`, `@QuarkusTest`, `SpringApplicationBuilder`, Testcontainers ou
   Dev Services.
4. **Cobertura:** o piso de 80% de linhas do `app` continua valendo no CI, medido no serviço
   rodando.

## Requisitos funcionais

- **Testes de integração e cenários Cucumber** chamam o serviço já rodando:
  - `app` em `http://localhost:8080/api`, `email-service` em `http://localhost:8082/api`,
    `config-server` em `http://localhost:8888`;
  - `email-lambda`: o teste põe a mensagem na fila de envio do LocalStack e confere o efeito (o
    e-mail no SES do LocalStack, a mensagem na DLQ), com a função implantada pelos scripts de
    init do LocalStack.
- **Conferência de estado** direto na infraestrutura, sem passar pela aplicação: o banco por
  JDBC, o LocalStack pela API da AWS. Os passos não usam mais repositórios ou serviços da
  aplicação (`@Autowired`).
- **Testes unitários** (sem contexto Spring, sem servidor, sem rede) continuam como estão: não
  sobem nada.
- **Uma regra de arquitetura (ArchUnit) por módulo** impede que um teste volte a subir alguma
  coisa (cenário 3).
- **O ambiente de teste é um só:** o mesmo que se levanta para a verificação manual, com o
  overlay `blackbox` (ver "Decisões em aberto", item 2).
- **CI:** levanta o ambiente inteiro antes, roda as suítes, coleta a cobertura e derruba.
- **Constitution:** a regra entra em "Testes exigem a infraestrutura de pé" e substitui o que a
  05-028 dizia sobre `@SpringBootTest`.

## Requisitos não-funcionais

- Sem mock de serviço: o teste conversa com o serviço real e a infraestrutura real (constitution,
  "Testes: preferir real a fake").
- Testes independentes entre si: cada cenário limpa ou isola os próprios dados (endereços únicos,
  limpeza por JDBC), porque o serviço e o banco são compartilhados por toda a suíte.
- O tempo total do CI não pode crescer sem limite: a subida do ambiente acontece uma vez só.

## Fora de escopo

- Os testes que a suíte Python `blackbox-tests/` já tem (testam o `app` por fora e já não sobem
  nada); a suíte só ganha, conforme a decisão 6, os testes de configuração especial.
- Mudar o comportamento de qualquer serviço.
- O caderno de testes manual.

## Decisões resolvidas

- **Testes que dependem de subir um serviço com outra configuração vão para Python** (Leila,
  2026-10-20). São os que não dá para fazer com um só serviço já rodando com a configuração
  normal:
  - duas instâncias do `email-service` publicando ao mesmo tempo (o teste dos 50 envios, branch
    da 05-046);
  - SES fora do ar (`SesUnavailableIntegrationTest` e parte do `DeadLetterConsumerIntegrationTest`);
  - `config-server` inacessível (`ConfigServerClientIntegrationTest`, nos dois serviços);
  - Swagger UI desligado (`SwaggerUiSwitchedOffTest`, branch da 05-049);
  - os tempos curtos de nova tentativa do publicador (05-046);
  - `ProductionProfileSuppressesLoggingAspectsTest` (`app` com o perfil `production`);
  - a parte `startAppWith` do `EmailServiceClientIntegrationTest` (um segundo `app` com o
    endereço ou a chave do `email-service` errados, que não pode subir);
  - `ConfigServerCredentialsTest` (`config-server` sem usuário e senha, que não pode subir).

  Não entram, porque viram testes comuns contra o ambiente levantado antes:
  `BlackboxProfileIntegrationTest` (o ambiente de teste já usa o overlay `blackbox`),
  `RecipientDomainRejectionIntegrationTest`, o resto do `EmailServiceClientIntegrationTest`
  (apontam para o `email-service` em `localhost:8082`, o que está rodando) e `ConfigServerTest`
  (usa as credenciais do ambiente). `HmacPepperStartupCheckTest` é teste unitário e não sobe
  nada. Os valores do `application.yml` de teste do `email-service` (filas de teste, tempo máximo
  do DNS, limites das listas, esperas do publicador) e o limite de 10 MB do
  `ImportDisposableDomainsJobTest` dependem da decisão em aberto 2.

  Nesses testes, **o próprio teste em Python levanta e derruba os containers** de que precisa,
  com a configuração do cenário, e chama o serviço por fora. A regra "os testes não sobem nada"
  continua valendo para as suítes Java (Maven): é nelas que nada sobe.
- **Nenhum teste depende de URL fora do nosso controle** (Leila, 2026-10-20). As listas de
  e-mail temporário dos testes são servidas pelo S3 do LocalStack, publicadas pelo próprio teste.
- **No ambiente de teste, o repositório do GitHub nem é criado** (Leila, 2026-10-20). Hoje a
  migration `V6__create_disposable_domain_tables.sql` cadastra, em todos os ambientes, a lista
  `disposable-email-domains` do GitHub (spec 05-048), e a suíte Cucumber a apaga antes dos
  cenários. Esse cadastro passa a existir só em `staging` e `production`. Efeitos:
  - a V6 muda de conteúdo: um banco já criado precisa ser recriado (`docker compose down -v`); o
    sistema está em pré-produção (constitution), não há dado a preservar;
  - o caderno de testes do Serviço de E-mail, parte do documento já entregue, conta com esse
    repositório como o de número `1`; rodado de novo no `docker`, o resultado muda. O caderno
    ganha uma nota, como a da spec 05-049, sem reescrever os roteiros;
  - a spec 05-048 ganha uma nota de que o repositório inicial passou a ser só de
    `staging`/`production`.

## Decisões em aberto

Cada uma tem uma recomendação, detalhada em `plan.md`; falta a decisão da Leila. (A decisão 1,
sobre os testes que precisam de outra configuração, foi resolvida; ver "Decisões resolvidas".)

2. **Configuração do ambiente de teste.** Alguns testes precisam de valores diferentes dos do
   uso normal: captcha sempre aceito (já é o overlay `blackbox`), esperas curtas de nova
   tentativa no `email-service` (05-046), relógio do jogo. Opções: (a) estender o overlay
   `blackbox`; (b) um overlay só de testes automatizados. Recomendação: (a), um ambiente só.
3. **Cobertura.** Opções: (a) agente JaCoCo em cada serviço rodando (como o `app` já faz no
   overlay `blackbox`, porta 6300), com o relatório e o piso calculados a partir do *dump* depois
   das suítes; (b) piso só para o que é medido hoje e o resto informativo. Recomendação: (a),
   com o mesmo piso de 80% no `app`.
4. **Onde ficam os testes.** Opções: (a) continuam em `src/test` de cada módulo, rodados pelo
   Maven, só que sem subir nada; (b) módulos de teste separados, como `blackbox-tests/`.
   Recomendação: (a); o `src/test` não entra no artefato de produção (constitution, "Código de
   teste/dev nunca dentro da aplicação").
5. **Ordem com o trabalho em andamento.** As specs 05-046 (branch
   `claude/project-thread-kx436t`) e 05-049 (branch `claude/swagger-ui-email-service`) têm testes
   no formato antigo. Opções: (a) segurar as duas até esta spec ser implementada e reescrever os
   testes delas já no formato novo; (b) mesclar as duas antes, como estão, e converter depois.
   Recomendação: (a) para a 05-046, que hoje nem passa no CI; para a 05-049 a Leila decide.
6. **Onde fica a suíte Python dos testes de configuração especial.** Opções: (a) dentro de
   `blackbox-tests/`, numa pasta própria, aproveitando o `behave`/`pytest` e o cliente gerado que
   já existem; (b) uma suíte Python nova, só para esses testes. Recomendação: (a), um projeto
   Python de testes só.
