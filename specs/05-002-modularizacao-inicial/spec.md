# Spec: Modularização inicial do `app/` por domínio

**Status:** rascunho
**Issue:** —
**Iteração:** iteration-5

## Resumo

Primeiro passo da reorganização do `app/` por domínio/funcionalidade (Etapa 1 da disciplina —
ver `docs/context/alinhamento-projeto-disciplina.md`), em vez de por camada técnica. Move as
classes existentes para cinco módulos: `link`, `competition`, `login`, `log`, `email`. A única
mudança de código é criar as pastas dos módulos e ajustar pacotes, imports e demais
referências — nenhuma lógica muda. Testar o projeto ao final.

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
│                 # não o mecanismo de link em si), LoginController
├── log/          # Log, LogType, LogRepository, AuditLogService
└── email/        # EmailSender, StubEmailSender, SqsEmailSender, EmailContentRenderer,
                  # SentEmail, EmailRequest/EmailMessage/RenderedEmail
```

- Cada classe migra para o módulo correspondente ao seu domínio, não à sua camada técnica —
  ex.: `CompetitionService`, `CompetitionsController`, `Competition` (entidade) e
  `CompetitionRepository` ficam todos dentro de `competition/`, não espalhados em
  `service/`/`web/`/`domain/`/`repository/` como hoje.
- Pacotes DTO gerados pelo `openapi-generator-maven-plugin` (`{base}.api.*`) não são tocados
  por esta spec — continuam como estão, gerados a partir de `docs/openapi.yaml`.
- Classes que não pertencem claramente a nenhum dos cinco módulos (`CaptchaService`/ALTCHA,
  configuração de segurança, fixtures de teste compartilhadas) — ver "Decisões em aberto".

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

## Decisões em aberto

- Onde ficam classes que não pertencem claramente a nenhum dos cinco módulos: `CaptchaService`
  /integração ALTCHA, `SecurityConfig`, `ScenarioWorld`/fixtures de teste compartilhadas
  (`testsupport`), classes de infraestrutura genérica (se houver). Proposta: um módulo
  `shared`/`common`, ou deixá-las na raiz do pacote — a definir antes de implementar.
- Dependência de ordem com a spec 05-001 (renomear pacote base) — ver "Decisões em aberto"
  daquela spec.
- `PlayerManagementService`/`EntryRequestService` foram colocados em `competition/` nesta
  proposta por operarem sobre `Participation`; confirmar se faz sentido antes de mover, já que
  também lidam com o fluxo de convite/entrada que toca `link/`.
