# Plan: Desacoplar o módulo `link` dos seus consumidores

Traduz `spec.md` em decisões técnicas. Valida contra `memory/constitution.md`.

## Contexto técnico

- Módulo `link` já existe fisicamente como resultado da spec 05-002 (`{base}.link`, mesmo
  módulo Maven `app/`) — esta spec redesenha o que já está lá dentro, não move arquivo de novo.
- Stack disponível sem dependência nova: Jackson (`ObjectMapper`, já usado pelo Spring MVC nos
  controllers) para serializar/desserializar apenas o campo `extra` (`Map<String, String>`)
  de/para o `extraJson` gravado em `LinkRecord` — `userId` e `email` são colunas próprias da
  entidade, não passam por serialização; Spring para injeção de `List<LinkHandler>` no
  `LinkRouter`.

### Achados da leitura de código desta sessão

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
   A interface `LinkHandler` com um único `handle(dto)` não comporta isso: precisa de um
   segundo método opcional para a fase de conclusão.

## Decisões de arquitetura

| Pergunta | Decisão | Status | Raciocínio |
|---|---|---|---|
| Formato do DTO com campos extra por implementação | `LinkDto` único (sem hierarquia de subclasses): `Long userId`, `String email`, `Map<String, String> extra` | resolvida | Decisão da autora (sessão 2026-09-15): um DTO único evita que `LinkService`/`LinkRouter` precisem conhecer/fazer downcast de tipos concretos por implementação — o único contrato que `link` enxerga é sempre o mesmo tipo. O custo (fraco tipamento de `extra`) é aceito — ver "Riscos e trade-offs". |
| Onde vive a chave de serviço como constante | Sem catálogo central no módulo `link` — cada `LinkHandler` declara sua própria constante de chave e se registra no `LinkRouter` só por implementar a interface | resolvida | Decisão da autora: um catálogo central em `link` reintroduziria exatamente o acoplamento que esta spec elimina (`link` teria que conhecer os nomes/chaves de todo consumidor existente). |
| Migração de dados de `LoginLink`/`Participation` (FK) para `LinkRecord` (JSON) | Não se aplica — nenhuma migração de dados existentes é necessária | resolvida | Decisão da autora: o sistema ainda não entrou em produção, não há linha de `LoginLink` real para preservar. |
| Serialização do DTO | Jackson `ObjectMapper`, sem dependência nova, aplicado só ao campo `extra` (ver "Persistência do link" abaixo) | resolvida | Já é a biblioteca de serialização usada pelo Spring MVC no resto do projeto. |
| Como o `LinkRouter` é populado | Spring injeta `List<LinkHandler>` no construtor do `LinkRouter`, que monta o `Map<String, LinkHandler>` a partir da chave que cada implementação expõe (método `key()` da interface) | resolvida | Qualquer `LinkHandler` novo só precisa ser um `@Component` implementando a interface — nenhum lugar central lista os handlers manualmente, consistente com "sem catálogo" acima. |
| Colisão de chave entre duas implementações | `LinkRouter` falha ao subir o contexto Spring se duas implementações declararem a mesma chave, em vez de uma sobrescrever a outra silenciosamente no mapa. **Além disso**, um teste dedicado (`LinkRouterKeyUniquenessTest` ou similar) verifica isso explicitamente: constrói o `LinkRouter` com todas as implementações reais de `LinkHandler` presentes no contexto e checa que cada `key()` é não-nula e única, sem depender de subir a aplicação inteira. | resolvida | Sem catálogo central (decisão acima), nada mais detectaria a colisão — falhar cedo no boot troca um bug silencioso de produção por um erro de inicialização óbvio. Decisão da autora (sessão 2026-09-15): o teste dedicado dá o mesmo sinal de forma rápida e explícita, sem esperar um `@SpringBootTest` completo, e documenta o requisito "toda implementação tem chave definida e não duplicada" da spec. |
| Mapeamento exato dos fluxos atuais para implementações concretas de `LinkHandler` | **Dois handlers**: `LoginLinkHandler` (chave `"login"`, módulo `login`) para o login avulso; `CompetitionLinkHandler` (chave `"competition-entry"`, módulo `competition`) para convite **e** pedido de entrada pública — mesmo handler, já que os três call sites de criação produzem o mesmo formato e o mesmo comportamento de consumo. `EntryRequestService.confirmEntry` fica de fora — não usa link. | resolvida | Ver "Achados da leitura de código desta sessão" acima. |
| Consumo em duas fases (link ligado a participação, sem conta ainda) | `LinkHandler` ganha um segundo método, com implementação padrão que recusa: `default LinkOutcome complete(LinkDto dto, Map<String,String> extra) { throw new UnsupportedOperationException(...); }`, além de `LinkOutcome consume(LinkDto dto)`. `LinkRecord` só é marcado como usado quando um dos dois devolve o resultado final — `consume` pode devolver um `LinkOutcome` do tipo "pendente" sem consumir o registro. `LoginLinkHandler` nunca implementa `complete` (login avulso é sempre uma fase só); `CompetitionLinkHandler` implementa as duas. | resolvida | É o único jeito de manter a interface única + mapa (nenhuma decisão anterior muda) cobrindo um fluxo que hoje é de fato dois requests HTTP sobre o mesmo token. |
| Persistência do link: `userId`/`email` como colunas ou dentro do JSON | `LinkRecord` grava `userId` e `email` como **colunas próprias** da entidade, não dentro de um JSON opaco. Só o campo `extra` do `LinkDto` (o que sobra de específico de cada implementação) vira JSON, num campo `extraJson` separado. | resolvida | Decisão da autora (sessão 2026-09-15): reduz a perda de integridade referencial que gravar o DTO inteiro como JSON causaria (ver "Riscos e trade-offs") — `userId`/`email` continuam consultáveis/indexáveis diretamente, e o mecanismo genérico de `link` segue sem precisar conhecer nada sobre o significado de `extra`. |
| Onde vive a lógica de estabelecer sessão (`SecurityContext` + gravar `LoginSession`) após qualquer consumo bem-sucedido | Em aberto | em aberto | É genérica (idêntica nos dois handlers), o que sugere pertencer a `link` como utilitário chamado pelos handlers — mas `LoginSession` foi alocada em `link` na spec 05-002 por guardar "dispositivo"/sessão, um conceito que também é razoável chamar de `login`. Não é uma decisão desta spec sozinha; revisar junto da 05-002 antes de implementar. |

