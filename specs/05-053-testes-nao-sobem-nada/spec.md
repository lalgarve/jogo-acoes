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

- A suíte Python `blackbox-tests/`, que já testa o `app` por fora.
- Mudar o comportamento de qualquer serviço.
- O caderno de testes manual.

## Decisões em aberto

Cada uma tem uma recomendação, detalhada em `plan.md`; falta a decisão da Leila.

1. **Testes que hoje dependem de subir o serviço com uma configuração diferente.** Não dá para
   reproduzir com um serviço só, já rodando com a configuração normal:
   - `config-server` inacessível (o serviço não pode subir);
   - SES fora do ar (`SesUnavailableIntegrationTest` e parte do `DeadLetterConsumerIntegrationTest`, este no branch da 05-046);
   - Swagger UI desligado (`SwaggerUiSwitchedOffTest`, no branch da 05-049);
   - duas instâncias publicando ao mesmo tempo (`EmailQueuePublisherConcurrencyTest`, branch da 05-046);
   - `HmacPepperStartupCheckTest`, `ProductionProfileSuppressesLoggingAspectsTest` e afins, que
     sobem o serviço com outro perfil.

   Opções: (a) instâncias extras levantadas antes, cada uma com a sua configuração (ex.: um
   `email-service` com o SES numa porta fechada); (b) virar verificação manual registrada na
   spec de origem; (c) virar teste unitário da classe envolvida, sem subir nada.
   Recomendação: decidir caso a caso entre (a) e (c), e (b) só para o que nenhum dos dois cobre.
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
