# Plan: Ambiente de testes blackbox (captcha sempre válido + cobertura JaCoCo da aplicação)

Traduz `spec.md` em decisões técnicas. Valida contra `memory/constitution.md`.

## Contexto técnico

`CaptchaService` (`captcha/CaptchaService.java`) é hoje uma única classe concreta (`@Service`,
sem interface) que faz três coisas: cria o desafio ALTCHA (`createChallenge`), codifica um
token de teste (`encodeToken`, usado só por `RequestCompetitionEntrySteps` para montar um
token válido nos cenários Cucumber) e verifica um token (`verify`, único método chamado em
código de produção — `EntryRequestService.confirmEntry`/`requestOrConfirmEntry`, ao validar
`captchaToken`). Não existe endpoint que emita o desafio pra um cliente real (sem frontend
ainda) — `captchaToken` hoje só é alcançável resolvendo o algoritmo ALTCHA à parte, exatamente
o que testes blackbox não devem precisar fazer.

O módulo `email` já resolve um problema formalmente idêntico — trocar a implementação de uma
interface por variável de ambiente/perfil — via `EmailSender` (interface) +
`StubEmailSender`/`SqsEmailSender` (`@ConditionalOnProperty(name = "email.sender", ...)`). Este
plan replica esse padrão para o captcha.

**Cobertura de código de uma aplicação já em execução é um problema diferente do que
`jacoco-maven-plugin` resolve hoje** (versão 0.8.15, `app/pom.xml`). O plugin funciona
instrumentando a JVM que o *próprio Maven/Surefire lança* para rodar os testes (`prepare-agent`
grava um `.exec` que o `report`/`check` leem logo em seguida, tudo dentro da mesma execução do
Maven) — não existe gancho aí para um processo `java -jar app.jar` de vida longa, rodando num
container, sendo exercitado por requisições HTTP externas (Swagger UI manual, ou a suíte Python
da spec 05-015) ao longo de uma sessão inteira. O projeto JaCoCo publica, à parte do plugin
Maven, dois artefatos standalone para exatamente esse caso: `org.jacoco:org.jacoco.agent` (com
classificador `runtime`) — o mesmo agente, mas empacotado para ser anexado via `-javaagent` a
qualquer JVM, não só uma lançada pelo Maven — e `org.jacoco:org.jacoco.cli` (classificador
`nodeps`) — uma ferramenta de linha de comando com os comandos `dump` (extrai os dados de
execução acumulados de um agente rodando em modo `tcpserver`, sem parar a JVM) e `report` (gera
HTML/XML a partir de um `.exec` + as classes/fontes compiladas).

**Duas lacunas que impedem o ambiente de ser usável de fora, mesmo com captcha resolvido.**
(1) Nenhuma linha de `app_user`/`user_role` com papel `ADMINISTRATOR` existe sem passar por
`UserMother` (só teste) ou uma inserção manual — confirmado, não existe `POST` de usuário/
promoção de papel em `docs/openapi.yaml`. (2) `SentEmail` (`email/SentEmail.java`,
`sent_email`) já grava `email`/`link`/`template`/`sentAt` a cada envio — tanto
`StubEmailSender` quanto `SqsEmailSender` chamam `SentEmailRecorder.record(...)` (confirmado
lendo as duas classes) — mas `SentEmailRepository` só tem `findByLink` (usado internamente); não
existe rota que devolva isso por HTTP. As duas lacunas usam o mesmo tipo de solução já aplicada
ao captcha: alguma coisa que só existe quando `blackbox` está ativo, nunca em outro perfil.

## Decisões de arquitetura

