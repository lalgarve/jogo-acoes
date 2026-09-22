# Plan: Pipeline de e-mail ponta a ponta em desenvolvimento (`email-lambda` como consumidor vivo)

Traduz `spec.md` em decisões técnicas. Valida contra `memory/constitution.md`.

## Contexto técnico

Hoje:

- `app` publica em `jogo-acoes-email-commands` via `SqsEmailSender`
  (`app/src/main/resources/application.yml:43`, `application-docker.yml:33-38`), contra o
  `localstack` do `docker-compose.yml` (`SERVICES: sqs`).
- `email-lambda/pom.xml` já depende de `quarkus-amazon-ses` (cliente CDI-gerenciado + Dev
  Services) — mas **não** de `quarkus-amazon-sqs`; nada no módulo hoje fala com SQS, ele só
  recebe eventos já prontos (`SQSEvent`) via o *handler* `EmailSendHandler`.
- `EmailSendHandler.handleRequest` (implementa `RequestHandler<SQSEvent, Void>`) já é a peça
  reutilizável: recebe um `SQSEvent`, desserializa cada `SQSMessage.getBody()` como
  `EmailMessage` e chama `SesClient.sendEmail`. Não precisa de nenhuma mudança — só precisa ser
  chamado por outra coisa além do runtime real da AWS Lambda.
- `EmailSendHandlerTest` já prova que, contra LocalStack, o SES exige remetente verificado
  (achado documentado em `docs/context/iteracao-4.md`) e resolve isso chamando
  `sesClient.verifyEmailIdentity(...)` antes de exercitar o handler — mas isso só acontece
  dentro do teste, nunca num processo rodando de verdade.
- Confirmado via a documentação oficial (Quarkiverse Amazon Services,
  `docs.quarkiverse.io/quarkus-amazon-services`) as propriedades de configuração usadas abaixo:
  `quarkus.<serviço>.endpoint-override`, `quarkus.<serviço>.aws.region`,
  `quarkus.<serviço>.aws.credentials.type=static` +
  `quarkus.<serviço>.aws.credentials.static-provider.access-key-id`/`.secret-access-key`. Dev
  Services (usada nos testes) só entra em ação quando `endpoint-override` **não** está
  configurado — ou seja, definir essas propriedades no `docker-compose` não conflita com o
  comportamento dos testes, que continuam sem elas.

## Decisões de arquitetura