## Estrutura de módulos/pacotes

```
{base}.link
├── LinkController          # endpoints: consumir token, e completar registro pendente
├── LinkService              # create(serviceKey, dto): grava LinkRecord, devolve token
│                            # consume(token): le LinkRecord, monta dto (userId/email das
│                            #   colunas + extra desserializado de extraJson), chama
│                            #   LinkRouter.consume
│                            # complete(token, extra): idem, chama LinkRouter.complete (fase 2)
├── LinkRouter               # Map<String, LinkHandler> montado a partir de List<LinkHandler> injetada
├── LinkHandler               # interface: String key(); LinkOutcome consume(LinkDto dto);
│                            #   default LinkOutcome complete(LinkDto dto, Map<String,String> extra)
├── LinkOutcome               # resultado: autenticado (com dado de redirecionamento) ou
│                            #   "registro pendente" (sinaliza ao controller devolver 202)
├── LinkDto                  # record: Long userId, String email, Map<String,String> extra
└── LinkRecord               # entidade JPA: id, token, serviceKey, userId, email, extraJson,
                              #   expiresAt, usedAt

{base}.login
└── LoginLinkHandler         # implements LinkHandler, key() = "login" -- só consume(), sem complete()

{base}.competition
└── CompetitionLinkHandler   # implements LinkHandler, key() = "competition-entry"
                              # consume(): autentica se dto.userId() != null; senão devolve "pendente"
                              # complete(dto, extra): cria User, vincula Participation (extra["participationId"]),
                              #   status = IN_COMPETITION
```