| Pergunta | Decisão | Status | Raciocínio |
|---|---|---|---|
| Como trocar a implementação de verificação de captcha | Extrair uma interface `CaptchaVerifier` (só `boolean verify(String token)` — o método que `EntryRequestService` de fato usa) de `CaptchaService`; a classe atual é renomeada para `AltchaCaptchaVerifier` (implementa a interface, mantém `createChallenge`/`encodeToken`, que são detalhes do ALTCHA, não da interface); nova classe `AlwaysPassCaptchaVerifier` (`verify` sempre retorna `true`, nenhuma dependência). Seleção via `@ConditionalOnProperty(name = "captcha.verifier", havingValue = "altcha", matchIfMissing = true)` / `havingValue = "always-pass"`, mesmo mecanismo de `email.sender`. | resolvida | Mesmo padrão já usado e testado no projeto (`EmailSender`); `createChallenge`/`encodeToken` continuam existindo (usados pelos testes Cucumber que exercitam o captcha de verdade no perfil padrão) sem forçar `AlwaysPassCaptchaVerifier` a implementá-los sem sentido. |
| Nome do perfil | `blackbox`, empilhado sobre `docker` (`SPRING_PROFILES_ACTIVE=docker,blackbox`) — nunca sozinho. `app/src/main/resources/application-blackbox.yml` só define `captcha.verifier: always-pass`. | resolvida | Perfis Spring compõem por ordem (o último listado tem prioridade em chave conflitante); um arquivo mínimo, só com a chave que muda, deixa claro que `blackbox` é um *acréscimo* a `docker`, não um ambiente paralelo completo (reaproveita datasource, fila etc. de `docker`). |
| Como subir esse perfil combinado via `docker-compose` | Novo arquivo `docker-compose.blackbox.yml`, sobreposição (`docker compose -f docker-compose.yml -f docker-compose.blackbox.yml up`) que redefine `SPRING_PROFILES_ACTIVE: docker,blackbox` e acrescenta `JAVA_TOOL_OPTIONS` (ver linha do agente JaCoCo abaixo) no serviço `app`, mais a porta de *dump* — `docker-compose.yml` original fica intocado, e listas como `ports` são concatenadas (não substituídas) pelo Compose entre arquivo base e sobreposição, então a porta `8080` continua publicada normalmente junto da nova. | resolvida | Mecanismo padrão do Docker Compose pra variações de ambiente (múltiplos `-f`), evita duplicar `db`/`localstack`/`adminer` num arquivo separado ou arriscar quebrar o uso normal de `docker-compose up` (CI, desenvolvimento). |
| Como impedir ativação em `staging`/`production` | Estrutural, não só documentado: `application-blackbox.yml` só existe como perfil adicional que precisa ser explicitamente listado em `SPRING_PROFILES_ACTIVE`, e `JAVA_TOOL_OPTIONS` com o `-javaagent` só é setado dentro de `docker-compose.blackbox.yml` — nenhum `application-staging.yml`/`application-production.yml` referencia `blackbox`, e nenhum dos dois é gerido via `docker-compose` (outra equipe, ver `README.md`, seção Ambientes). Nenhuma verificação de código adicional (ex. checagem de `Environment` em runtime) — o mecanismo de composição de perfis/compose já é a barreira. | resolvida | Consistente com como o projeto já trata outros perfis sensíveis (`production` não herda nada de `docker`/`staging` por acidente) — não precisa de um mecanismo novo só pra este caso. |
| Onde o agente/CLI do JaCoCo vivem | Embutidos na **mesma** imagem Docker de sempre (`Dockerfile`), sempre presentes mas inertes — `mvn dependency:copy` (executions novas em `app/pom.xml`, fase `package`) copiam `org.jacoco:org.jacoco.agent:0.8.15:jar:runtime` → `target/jacoco/jacocoagent.jar` e `org.jacoco:org.jacoco.cli:0.8.15:jar:nodeps` → `target/jacoco/jacococli.jar` (mesma versão 0.8.15 já usada pelo `jacoco-maven-plugin`, evita descompasso de formato de `.exec`); o estágio final do `Dockerfile` copia os dois pra `/app/`. Nenhum `ENTRYPOINT`/`CMD` novo — a ativação é só a variável `JAVA_TOOL_OPTIONS` (lida automaticamente por qualquer `java`, sem precisar reescrever o comando de start). | resolvida | Uma imagem só pra manter (sem Dockerfile paralelo pra um ambiente de uso ocasional); custo de ter os dois jars sempre presentes é mínimo (poucos MB) e não tem efeito nenhum enquanto `JAVA_TOOL_OPTIONS` não estiver setada — só `docker-compose.blackbox.yml` faz isso. |
| Como o agente é configurado | `JAVA_TOOL_OPTIONS=-javaagent:/app/jacocoagent.jar=output=tcpserver,address=*,port=6300,includes=dev.leilaalgarve.jogoacoes.*` no serviço `app` de `docker-compose.blackbox.yml`, porta `6300` publicada (`"6300:6300"`, concatenada à `8080:8080` já existente). `output=tcpserver` mantém o agente escutando pra comandos `dump` a qualquer momento, sem precisar parar a JVM; `includes` restringe a instrumentação ao pacote da aplicação (mesmo espírito do `<excludes>` já usado pelo `jacoco-maven-plugin` pra não contar `api`/`org.openapitools` gerados). | resolvida | Modo padrão do JaCoCo pra "coletar cobertura de uma aplicação de vida longa, sem reiniciar" — documentado pelo próprio projeto JaCoCo para exatamente este cenário. |
| Como gerar o relatório depois de uma sessão de teste | Script dedicado (`scripts/blackbox-coverage.sh`, raiz do repositório) que roda, em sequência: (1) `java -jar app/target/jacoco/jacococli.jar dump --address localhost --port 6300 --destfile target/jacoco-blackbox.exec` (contra o container já em execução, porta publicada no host); (2) `java -jar app/target/jacoco/jacococli.jar report target/jacoco-blackbox.exec --classfiles app/target/classes --sourcefiles app/src/main/java --html target/site/jacoco-blackbox`. Pressupõe `app/target/classes` local compilado a partir do mesmo commit rodando no container (mesma checkout) — documentado no `README.md`. Diretório de saída (`target/site/jacoco-blackbox/`) propositalmente diferente do relatório da suíte Java (`target/site/jacoco/`). | resolvida | Um comando, sem exigir lembrar dos dois passos/flags do `jacococli` toda vez; caminho de saída deliberadamente separado do relatório da suíte JUnit/Cucumber pra nunca colidir/sobrescrever. |
| Como garantir um administrador no ambiente | `blackbox/BlackboxDataSeeder.java` (`ApplicationRunner`, `@Component @Profile("blackbox")`) — na inicialização, busca por e-mail fixo (`admin@blackbox.local`, documentado no README); se não existir, cria o `User` (`registered = true`) + `UserRole` com papel `ADMINISTRATOR`, mesma lógica de `UserMother.administrator()` (só que em código principal, gated por perfil). Idempotente (checa antes de criar) — subir/derrubar o container repetidamente não duplica nem falha. | resolvida | Reaproveita a lógica que os testes Cucumber já usam pra montar um administrador; um `ApplicationRunner` com `@Profile` é o mesmo mecanismo de "só existe em `blackbox`" já usado pro captcha, sem precisar de migration/seed de banco separado (que afetaria o perfil `docker` inteiro, inclusive CI). |
| Como ler o link de um e-mail enviado, de fora | Novo endpoint `GET /blackbox/last-email?email={endereço}` (`blackbox/BlackboxController.java`, `@RestController @Profile("blackbox")`), lendo `SentEmailRepository.findTopByEmailOrderBySentAtDesc(email)` (método novo) e devolvendo `{ link, template, sentAt }` do envio mais recente pra aquele endereço; `404` se nada foi enviado ainda. `blackbox/BlackboxSecurityConfigContributor.java` libera a rota (`permitAll`) — não faz sentido exigir sessão pra ler o e-mail de um jogador que ainda nem tem uma. **Não entra em `docs/openapi.yaml`**: é andaime de teste, não contrato de produto — a suíte Python (spec 05-015) chama essa rota direto via HTTP, fora do cliente gerado a partir do contrato. | resolvida | Reaproveita a tabela/gravação que já existe (`SentEmail`); manter fora do contrato oficial evita que uma rota de teste vaze pro cliente gerado como se fosse API de produto, e evita qualquer fricção com `OpenApiRoutesConsistencyTest`/`OpenApiRolesConsistencyTest` (que só veem rotas do perfil em que rodam, nunca `blackbox`). |