| Pergunta | Decisão | Status | Raciocínio |
|---|---|---|---|
| Como consumir a fila sem esperar por um *event source mapping* real da AWS | Um bean CDI novo (`EmailQueuePoller`) que, no `StartupEvent`, se habilitado por configuração, abre uma *thread* própria fazendo `receiveMessage` (long polling) num loop e chama `EmailSendHandler.handleRequest(...)` diretamente, em processo — sem passar pelo "Mock Event Server" HTTP que o `quarkus-amazon-lambda` expõe para `sam local` | resolvida | Mais simples que simular o protocolo da AWS Lambda Runtime API só para uso interno; o handler já é uma classe CDI comum, chamável direto. Mesmo `SQSEvent`/`SQSMessage` que `EmailSendHandlerTest` já monta manualmente (prova que a API permite isso sem depender do runtime real). |
| Ligar/desligar esse modo | Propriedade `email.dev-poller.enabled` (MicroProfile Config, `boolean`, default `false`) — variável de ambiente equivalente `EMAIL_DEV_POLLER_ENABLED` | resolvida | Mesmo padrão "presente mas inerte por padrão" já usado para o agente JaCoCo embutido na imagem `app` (spec 05-014) — só a sobreposição `docker-compose.email-lambda.yml` liga. |
| Cliente SQS no módulo `email-lambda` | Nova dependência `io.quarkiverse.amazonservices:quarkus-amazon-sqs` (mesmo BOM `quarkus-amazon-services-bom` já importado) — dá um `SqsClient` CDI-gerenciado + Dev Services automático em teste (mesmo mecanismo já usado para SES) | resolvida | Consistência com a extensão já usada para SES; sem essa dependência não existe `SqsClient` disponível para injetar no poller. |
| Configuração de endpoint/credenciais para o `docker-compose` (SQS **e** SES) | Variáveis de ambiente no serviço `email-lambda` da sobreposição: `QUARKUS_SQS_ENDPOINT_OVERRIDE`/`QUARKUS_SES_ENDPOINT_OVERRIDE=http://localstack:4566`, `QUARKUS_SQS_AWS_REGION`/`QUARKUS_SES_AWS_REGION=us-east-1`, `QUARKUS_SQS_AWS_CREDENTIALS_TYPE`/`QUARKUS_SES_AWS_CREDENTIALS_TYPE=static`, `QUARKUS_..._CREDENTIALS_STATIC_PROVIDER_ACCESS_KEY_ID`/`_SECRET_ACCESS_KEY=test` | resolvida | Mesmo raciocínio já usado do lado do `app` (`application-docker.yml`: credenciais fixas de LocalStack, região estática `us-east-1`) — mapeamento 1:1 de propriedade Quarkus para variável de ambiente (ponto/traço → `_`, maiúsculo), confirmado contra a documentação oficial. |
| Nome/valor da fila no `email-lambda` | Nova propriedade `email.queue-name=${EMAIL_QUEUE_NAME:jogo-acoes-email-commands}` em `application.properties`, mesmo valor default que `app` já usa | resolvida | Mesma convenção de sincronizar o nome "à mão" entre os dois lados, já usada em `docker/localstack/init/01-create-queue.sh` (comentário: "Name kept in sync by hand with EMAIL_QUEUE_NAME's default in app/..."). |
| SES habilitado no `localstack` do `docker-compose.yml` (base, não a sobreposição) | Muda `SERVICES: sqs` para `SERVICES: sqs,ses` no arquivo base | resolvida | Emulação de SES é inerte para quem não usa `email-lambda` (nada mais no projeto chama SES) — mesmo espírito "presente mas inofensivo" do agente JaCoCo. Colocar na sobreposição exigiria montar um segundo diretório de init scripts no mesmo caminho de container que o `docker-compose.yml` base já monta (`/etc/localstack/init/ready.d`), o que o Compose não mescla de forma limpa entre arquivos — mudar só o valor de uma variável de ambiente já existente é mais simples e não altera nenhum comportamento hoje observável. |
| Verificação do remetente no LocalStack | Novo script `docker/localstack/init/02-verify-ses-sender.sh` (mesmo diretório/mecanismo do `01-create-queue.sh`, roda uma vez quando o LocalStack fica pronto): `awslocal ses verify-email-identity --email-address no-reply@jogo-acoes.example` | resolvida | Resolve, no ambiente `docker-compose`, o mesmo problema que `EmailSendHandlerTest` já resolve dentro do teste (LocalStack aplica a mesma regra do SES real de exigir remetente verificado) — sem isso, a primeira mensagem processada pelo poller falharia com `MessageRejectedException`. Endereço mantido em sincronia à mão com `email.sender-address` em `email-lambda/src/main/resources/application.properties`, mesma convenção já usada para o nome da fila. |
| Imagem Docker do `email-lambda` | Novo `email-lambda/Dockerfile`, multi-stage, mesmo padrão do `Dockerfile` da raiz: builda só `-pl email-lambda -am package -DskipTests` (modo JVM/*fast-jar*, não nativo), copia `target/quarkus-app/` para a imagem final, `ENTRYPOINT java -jar quarkus-run.jar` | resolvida | Modo nativo é manual/fora de CI por decisão já tomada (Iteração 4) — replicar isso aqui evita builds de vários minutos num fluxo pensado para ser rápido de subir/derrubar em dev. |
| Nome/formato da sobreposição do `docker-compose` | `docker-compose.email-lambda.yml`, mesmo padrão de nomenclatura de `docker-compose.blackbox.yml` — comando `docker compose -f docker-compose.yml -f docker-compose.email-lambda.yml up` | resolvida | Consistência com a spec 05-014, que já estabeleceu esse padrão de sobreposição opcional. |
| Confirmar que uma mensagem foi processada, em dev | Nenhum mecanismo novo — a confirmação vem dos logs do container `email-lambda` (`docker compose logs -f email-lambda`) e do fato de a mensagem sumir da fila (`awslocal sqs get-queue-attributes --queue-url ... --attribute-names ApproximateNumberOfMessages`, documentado no `README.md`) | resolvida | Suficiente para o objetivo desta spec (ver o pipeline funcionar); uma ferramenta de inspeção visual da fila é maior que o necessário aqui — ver "Fora de escopo" em `spec.md`. |

## Estrutura de módulos/pacotes

- `email-lambda/pom.xml` (modificado) — nova dependência `io.quarkiverse.amazonservices:
  quarkus-amazon-sqs` (sem versão explícita, gerenciada pelo `quarkus-amazon-services-bom` já
  importado).
- `email-lambda/src/main/java/dev/leilaalgarve/jogoacoes/email/lambda/EmailQueuePoller.java`
  (novo) — bean CDI, observa `StartupEvent`; se `email.dev-poller.enabled=true`, injeta
  `SqsClient`/`EmailSendHandler`, resolve a URL da fila (`getQueueUrl` a partir de
  `email.queue-name`) e roda um loop de `receiveMessage` (long polling, `waitTimeSeconds=20`)
  numa *thread* dedicada; monta um `SQSEvent`/`SQSMessage` por mensagem recebida (corpo bruto),
  chama `EmailSendHandler.handleRequest`, e deleta a mensagem (`deleteMessage`) só em caso de
  sucesso.
- `email-lambda/src/main/resources/application.properties` (modificado) — novas propriedades:
  `email.dev-poller.enabled=false` (default) e `email.queue-name=
  ${EMAIL_QUEUE_NAME:jogo-acoes-email-commands}`.
- `email-lambda/Dockerfile` (novo) — build multi-stage descrito acima.
- `docker-compose.yml` (modificado) — `localstack.environment.SERVICES` de `sqs` para `sqs,ses`
  (única mudança neste arquivo).
- `docker-compose.email-lambda.yml` (novo) — serviço `email-lambda`: `build:
  email-lambda/Dockerfile` (contexto na raiz do repo, mesmo motivo do `Dockerfile` do `app` —
  o reator precisa do `pom.xml` raiz), `depends_on: localstack` (saudável), as variáveis de
  ambiente `QUARKUS_SQS_*`/`QUARKUS_SES_*`/`EMAIL_DEV_POLLER_ENABLED` listadas na tabela acima.
- `docker/localstack/init/02-verify-ses-sender.sh` (novo) — verificação do remetente, descrita
  acima.
- `README.md` (modificado) — nova seção curta, mesmo estilo da seção "Ambiente de testes
  blackbox" (spec 05-014): como subir (`docker compose -f docker-compose.yml -f
  docker-compose.email-lambda.yml up`), como confirmar que uma mensagem foi processada, e que
  isso é só para desenvolvimento local — não muda nada sobre a implantação real da Lambda.

## Riscos e trade-offs

- **`EmailQueuePoller` roda numa *thread* própria fora do ciclo de vida gerenciado normal de
  requisições CDI** — precisa tratar corretamente o encerramento (`docker compose down` envia
  `SIGTERM`; a *thread* precisa observar isso e sair do loop, não travar o *shutdown* do
  container). Mitigado usando uma *thread* interrompível padrão e checando `Thread
  .currentThread().isInterrupted()` a cada iteração — sem biblioteca nova.
- **Duplicação de responsabilidade entre o poller de dev e o futuro *event source mapping* da
  AWS real**: os dois fazem "pegar mensagem da fila, invocar o handler", mas por mecanismos
  diferentes (*polling* explícito vs. infraestrutura gerenciada da AWS). Aceito deliberadamente
  — não vale a pena esperar a conta AWS existir só para ter um único caminho; o handler
  (`EmailSendHandler`) continua sendo a única lógica de negócio, o poller é só um adaptador de
  invocação alternativo, isolado num arquivo próprio.
- **Mudança na imagem `localstack` do `docker-compose.yml` base** (`SERVICES: sqs` →
  `sqs,ses`) é uma mudança num arquivo usado por todo mundo, não só por quem quer testar
  `email-lambda`. Mitigado por ser aditivo e não observável por quem não chama SES — nenhum
  teste/fluxo existente depende do valor atual de `SERVICES`.
- **`quarkus-amazon-sqs` habilita Dev Services de SQS também nos testes do módulo
  `email-lambda`**, já que a extensão ativa isso por padrão quando não há `endpoint-override`
  configurado (igual `quarkus-amazon-ses` já faz para SES). Nenhum teste hoje usa `SqsClient`,
  então isso não deveria mudar nada observável — mas vale confirmar rodando a suíte
  (`mvn -pl email-lambda -am test`) depois de adicionar a dependência, exatamente para checar
  esse efeito colateral.
