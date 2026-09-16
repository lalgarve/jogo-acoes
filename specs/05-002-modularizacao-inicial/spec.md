# Spec: Modularização inicial do `app/` por domínio

**Status:** rascunho
**Issue:** [#46](https://github.com/lalgarve/jogo-acoes/issues/46)
**Iteração:** iteration-5

## Resumo

Primeiro passo da reorganização do `app/` por domínio/funcionalidade (Etapa 1 da disciplina —
ver `docs/context/alinhamento-projeto-disciplina.md`), em vez de por camada técnica. Move as
classes existentes para sete módulos: `link`, `competition`, `login`, `log`, `email`,
`captcha`, `common`. A única mudança de código é criar as pastas dos módulos e ajustar
pacotes, imports e demais referências — nenhuma lógica muda. Testar o projeto ao final.

## Motivação

Hoje o `app/` está organizado por camada técnica (`web/`, `service/`, `repository/`,
`domain/`), o que a Etapa 1 da disciplina não aceita como critério atendido. Este é o primeiro
passo de correção: mover código para módulos por domínio, sem ainda mexer no desenho interno
de cada um — isso fica pra specs futuras (ex. a 05-003, que desacopla especificamente o módulo
`link`).

## Cenários (comportamento esperado)

Não aplicável — mudança estrutural, sem comportamento novo. Critério de aceite: suíte
`.feature` e testes unitários/integração passam 100%, sem nenhuma asserção alterada — só a
localização do código muda.

## Requisitos funcionais

Pacote base do módulo `app`: `dev.leilaalgarve.jogoacoes` (ver spec 05-001) — abreviado como
`{base}` no resto deste documento.

**Nova estrutura de pastas:**

```
{base}
├── link/         # LoginLink, LoginSession e o mecanismo de token/link mágico
├── competition/  # Competition, Participation, CompetitionService, EntryRequestService,
│                 # PlayerManagementService, controllers correspondentes
├── login/        # User, Role, UserRole, LoginService (parte de autenticação/sessão,
│                 # não o mecanismo de link em si), LoginController, SecurityConfig
├── log/          # Log, LogType, LogRepository, AuditLogService
├── email/        # EmailSender, StubEmailSender, SqsEmailSender, EmailContentRenderer,
│                 # SentEmail, EmailRequest/EmailMessage/RenderedEmail
├── captcha/      # CaptchaService e a integração com o provedor de captcha (ALTCHA)
└── common/       # ScenarioWorld/fixtures de teste compartilhadas (testsupport) e classes de
                  # infraestrutura genérica sem domínio próprio
```

- Cada classe migra para o módulo correspondente ao seu domínio, não à sua camada técnica —
  ex.: `CompetitionService`, `CompetitionsController`, `Competition` (entidade) e
  `CompetitionRepository` ficam todos dentro de `competition/`, não espalhados em
  `service/`/`web/`/`domain/`/`repository/` como hoje.
- Pacotes DTO gerados pelo `openapi-generator-maven-plugin` (`{base}.api.*`) não são tocados
  por esta spec — continuam como estão, gerados a partir de `docs/openapi.yaml`.
- `SecurityConfig` migra para `login/` (é configuração de autenticação/sessão, mesmo
  raciocínio que já coloca `LoginService`/`LoginController` lá); fixtures de teste
  compartilhadas (`ScenarioWorld`/`testsupport`) e qualquer classe de infraestrutura genérica
  sem domínio próprio migram para o módulo novo `common/` — ver "Decisões em aberto".

### Convenção de sub-pacotes internos (`client`/`dto`/`exception`)

Confirmado nesta sessão contra o repositório de referência da disciplina
([`elberthmoraes-prof/desenvolvimento-avancado-com-spring-e-microsservicos-26e3-26e3`](https://github.com/elberthmoraes-prof/desenvolvimento-avancado-com-spring-e-microsservicos-26e3-26e3),
módulo `academico-service`), para uso dentro de cada módulo de domínio à medida que a
necessidade aparecer — **não é um requisito desta spec aplicar já em todos os módulos**, é uma
convenção a seguir aos poucos, módulo a módulo, conforme o trabalho avançar (ex.: `client/` só
passa a existir quando um módulo ganhar integração via OpenFeign, Etapa 2):

- **`client/`**: interface `@FeignClient` + um *gateway* que encapsula a chamada remota e
  traduz exceções do Feign (`FeignException.NotFound`, `RetryableException` etc.) em exceções
  de domínio, mais o DTO de resposta do serviço remoto.
- **`dto/`**: os DTOs de request/resposta do próprio módulo (`record`, sem sufixo "Dto" no
  nome da classe — o pacote já deixa isso implícito). Ex.: `competition.dto.CreateRequest`,
  não `CreateRequestDto`.
- **`exception/`**: só criado dentro de um módulo quando esse módulo tiver **duas ou mais**
  exceções próprias — uma exceção única fica solta na raiz do pacote do módulo. Existe também
  um pacote `exception/` **global**, na raiz de `{base}`, com o `GlobalExceptionHandler` e o
  formato padrão de resposta de erro — esse pacote global importa tipos de exceção de todos os
  módulos, é o único lugar do projeto onde isso é esperado.
- Nomes de pacote/classe continuam em inglês, como o restante do projeto — a convenção do
  repositório de referência (em português) é adaptada só na estrutura, não no idioma.

Observação: o próprio repositório de referência não aplica essa regra de forma 100%
consistente (um dos módulos de exemplo mantém duas exceções na raiz do pacote, sem subpacote
`exception/`, mesmo tendo mais de uma) — seguimos a regra como descrita acima por ser a mais
clara de aplicar, não por ser universal naquele repositório.

## Requisitos não-funcionais

Nenhum além de build e suíte verdes ao final.

## Fora de escopo

- Qualquer redesenho de acoplamento **entre** módulos — nesta spec o código simplesmente muda
  de pasta/pacote, mantendo as mesmas chamadas diretas que existem hoje (ex.: `LoginLink`
  continua com FK direta pra `Participation`, mesmo já estando fisicamente em `link/`). O
  desacoplamento do módulo `link` especificamente é a spec 05-003.
- Módulos Maven de verdade (reactor multi-módulo) — isso aqui é só pacote dentro do módulo
  `app/` existente, não um novo artefato Maven.
- Qualquer nova regra de negócio.
- Aplicar a convenção `client`/`dto`/`exception` retroativamente em todos os módulos — como
  descrito acima, isso é incremental, não um entregável desta spec.

## Decisões em aberto

- ~~Onde ficam classes que ainda não pertencem claramente a nenhum dos módulos de domínio:
  `SecurityConfig`, `ScenarioWorld`/fixtures de teste compartilhadas (`testsupport`), classes
  de infraestrutura genérica?~~ **Resolvido (sessão 2026-09-16):** `SecurityConfig` migra
  para `login/`. `ScenarioWorld`/`testsupport`/infraestrutura genérica sem domínio próprio
  ganham um módulo novo, `common/` — ver "Nova estrutura de pastas" acima. (`CaptchaService`
  já não estava mais nesta lista — módulo próprio, `captcha/`.)
- Dependência de ordem com a spec 05-001 (renomear pacote base) — ver "Decisões em aberto"
  daquela spec.
- `PlayerManagementService`/`EntryRequestService` foram colocados em `competition/` nesta
  proposta por operarem sobre `Participation`; confirmar se faz sentido antes de mover, já que
  também lidam com o fluxo de convite/entrada que toca `link/`.