## Estrutura de módulos/pacotes

- `app/src/main/java/dev/leilaalgarve/jogoacoes/captcha/CaptchaVerifier.java` (novo) —
  interface, um método (`verify`).
- `app/src/main/java/dev/leilaalgarve/jogoacoes/captcha/AltchaCaptchaVerifier.java`
  (renomeado de `CaptchaService.java`) — implementação real, `createChallenge`/`encodeToken`
  preservados.
- `app/src/main/java/dev/leilaalgarve/jogoacoes/captcha/AlwaysPassCaptchaVerifier.java`
  (novo) — implementação de bypass.
- `app/src/main/java/dev/leilaalgarve/jogoacoes/competition/EntryRequestService.java`
  (modificado) — passa a depender de `CaptchaVerifier`, não da classe concreta.
- `app/src/test/java/dev/leilaalgarve/jogoacoes/competition/steps/RequestCompetitionEntrySteps.java`
  (modificado) — referência à classe renomeada (`AltchaCaptchaVerifier`), já que os cenários
  Cucumber continuam exercitando o captcha de verdade (o perfil de teste padrão nunca ativa
  `blackbox`).
- `app/src/main/resources/application-blackbox.yml` (novo).
- `app/pom.xml` (modificado) — duas execuções novas de `maven-dependency-plugin` (`copy`,
  fase `package`) trazendo `jacocoagent.jar`/`jacococli.jar` pra `target/jacoco/`.
