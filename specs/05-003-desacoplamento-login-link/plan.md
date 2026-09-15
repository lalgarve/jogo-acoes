# Plan: Desacoplar o módulo `link` dos seus consumidores

Traduz `spec.md` em decisões técnicas. Valida contra `memory/constitution.md`.

## Contexto técnico

- Módulo `link` já existe fisicamente como resultado da spec 05-002 (`{base}.link`, mesmo
  módulo Maven `app/`) — esta spec redesenha o que já está lá dentro, não move arquivo de novo.
- Stack disponível sem dependência nova: Jackson (`ObjectMapper`, já usado pelo Spring MVC nos
  controllers) para serializar/desserializar `LinkDto` de/para o `dtoJson` gravado em
  `LinkRecord`; Spring para injeção de `List<LinkHandler>` no `LinkRouter`.
- Pré-requisito funcional: nenhum `LinkHandler` concreto pode ser implementado antes de saber
  quais fluxos hoje passam por `LoginLink` — ver decisão em aberto abaixo.

## Decisões de arquitetura

| Pergunta | Decisão | Status | Raciocínio |
|---|---|---|---|
| Formato do DTO com campos extra por implementação | `LinkDto` único (sem hierarquia de subclasses): `Long userId`, `String email`, `Map<String, String> extra` | resolvida | Decisão da autora (sessão 2026-09-15): um DTO único evita que `LinkService`/`LinkRouter` precisem conhecer/fazer downcast de tipos concretos por implementação — o único contrato que `link` enxerga é sempre o mesmo tipo. O custo (fraco tipamento de `extra`) é aceito — ver "Riscos e trade-offs". |
| Onde vive a chave de serviço como constante | Sem catálogo central no módulo `link` — cada `LinkHandler` declara sua própria constante de chave e se registra no `LinkRouter` só por implementar a interface | resolvida | Decisão da autora: um catálogo central em `link` reintroduziria exatamente o acoplamento que esta spec elimina (`link` teria que conhecer os nomes/chaves de todo consumidor existente). |
| Migração de dados de `LoginLink`/`Participation` (FK) para `LinkRecord` (JSON) | Não se aplica — nenhuma migração de dados existentes é necessária | resolvida | Decisão da autora: o sistema ainda não entrou em produção, não há linha de `LoginLink` real para preservar. |
| Serialização do DTO | Jackson `ObjectMapper`, sem dependência nova | resolvida | Já é a biblioteca de serialização usada pelo Spring MVC no resto do projeto. |
| Como o `LinkRouter` é populado | Spring injeta `List<LinkHandler>` no construtor do `LinkRouter`, que monta o `Map<String, LinkHandler>` a partir da chave que cada implementação expõe (ex. método `key()` da interface) | resolvida | Qualquer `LinkHandler` novo só precisa ser um `@Component` implementando a interface — nenhum lugar central lista os handlers manualmente, consistente com "sem catálogo" acima. |
| Colisão de chave entre duas implementações | `LinkRouter` falha ao subir o contexto Spring se duas implementações declararem a mesma chave, em vez de uma sobrescrever a outra silenciosamente no mapa | resolvida | Sem catálogo central (decisão acima), nada mais detectaria a colisão — falhar cedo no boot troca um bug silencioso de produção por um erro de inicialização óbvio. |
| Mapeamento exato dos fluxos atuais (login avulso, convite de competição, pedido de entrada) para implementações concretas de `LinkHandler` | Em aberto | em aberto | Requer revisão linha a linha de `LoginService`/`EntryRequestService`/`PlayerManagementService` (não feita nesta sessão) para saber se são 1, 2 ou 3 `LinkHandler`s distintos — os nomes usados no diagrama "depois" de `spec.md` são ilustrativos. |

## Estrutura de módulos/pacotes

```
{base}.link
├── LinkController          # único endpoint: recebe o token, delega em LinkService.consume
├── LinkService              # create(serviceKey, dto): grava LinkRecord, devolve token
│                            # consume(token): lê LinkRecord, desserializa dto, chama LinkRouter
├── LinkRouter               # Map<String, LinkHandler> montado a partir de List<LinkHandler> injetada
├── LinkHandler              # interface: String key(); void handle(LinkDto dto)
├── LinkDto                  # record: Long userId, String email, Map<String,String> extra
└── LinkRecord               # entidade JPA: id, token, serviceKey, dtoJson, expiresAt, usedAt

{base}.login
└── LoginLinkHandler         # implements LinkHandler, key() = "login" (nome final a confirmar)

{base}.competition
└── <a definir>              # 1+ implementações de LinkHandler para convite/pedido de entrada
                              # — quantidade e nomes dependem da decisão em aberto acima
```

- Cada `LinkHandler` concreto mora no módulo que o implementa (`login`, `competition`), nunca
  em `link` — é o que inverte a direção de dependência descrita na spec.
- `LinkDto.extra` carrega os campos específicos de cada implementação como pares
  texto-texto (ex. `competitionId`) — cada `LinkHandler` só sabe interpretar as chaves que ele
  mesmo colocou lá no momento de criar o link (via `LinkService.create`); `link` nunca olha
  dentro de `extra`.

## Riscos e trade-offs

- **Perda de integridade referencial no banco**: `LinkRecord` não tem FK pra `User`/
  `Participation` — `dtoJson` é texto livre, então um `userId` inválido dentro dele só é
  percebido quando o `LinkHandler` tenta usá-lo (ex. `userRepository.findById` vazio), não no
  momento de gravar o link. Aceito nesta spec (ver decisão "migração de dados" acima), mas o
  teste de verificação dedicado que cada implementação precisa ter (requisito da spec) deve
  cobrir explicitamente o caso de `userId` inexistente — o banco não pega mais esse erro
  sozinho.
- **`extra` fracamente tipado**: nenhum compilador ajuda a garantir que um `LinkHandler`
  recebeu as chaves que espera dentro de `extra`. Mitigação: cada implementação valida o
  conteúdo de `extra` logo no início do `handle()` e falha com mensagem clara (ex.
  `IllegalArgumentException` nomeando a chave faltante), em vez de deixar uma
  `NullPointerException` genérica estourar no meio da lógica de negócio.
- **Sem catálogo de chaves + falha no boot por colisão**: resolve a colisão silenciosa, mas
  significa que uma chave duplicada só aparece ao subir a aplicação (não em tempo de
  compilação) — aceitável dado que isso só aconteceria ao adicionar um `LinkHandler` novo, um
  evento raro e sempre acompanhado de teste próprio.
- **Nenhum mecanismo impede um `LinkHandler` em `competition` de importar tipos de `login`
  diretamente** (contornando o módulo `link`) — não é o acoplamento que esta spec ataca (que é
  `link` conhecer os consumidores), mas é uma deriva arquitetural parecida. Fora do escopo
  resolver aqui; fica registrado como risco conhecido, candidato a um teste ArchUnit numa spec
  futura de qualidade arquitetural.