- Cada `LinkHandler` concreto mora no módulo que o implementa (`login`, `competition`), nunca
  em `link` — é o que inverte a direção de dependência descrita na spec.
- `LinkService` monta `LinkRecord` a partir do `LinkDto` copiando `userId`/`email` diretamente
  para as colunas correspondentes e serializando **só** `extra` para `extraJson` — o resto do
  DTO nunca passa por JSON. Na leitura, o caminho inverso: `userId`/`email` vêm das colunas,
  `extra` é desserializado de `extraJson`.
- `LinkDto.extra` carrega os campos específicos de cada implementação como pares
  texto-texto — `CompetitionLinkHandler` usa pelo menos `participationId` (para localizar a
  `Participation` a confirmar/vincular, já que `LinkRecord` não tem FK direta pra ela). `link`
  nunca olha dentro de `extra`.
- **Achado incidental, fora do escopo desta spec**: `PlayerManagementService.sendInviteEmail`
  e `CompetitionService.decideInviteEmailTiming` hoje duplicam quase byte a byte a lógica de
  criar um `LoginLink` ligado a uma `Participation` e escolher o template de e-mail. Centralizar
  a criação em `LinkService.create("competition-entry", dto)` remove essa duplicação como
  efeito colateral — vale mencionar como motivação extra ao implementar, mas não é requisito
  desta spec.

## Riscos e trade-offs

- **Perda de integridade referencial parcial**: mesmo com `userId`/`email` como colunas
  próprias de `LinkRecord` (decisão desta sessão — ver tabela acima), a coluna `userId` não
  tem FK pra `User`, e o conteúdo de `extraJson` (ex. `participationId`, dentro de `extra`)
  continua opaco — um valor inválido em qualquer um dos dois só é percebido quando o
  `LinkHandler` tenta usá-lo, não no momento de gravar o link. Aceito nesta spec (ver decisão
  "migração de dados" acima), mas o teste de verificação dedicado que cada implementação
  precisa ter (requisito da spec) deve cobrir explicitamente esse caso — o banco não pega mais
  esse erro sozinho. A mudança desta sessão (colunas em vez de JSON opaco para `userId`/
  `email`) reduz a superfície do problema, não a elimina: o que resta genuinamente opaco é só
  `extra`.
- **`extra` fracamente tipado**: nenhum compilador ajuda a garantir que um `LinkHandler`
  recebeu as chaves que espera dentro de `extra`. Mitigação: cada implementação valida o
  conteúdo de `extra` logo no início de `consume()`/`complete()` e falha com mensagem clara
  (ex. `IllegalArgumentException` nomeando a chave faltante), em vez de deixar uma
  `NullPointerException` genérica estourar no meio da lógica de negócio.
- **Sem catálogo de chaves + falha no boot por colisão**: resolve a colisão silenciosa, e o
  teste dedicado (ver tabela acima) antecipa esse erro para a suíte de testes, antes mesmo de
  subir a aplicação — mitigando o que antes só apareceria ao efetivamente iniciar o contexto
  Spring com dois `LinkHandler`s conflitantes.
- **Método `complete` como `default` que lança exceção**: um `LinkHandler` que não suporta
  conclusão em duas fases só descobre isso em tempo de execução, se `LinkService` chamar
  `complete` no handler errado. Mitigação: `LinkService.complete(token, extra)` só deveria ser
  alcançável a partir de um `LinkOutcome` "pendente" anterior — o próprio fluxo (202 → só then
  o cliente chama completar) já impede isso na prática, mas vale um teste explícito provando
  que chamar `complete` sem um `consume` pendente antes falha de forma controlada.
- **Nenhum mecanismo impede um `LinkHandler` em `competition` de importar tipos de `login`
  diretamente** (contornando o módulo `link`) — não é o acoplamento que esta spec ataca (que é
  `link` conhecer os consumidores), mas é uma deriva arquitetural parecida. Fora do escopo
  resolver aqui; fica registrado como risco conhecido, candidato a um teste ArchUnit numa spec
  futura de qualidade arquitetural.