- `Dockerfile` (modificado) — estágio final copia os dois jars do JaCoCo pra `/app/`, sempre.
- `docker-compose.blackbox.yml` (novo, raiz do repositório).
- `scripts/blackbox-coverage.sh` (novo) — dump + report num comando.
- `app/src/main/java/dev/leilaalgarve/jogoacoes/blackbox/BlackboxDataSeeder.java` (novo) —
  garante o administrador conhecido, só em `blackbox`.
- `app/src/main/java/dev/leilaalgarve/jogoacoes/blackbox/BlackboxController.java` (novo) —
  `GET /blackbox/last-email`, só em `blackbox`.
- `app/src/main/java/dev/leilaalgarve/jogoacoes/blackbox/BlackboxSecurityConfigContributor.java`
  (novo) — libera a rota acima (`permitAll`); satisfaz o `ArchitectureTest` (todo
  `@RestController` precisa de um `SecurityConfigContributor` no mesmo pacote).
- `app/src/main/java/dev/leilaalgarve/jogoacoes/email/SentEmailRepository.java` (modificado) —
  novo método `findTopByEmailOrderBySentAtDesc(String email)`.
- `README.md` (modificado) — seção sobre o ambiente `blackbox` (captcha, administrador semeado,
  como ler o link de um e-mail, como gerar o relatório de cobertura da aplicação exercitada
  externamente), deixando claro que o relatório é independente do de sempre da suíte Java.

## Riscos e trade-offs

- **Um `SPRING_PROFILES_ACTIVE` digitado errado poderia, em teoria, incluir `blackbox` em
  qualquer ambiente** — mitigado por nunca ser o padrão em lugar nenhum (`matchIfMissing` só
  existe para `altcha`, o valor seguro) e por `staging`/`production` serem geridos por outra
  equipe, fora do alcance de quem sobe o `docker-compose` local.
- **`AlwaysPassCaptchaVerifier` aceita literalmente qualquer coisa, incluindo token vazio** —
  aceito deliberadamente: o objetivo é remover o captcha do caminho do teste blackbox por
  completo, não criar uma versão "mais fácil, mas ainda assim uma prova de trabalho real".
- **`app/target/classes` local precisa corresponder ao que está rodando no container** para o
  relatório de cobertura fazer sentido — se alguém gerar o relatório contra um checkout
  desatualizado em relação à imagem rodando, os números batem errado (linhas cobertas apontando
  pro código errado). Mitigado documentando isso explicitamente no `README.md`; sem verificação
  automática de que os dois batem (fora de escopo — ambiente de uso manual/pontual).
- **O agente em modo `tcpserver` acumula cobertura entre múltiplos `dump`s** (não reseta
  sozinho) — é o comportamento padrão do JaCoCo e é o que se quer aqui (uma sessão de teste
  inteira, com vários cliques/requisições, deve somar no mesmo relatório); reiniciar o container
  é a forma de zerar entre sessões, também documentado.
- **`GET /blackbox/last-email` sem autenticação lê o e-mail de qualquer endereço** — divulgação
  de informação inaceitável fora deste contexto; aceito pelas mesmas razões do bypass de
  captcha (pré-produção, `blackbox` nunca exposto publicamente) e protegido pela mesma barreira
  estrutural (`@Profile("blackbox")` — a classe do controller nem chega a virar bean fora desse
  perfil).
- **Só o último e-mail por endereço é recuperável** — se um teste disparar dois e-mails
  seguidos pro mesmo endereço antes de ler o primeiro (ex. reenvio de convite), o primeiro link
  fica inacessível pelo endpoint. Aceitável para os fluxos de hoje (um e-mail por vez, lido
  logo em seguida); um endpoint que liste o histórico é o tipo de melhoria que fica para a
  "caixa postal mais completa" citada em `spec.md`.
- **`BlackboxDataSeeder` roda em todo start do container** — custo desprezível (uma consulta
  `findByEmail` a mais na inicialização), mas só existe enquanto `blackbox` estiver ativo, então
  não afeta o tempo de subida de nenhum outro perfil.
