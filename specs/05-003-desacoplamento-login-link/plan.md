# Plan: Desacoplar o módulo `link` dos seus consumidores

Traduz `spec.md` em decisões técnicas. Valida contra `memory/constitution.md`.

**Status: implementado** (sessão 2026-09-16, branch `claude/jogo-acoes-iteracao-5-5hloak`) —
ver `tasks.md` para o checklist T001–T019, todos concluídos. Esta versão do documento já
incorpora os achados feitos durante a implementação (não só o planejamento prévio).

## Contexto técnico

- Módulo `link` já existe fisicamente como resultado da spec 05-002 (`{base}.link`, mesmo
  módulo Maven `app/`) — esta spec redesenha o que já está lá dentro, não move arquivo de novo.
- Stack disponível sem dependência nova: Jackson (`ObjectMapper`, já usado pelo Spring MVC nos
  controllers) para serializar/desserializar apenas o campo `extra` (`Map<String, String>`)
  de/para o `extraJson` gravado em `LinkRecord` — `userId` e `email` são colunas próprias da
  entidade, não passam por serialização; Spring para injeção de `List<LinkHandler>` no
  `LinkRouter`. **Achado na implementação**: este projeto roda Spring Boot 4.1/Jackson 3 — o
  `ObjectMapper` autoconfigurado é `tools.jackson.databind.ObjectMapper` (não
  `com.fasterxml.jackson.databind.ObjectMapper`); `JacksonException` (pacote
  `tools.jackson.core`) já é uma `RuntimeException`, então `LinkService` não precisa de
  try/catch ao serializar/desserializar `extra`.

### Achados da leitura de código desta sessão (planejamento)

Lidos `LoginService`, `LoginController`, `LoginLink`, `EntryRequestService`,
`PlayerManagementService`, `CompetitionService` (estado em `master`) linha a linha. Três
achados mudam o desenho em relação ao que estava ilustrado em `spec.md`:

1. **Só existem dois "formatos" de link hoje, não três.** `LoginLink.participation == null`
   (login avulso, criado só por `LoginService.requestLoginLink`) ou
   `LoginLink.participation != null` (ligado a uma competição) — e esse segundo formato é
   criado por **três** call sites diferentes (`EntryRequestService.requestEntry`,
   `PlayerManagementService.sendInviteEmail`, `CompetitionService.decideInviteEmailTiming`),
   mas os três produzem exatamente o mesmo formato de link e o mesmo comportamento no
   consumo — convite e pedido de entrada pública não se distinguem depois de o link existir.
   Ou seja: **dois `LinkHandler`s, não três nem um por serviço "chamador"**.
2. **`EntryRequestService.confirmEntry` não usa `LoginLink`/token nenhum** — é uma chamada
   autenticada direta (jogador já logado confirmando entrada). Fora do escopo desta spec.
3. **O caso "ligado a uma competição, sem conta ainda" é de duas fases**, não uma:
   `consumeLoginLink` devolve 202 ("registro necessário") sem marcar o link como usado, e um
   **segundo** request (`completeRegistration(token, name)`) — no mesmo token, ainda não
   consumido — é quem de fato cria o usuário, vincula a `Participation` e estabelece a sessão.
   A interface `LinkHandler` com um único `handle(payload)` não comporta isso: precisa de um
   segundo método opcional para a fase de conclusão.

### Achados feitos durante a implementação (sessão 2026-09-16)

Ler o código na hora de implementar revelou mais três pontos que o planejamento acima não
tinha antecipado:

4. **`consumeLoginLink`'s "já autenticado" atalho precisa de um terceiro método na
   interface.** O código original checa `isAuthenticated()` **antes** de checar se o link já
   foi usado, e nesse caso monta o redirecionamento usando o **usuário da sessão atual**, não
   necessariamente o dono do link — sem tocar em `usedAt`/sem estabelecer sessão de novo (ver
   cenário "Already logged-in player clicks a login link originally used on a different
   device" em `login.feature`). Isso não cabia em `consume`/`complete` sem duplicar lógica de
   redirecionamento em cada handler, então `LinkHandler` ganhou um terceiro método,
   `LinkOutcome alreadyAuthenticated(Long authenticatedUserId, LinkPayload payload)`, chamado
   por `LinkService.consume` antes de qualquer checagem de uso/expiração específica de handler.
5. **`LoginController` não foi movido para `link/` como `LinkController`.** A ideia original
   (`LinkController` em `link/`, implementando `LoginApi`) esbarra em `requestLoginLink`: essa
   operação (buscar usuário por e-mail, invalidar links anteriores, decidir se envia e-mail) é
   inteiramente uma responsabilidade do `login`, sem nada genérico pra despachar — e um
   controller fisicamente em `link/` não pode chamar código de `login` sem violar o requisito
   de direção de dependência da própria spec. Resolvido mantendo `LoginController` em `login/`
   (implementando `LoginApi`, mesmas rotas/contrato de sempre): `consumeLoginLink`/
   `completeRegistration` viram adaptadores finos sobre `LinkService` (genérico); só
   `requestLoginLink` continua com lógica própria, porque criar um login avulso é mesmo um
   assunto só de `login`.
6. **`LoginSession` também precisou perder a FK pra `User`.** Ela mora em `link/` desde a
   05-002 (guarda "dispositivo"/sessão), mas seu campo `user` (`@ManyToOne User user`) violava
   o mesmo requisito de dependência que motivou tirar a FK de `LinkRecord` — `link` não pode
   importar `login.User`. Resolvido trocando por `Long userId` puro, igual a `LinkRecord`; ver
   "Decisão de arquitetura" abaixo.

## Decisões de arquitetura

| Pergunta | Decisão | Status | Raciocínio |
|---|---|---|---|
| Formato do DTO com campos extra por implementação | `LinkPayload` único (sem hierarquia de subclasses): `Long userId`, `String email`, `Map<String, String> extra` — vive em `{base}.link.dto`, sem sufixo "Dto" no nome, seguindo a convenção de sub-pacotes definida na spec 05-002 | resolvida | Decisão da autora (sessão 2026-09-15): um DTO único evita que `LinkService`/`LinkRouter` precisem conhecer/fazer downcast de tipos concretos por implementação — o único contrato que `link` enxerga é sempre o mesmo tipo. O custo (fraco tipamento de `extra`) é aceito — ver "Riscos e trade-offs". Nome ajustado na sessão 2026-09-16 para aplicar a convenção `dto/` já documentada em 05-002. |
| Onde vive a chave de serviço como constante | Sem catálogo central no módulo `link` — cada `LinkHandler` declara sua própria constante de chave e se registra no `LinkRouter` só por implementar a interface | resolvida | Decisão da autora: um catálogo central em `link` reintroduziria exatamente o acoplamento que esta spec elimina (`link` teria que conhecer os nomes/chaves de todo consumidor existente). |
| Migração de dados de `LoginLink`/`Participation` (FK) para `LinkRecord` (JSON) | Não se aplica — nenhuma migração de dados existentes é necessária | resolvida | Decisão da autora: o sistema ainda não entrou em produção, não há linha de `LoginLink` real para preservar. |
| Serialização do DTO | Jackson `ObjectMapper` (Jackson 3, `tools.jackson.*` — ver "Contexto técnico"), sem dependência nova, aplicado só ao campo `extra` (ver "Persistência do link" abaixo) | resolvida | Já é a biblioteca de serialização usada pelo Spring MVC no resto do projeto. |
| Como o `LinkRouter` é populado | Spring injeta `List<LinkHandler>` no construtor do `LinkRouter`, que monta o `Map<String, LinkHandler>` a partir da chave que cada implementação expõe (método `key()` da interface) | resolvida | Qualquer `LinkHandler` novo só precisa ser um `@Component` implementando a interface — nenhum lugar central lista os handlers manualmente, consistente com "sem catálogo" acima. |
| Colisão de chave entre duas implementações | `LinkRouter` falha ao subir o contexto Spring se duas implementações declararem a mesma chave, em vez de uma sobrescrever a outra silenciosamente no mapa. **Além disso**, um teste dedicado (`LinkRouterKeyUniquenessTest`) verifica isso explicitamente: constrói o `LinkRouter` com as implementações reais de `LinkHandler` e checa que cada `key()` é não-nula e única, sem depender de subir a aplicação inteira. | **implementada** | Sem catálogo central (decisão acima), nada mais detectaria a colisão — falhar cedo no boot troca um bug silencioso de produção por um erro de inicialização óbvio. O teste dedicado dá o mesmo sinal de forma rápida e explícita, sem esperar um `@SpringBootTest` completo. |
| Mapeamento exato dos fluxos atuais para implementações concretas de `LinkHandler` | **Dois handlers**: `LoginLinkHandler` (chave `"login"`, módulo `login`) para o login avulso; `CompetitionLinkHandler` (chave `"competition-entry"`, módulo `competition`) para convite **e** pedido de entrada pública — mesmo handler. `EntryRequestService.confirmEntry` fica de fora — não usa link. | resolvida | Ver "Achados da leitura de código" acima. |
| Consumo em duas fases (link ligado a participação, sem conta ainda) | `LinkHandler` ganha um segundo método, com implementação padrão que recusa: `default LinkOutcome complete(LinkPayload payload, Map<String,String> extra) { throw new UnsupportedOperationException(...); }`, além de `LinkOutcome consume(LinkPayload payload)`. `LinkRecord` só é marcado como usado quando um dos dois devolve o resultado final. `LoginLinkHandler` nunca implementa `complete`; `CompetitionLinkHandler` implementa as duas. | **implementada** | É o único jeito de manter a interface única + mapa cobrindo um fluxo que hoje é de fato dois requests HTTP sobre o mesmo token. |
| "Já autenticado" ao consumir um link (achado 4 acima) | `LinkHandler` ganha um **terceiro** método, obrigatório (sem `default`): `LinkOutcome alreadyAuthenticated(Long authenticatedUserId, LinkPayload payload)`. `LinkService.consume` chama esse método (via `LinkSessionService.currentAuthenticatedUserId()`) **antes** de checar `usedAt`, sem marcar o link como usado nem estabelecer sessão de novo. | **implementada** | É o único jeito de preservar o atalho do código original (login.feature: "Already logged-in player clicks a login link originally used on a different device") sem duplicar lógica de redirecionamento por handler nem vazar a checagem de autenticação pra fora de `link`. |
| Persistência do link: `userId`/`email` como colunas ou dentro do JSON | `LinkRecord` grava `userId` e `email` como **colunas próprias** da entidade, não dentro de um JSON opaco. Só o campo `extra` do `LinkPayload` vira JSON, num campo `extraJson` separado. Nenhuma das duas colunas tem FK. | **implementada** | Reduz a perda de integridade referencial que gravar o payload inteiro como JSON causaria — `userId`/`email` continuam consultáveis/indexáveis diretamente — sem reintroduzir a FK pra `User` que motivou a mudança. |
| Retorno de `LinkService.create` (achado durante a implementação) | `create(serviceKey, payload)` devolve um `LinkCreationResult(Long id, String token)`, não só o token | resolvida | Os três call sites de criação (login avulso, convite, pedido de entrada) precisam do `id` numérico pra auditoria (`AuditLogService.record(..., relatedObjectId, ...)`) e do `token` pra montar a URL do e-mail — um `String` só não bastava. |
| Onde vive a lógica de estabelecer sessão (`SecurityContext` + gravar `LoginSession`) após qualquer consumo bem-sucedido | **Resolvida**: interface `LinkSessionService` em `link/` (`Optional<Long> currentAuthenticatedUserId()`; `void establish(Long userId, String token)`), implementada por `LoginLinkSessionService` em `login/` — mesmo padrão de inversão de dependência do `LinkHandler`. `LinkService` chama a interface, nunca a implementação. | **implementada** | A mecânica (`SecurityContext`, limite de dispositivos, `LoginSession`) é genérica, mas monta a `Authentication` a partir de `User`/papéis — que só `login` conhece. `link` depende só da interface; `login` pode depender de volta em `link` (`LoginSession`/`LoginSessionRepository`), nunca o contrário — a mesma direção de dependência já usada para `LinkHandler`. |
| `LoginSession.user` também precisou virar `Long userId` (achado 6 acima) | `LoginSession` (mora em `link/`) troca `@ManyToOne User user` por `Long userId`, sem FK — mesmo tratamento de `LinkRecord.userId`. `LoginSessionRepository`/chamadores atualizados (`findByUserIdAndEndedAtIsNullOrderByCreatedAtAsc`). | **implementada** | `LoginSession` vivendo em `link/` com uma FK Java pra `login.User` violava o mesmo requisito de dependência que motivou o redesenho de `LinkRecord` — passou despercebido no planejamento porque `LoginSession` não é o `LinkRecord` em si, mas mora no mesmo módulo e está sujeita à mesma regra. |

## Estrutura de módulos/pacotes (estado final, implementado)

```
{base}.link
├── LinkController?           # NÃO EXISTE — ver achado 5: LoginController (login/) faz esse
│                              #   papel; consumeLoginLink/completeRegistration delegam pra
│                              #   LinkService, requestLoginLink fica local a login/.
├── LinkService                # create(serviceKey, payload) -> LinkCreationResult(id, token)
│                              # consume(token): já-autenticado? handler.alreadyAuthenticated();
│                              #   senão handler.consume(), marca usedAt se não-pendente e
│                              #   chama LinkSessionService.establish
│                              # complete(token, extra): idem, via handler.complete()
│                              # invalidateActiveLinksFor(userId): usado só por login avulso
├── LinkRouter                 # Map<String, LinkHandler> montado a partir de List<LinkHandler> injetada
├── LinkHandler                 # interface: String key(); LinkOutcome consume(LinkPayload);
│                              #   LinkOutcome alreadyAuthenticated(Long, LinkPayload);
│                              #   default LinkOutcome complete(LinkPayload, Map<String,String>)
├── LinkSessionService          # interface: Optional<Long> currentAuthenticatedUserId();
│                              #   void establish(Long userId, String token)
├── LinkOutcome                 # pending() | authenticated(userId, redirectData: Map<String,String>)
├── LinkRecord                  # entidade JPA: id, token, serviceKey, userId, email, extraJson,
│                              #   emailSentAt, expiresAt, usedAt, invalidatedAt — sem FK
├── LinkRecordRepository
├── LinkCreationResult          # record: Long id, String token
├── LoginSession                # userId (Long, sem FK), linkRecord (FK pra LinkRecord), deviceId, ...
├── LoginSessionRepository
├── LoginLinkInvalidException / LoginLinkUsedOnAnotherDeviceException  # nomes mantidos (05-002)
└── dto
    └── LinkPayload             # record: Long userId, String email, Map<String,String> extra

{base}.login
├── LoginController            # implements LoginApi (mesmas rotas de sempre) -- ver achado 5
├── LoginLinkHandler            # implements LinkHandler, key() = "login" -- só consume(), sem complete()
└── LoginLinkSessionService     # implements LinkSessionService

{base}.competition
└── CompetitionLinkHandler      # implements LinkHandler, key() = "competition-entry"
                                 # consume(): payload.userId() != null? autentica : pendente
                                 # complete(payload, extra): cria User, atribui role PLAYER,
                                 #   vincula Participation (extra["participationId"]),
                                 #   status = IN_COMPETITION
```

- Cada `LinkHandler` concreto mora no módulo que o implementa (`login`, `competition`), nunca
  em `link` — é o que inverte a direção de dependência descrita na spec. `LoginLinkHandler`/
  `CompetitionLinkHandler` importam `{base}.link.dto.LinkPayload` e `{base}.link.LinkHandler`/
  `LinkOutcome` — os únicos tipos de `link` que cruzam a fronteira do módulo.
- `LinkService` monta `LinkRecord` a partir do `LinkPayload` copiando `userId`/`email`
  diretamente para as colunas correspondentes e serializando **só** `extra` para `extraJson`.
- `LinkPayload.extra` carrega os campos específicos de cada implementação como pares
  texto-texto — `CompetitionLinkHandler` usa `participationId` (constante
  `CompetitionLinkHandler.PARTICIPATION_ID_EXTRA_KEY`). `link` nunca olha dentro de `extra`.
- **Duplicação removida** (era "achado incidental, fora do escopo" no planejamento, virou
  efeito colateral real da implementação): `EntryRequestService.requestEntry`,
  `PlayerManagementService.sendInviteEmail` e `CompetitionService.decideInviteEmailTiming`
  agora chamam todos `linkService.create("competition-entry", payload)` em vez de cada um
  construir seu próprio `LoginLink` manualmente.
- `PlayerManagementService.removePlayer` não precisa mais apagar links antes de apagar a
  `Participation` — sem FK de `LinkRecord` pra `Participation`, a ordem deixou de importar
  (comentário atualizado no código).

## Riscos e trade-offs

- **Perda de integridade referencial parcial**: `LinkRecord.userId`/`LoginSession.userId` não
  têm FK pra `User`, e o conteúdo de `extraJson` (ex. `participationId`) continua opaco — um
  valor inválido só é percebido quando o `LinkHandler`/`LinkSessionService` tenta usá-lo.
  Aceito nesta spec. **Mitigação implementada**: `LoginLinkHandler.consume` rejeita
  `payload.userId() == null` com `IllegalArgumentException`; `CompetitionLinkHandler`
  valida `extra["participationId"]` ausente/não-numérico da mesma forma, além de tratar
  participação inexistente como link inválido (`LoginLinkInvalidException`). Ambos cobertos
  por `LoginLinkHandlerTest`/`CompetitionLinkHandlerTest`.
- **`extra` fracamente tipado**: mitigado pela validação explícita acima, em vez de deixar uma
  `NullPointerException`/`NumberFormatException` genérica estourar no meio da lógica de negócio.
- **Sem catálogo de chaves + falha no boot por colisão**: coberto por
  `LinkRouterKeyUniquenessTest`, que também prova a colisão real via `assertThatThrownBy`.
- **Método `complete` como `default` que lança exceção / chamar `complete` sem um `consume`
  pendente antes**: coberto por `LinkServiceTest.completeFailsWhenTheLinkIsAlreadyUsed` —
  prova que `complete` num token já usado falha com `LoginLinkInvalidException`, sem tocar
  `LinkRouter`/`LinkSessionService` (`verifyNoInteractions`).
- **Nenhum mecanismo impede um `LinkHandler` em `competition` de importar tipos de `login`
  diretamente** (contornando o módulo `link`) — continua fora do escopo, candidato a um teste
  ArchUnit numa spec futura de qualidade arquitetural. Note que isso é **esperado e permitido**
  nesta spec (`competition`/`login` podem depender um do outro livremente, como já faziam
  antes); o que a spec proíbe é só `link` depender de qualquer um dos dois.
- **`docs/diagrams/sequencia.md` ficou desatualizado** (ainda descreve `LoginService`/
  `LoginLinkRepository`/`LoginLink`, removidos nesta implementação) — `der.md` e `classes.md`
  já foram atualizados, mas os quatro diagramas de sequência de `sequencia.md` não; é um
  trabalho de documentação maior (quatro fluxos completos), registrado aqui como pendência
  em vez de refeito às pressas nesta sessão.
