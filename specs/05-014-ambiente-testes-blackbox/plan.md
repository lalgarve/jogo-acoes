# Plan: Ambiente de testes blackbox (captcha sempre válido)

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

## Decisões de arquitetura

| Pergunta | Decisão | Status | Raciocínio |
|---|---|---|---|
| Como trocar a implementação de verificação | Extrair uma interface `CaptchaVerifier` (só `boolean verify(String token)` — o método que `EntryRequestService` de fato usa) de `CaptchaService`; a classe atual é renomeada para `AltchaCaptchaVerifier` (implementa a interface, mantém `createChallenge`/`encodeToken`, que são detalhes do ALTCHA, não da interface); nova classe `AlwaysPassCaptchaVerifier` (`verify` sempre retorna `true`, nenhuma dependência). Seleção via `@ConditionalOnProperty(name = "captcha.verifier", havingValue = "altcha", matchIfMissing = true)` / `havingValue = "always-pass"`, mesmo mecanismo de `email.sender`. | resolvida | Mesmo padrão já usado e testado no projeto (`EmailSender`); `createChallenge`/`encodeToken` continuam existindo (usados pelos testes Cucumber que exercitam o captcha de verdade no perfil padrão) sem forçar `AlwaysPassCaptchaVerifier` a implementá-los sem sentido. |
| Nome do perfil | `blackbox`, empilhado sobre `docker` (`SPRING_PROFILES_ACTIVE=docker,blackbox`) — nunca sozinho. `app/src/main/resources/application-blackbox.yml` só define `captcha.verifier: always-pass`. | resolvida | Perfis Spring compõem por ordem (o último listado tem prioridade em chave conflitante); um arquivo mínimo, só com a chave que muda, deixa claro que `blackbox` é um *acréscimo* a `docker`, não um ambiente paralelo completo (reaproveita datasource, fila etc. de `docker`). |
| Como subir esse perfil combinado via `docker-compose` | Novo arquivo `docker-compose.blackbox.yml`, sobreposição (`docker compose -f docker-compose.yml -f docker-compose.blackbox.yml up`) que só redefine `SPRING_PROFILES_ACTIVE: docker,blackbox` no serviço `app` — `docker-compose.yml` original fica intocado. | resolvida | Mecanismo padrão do Docker Compose pra variações de ambiente (múltiplos `-f`), evita duplicar `db`/`localstack`/`adminer` num arquivo separado ou arriscar quebrar o uso normal de `docker-compose up` (CI, desenvolvimento). |
| Como impedir ativação em `staging`/`production` | Estrutural, não só documentado: `application-blackbox.yml` só existe como perfil adicional que precisa ser explicitamente listado em `SPRING_PROFILES_ACTIVE` — nenhum `application-staging.yml`/`application-production.yml` referencia ou ativa `blackbox` automaticamente, e o `docker-compose.blackbox.yml` só se aplica a quem rodar esse `docker-compose` localmente (staging/produção não usam `docker-compose`, são geridos por outra equipe — ver `README.md`, seção Ambientes). Nenhuma verificação de código adicional (ex. checagem de `Environment` em runtime) — o mecanismo de composição de perfis já é a barreira. | resolvida | Consistente com como o projeto já trata outros perfis sensíveis (`production` não herda nada de `docker`/`staging` por acidente) — não precisa de um mecanismo novo só pra este caso. |
| Como documentar rodar com/sem JaCoCo | `jacoco-maven-plugin` já reconhece a propriedade padrão `jacoco.skip` em todas as suas execuções (`prepare-agent`/`report`/`check`) sem precisar de mudança no `pom.xml` — `mvn test -Djacoco.skip=true` roda a suíte sem instrumentação; `mvn test` (ou `mvn verify`, que também aplica o piso de cobertura) já gera o relatório em `target/site/jacoco/index.html` normalmente. README ganha essa explicação, sem mudança de configuração. | resolvida | Capacidade que o plugin já tem — documentar em vez de reimplementar. |

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
- `docker-compose.blackbox.yml` (novo, raiz do repositório).
- `README.md` (modificado) — seção sobre o ambiente `blackbox` e instruções de JaCoCo.

## Riscos e trade-offs

- **Um `SPRING_PROFILES_ACTIVE` digitado errado poderia, em teoria, incluir `blackbox` em
  qualquer ambiente** — mitigado por nunca ser o padrão em lugar nenhum (`matchIfMissing` só
  existe para `altcha`, o valor seguro) e por `staging`/`production` serem geridos por outra
  equipe, fora do alcance de quem sobe o `docker-compose` local.
- **`AlwaysPassCaptchaVerifier` aceita literalmente qualquer coisa, incluindo token vazio** —
  aceito deliberadamente: o objetivo é remover o captcha do caminho do teste blackbox por
  completo, não criar uma versão "mais fácil, mas ainda assim uma prova de trabalho real".
