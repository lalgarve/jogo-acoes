# Caderno de testes manuais

Casos de teste manuais, executáveis via Swagger UI, organizados por Etapa da disciplina
(Issue #43). Cada caso indica os passos, o trecho de log esperado (confirmando que a chamada
passou pelo controller/repositório certo) e o `SELECT` que confirma o estado gravado no banco.

Todos os trechos de log e resultados de `SELECT` deste documento foram capturados executando a
aplicação de verdade (não são exemplos hipotéticos) — ver "Como reproduzir" em cada Etapa.

## Etapa 1 — Organização Arquitetural

### Como reproduzir

1. Subir a aplicação (`docker-compose up` a partir da raiz do repositório, ou
   `mvn spring-boot:run` dentro de `app/` para o perfil `sandbox`, com H2 no lugar do
   PostgreSQL).
2. Abrir o Swagger UI em `http://localhost:8080/api/swagger-ui/index.html` (spec 05-009).
3. Precisamos de um jogador cadastrado com sessão ativa para exercitar `GET`/`DELETE
   /sessions`. Sem uma tela de cadastro ainda (fora do escopo desta Etapa), os passos abaixo
   preparam esse estado via a própria API:
   - `POST /login-requests` com o e-mail de um jogador já cadastrado no banco — devolve `202`
     sempre (não revela se o e-mail existe), e grava um `LinkRecord` com o token do link mágico.
   - Como não há envio de e-mail real (`email.sender: stub` no perfil `sandbox`), o token não
     chega a uma caixa de entrada — mas aparece no log da própria chamada (ver Caso 1 abaixo,
     `RepositoryLoggingAspect` loga o retorno de `LinkRecordRepository.save(...)` por completo,
     token incluído), ou pode ser lido direto da tabela `link_record`/`sent_email`.
   - `GET /login-links/{token}` com esse token estabelece a sessão (cookie `SESSION`) — feito
     no próprio navegador que tem o Swagger UI aberto, as chamadas seguintes na mesma aba já
     saem autenticadas.
4. Consultar o PostgreSQL: via a UI web da spec 05-013 (quando implementada) ou `psql`
   diretamente contra o container `db` do `docker-compose.yml`. Os `SELECT`s abaixo foram
   capturados contra H2 (perfil `sandbox`, sem Docker disponível no momento da escrita) — sintaxe
   padrão, mesmo resultado esperado em PostgreSQL.

### Caso 1 — Fluxo Controller → Service → Repository → Banco

**Objetivo:** confirmar que uma requisição autenticada percorre o controller, o serviço e o
repositório antes de tocar o banco — nenhuma camada pulada.

**Passos no Swagger UI:**
1. Com uma sessão já estabelecida (ver "Como reproduzir"), expandir `GET /sessions` (tag
   `sessions`) e clicar em "Try it out" → "Execute", sem parâmetros.
2. Resposta esperada: `200`, um array com pelo menos uma sessão — a que fez a própria
   requisição, com `"current": true`.

**Log esperado** (`SessionsController` → `SessionsService`/repositórios, DEBUG, spec 05-011 —
trecho real, capturado com um único jogador logado num único dispositivo):

```
DEBUG ... ControllerLoggingAspect  : --> SessionsController.listActiveSessions()
DEBUG ... RepositoryLoggingAspect  : --> UserRepository.findByEmail(leila.teste@example.com)
DEBUG ... RepositoryLoggingAspect  : <-- UserRepository.findByEmail returned {"email":"leila.teste@example.com","id":1,"name":"Leila Teste","registered":true}
DEBUG ... RepositoryLoggingAspect  : --> LoginSessionRepository.findByUserIdAndEndedAtIsNullOrderByCreatedAtAsc(1)
DEBUG ... RepositoryLoggingAspect  : <-- LoginSessionRepository.findByUserIdAndEndedAtIsNullOrderByCreatedAtAsc returned [{"createdAt":"2026-09-18T22:12:09.875974","deviceId":"Windows 15.0.0 · Chromium 131","endedAt":null,"httpSessionId":"7d8e6745-...","id":1,"linkRecord":{...},"userId":1}]
DEBUG ... ControllerLoggingAspect  : <-- SessionsController.listActiveSessions returned [{"createdAt":"2026-09-18T22:12:09.875974Z","current":true,"deviceLabel":"Windows 15.0.0 · Chromium 131","id":1}]
```

O controller nunca chama `LoginSessionRepository` diretamente — só através de
`SessionsService.listActive(...)`; o que o log mostra é a chamada ao repositório acontecendo
*durante* a execução do método do controller (mesma thread, entre a linha `-->` e `<--` do
controller), não uma chamada direta visível no controller.

**Select de verificação:**

```sql
SELECT id, user_id, device_id, http_session_id, created_at, ended_at
FROM login_session
WHERE user_id = 1;
```

Resultado real (jogador de id `1`, uma sessão ativa, `ended_at` nulo):

```
ID | USER_ID | DEVICE_ID                    | HTTP_SESSION_ID                      | CREATED_AT                 | ENDED_AT
 1 |       1 | Windows 15.0.0 · Chromium 131 | 7d8e6745-8996-4050-8238-9b1055747aa1 | 2026-09-18 22:12:09.875974 | null
```

### Caso 2 — Validação automática de entrada

**Objetivo:** confirmar que uma entrada malformada é rejeitada antes de alcançar o código de
negócio.

**Achado ao reproduzir este caso** (registrado aqui em vez de silenciosamente ajustado, porque
muda o que o caso realmente demonstra): a tabela do §4 de
`alinhamento-projeto-disciplina.md` descreve esse critério como "Validação via Bean
Validation" — anotações `@Valid`/`@Email` em DTOs de corpo de requisição. Nenhuma operação de
`/sessions` tem corpo (`GET`/`DELETE` só usam path/query), então não há Bean Validation para
demonstrar *nela*. O que existe é uma camada diferente, também automática: a conversão de tipo
do Spring MVC para o path variable `sessionId` (`Long`).

**Passos no Swagger UI:**
1. Expandir `DELETE /sessions/{sessionId}`, "Try it out", preencher `sessionId` com `abc` (não
   numérico), "Execute".
2. Resposta real: `400`, corpo `{"timestamp":"...","status":400,"error":"Bad Request","path":"/api/sessions/abc"}`
   — o corpo **padrão do Spring Boot**, não o schema `Error` do nosso contrato. Confirma que a
   rejeição acontece na camada de binding de parâmetros do Spring MVC
   (`MethodArgumentTypeMismatchException`), antes mesmo do Spring conseguir invocar
   `SessionsController.revokeSession(Long)` — não há conversão de `"abc"` para `Long` possível.

**Log esperado:** nenhum. Confirmado executando: nem `ControllerLoggingAspect` nem
`RepositoryLoggingAspect` produzem qualquer linha para essa chamada — o método do controller
nunca chega a ser invocado, então o aspecto (que envolve a *execução* do método) nunca tem
uma chamada pra interceptar. Isso por si só já é uma confirmação útil: a validação aconteceu
*antes* de qualquer código nosso rodar.

**Select de verificação:** nenhum — nada foi lido nem escrito no banco.

**Bean Validation de verdade, pra completar o critério:** `POST /competitions` com
`type: "PRIVATE"` e `emails: ["nao-e-um-email"]` aciona `@Email` (gerado a partir de
`format: email` no contrato, presente em `CompetitionCreateRequest.emails` — confirmado no
código gerado, `target/generated-sources/openapi/.../model/CompetitionCreateRequest.java`),
`MethodArgumentNotValidException`, capturada centralmente por
`ApiExceptionHandler.handleValidation` → `400` com o schema `Error` do nosso contrato
(`{"message":"Invalid e-mail"}`), dessa vez com o corpo *nosso*, não o padrão do Spring Boot.

### Caso 3 — Tratamento de exceção centralizado

**Objetivo:** confirmar que um erro esperado (recurso não encontrado) devolve uma resposta
previsível e padronizada.

**Achado ao reproduzir este caso** (mesmo espírito do Caso 2): `DELETE
/sessions/{sessionId}` com um id que não existe (ou pertence a outro jogador) devolve `404`,
mas **não** passa por `ApiExceptionHandler` — `SessionsController.revokeSession` retorna
`ResponseEntity.notFound().build()` diretamente, um retorno normal, não uma exceção
capturada. O critério "tratamento de exceção centralizado" descreve `ApiExceptionHandler`
como mecanismo — ele existe e funciona (ver abaixo), só não é *este* caminho específico que o
exercita.

**Passos no Swagger UI:**
1. Expandir `DELETE /sessions/{sessionId}`, "Try it out", preencher `sessionId` com `999999`
   (id que não existe), "Execute".
2. Resposta real: `404`, corpo vazio.

**Log esperado** (trecho real):

```
DEBUG ... ControllerLoggingAspect  : --> SessionsController.revokeSession(999999)
DEBUG ... RepositoryLoggingAspect  : --> UserRepository.findByEmail(leila.teste@example.com)
DEBUG ... RepositoryLoggingAspect  : <-- UserRepository.findByEmail returned {"email":"leila.teste@example.com","id":1,"name":"Leila Teste","registered":true}
DEBUG ... RepositoryLoggingAspect  : --> LoginSessionRepository.findByIdAndUserId(999999, 1)
DEBUG ... RepositoryLoggingAspect  : <-- LoginSessionRepository.findByIdAndUserId returned null
DEBUG ... ControllerLoggingAspect  : <-- SessionsController.revokeSession returned null
```

`returned null` na última linha é o corpo `null` de um `404` sem conteúdo — o método do
controller retornou normalmente, sem lançar nada.

**Select de verificação:** nenhum — `findByIdAndUserId` não encontrou a linha, nada foi
alterado.

**Tratamento de exceção centralizado de verdade, pra completar o critério:** `GET
/competitions/{competitionId}` com um id de competição que o jogador logado não participa (e
não é administrador) aciona a mesma resposta HTTP (`404`, sem revelar se a competição existe),
mas por um caminho diferente — `CompetitionViewService` lança (indiretamente, via
`Optional` vazio tratado no controller) o padrão já visto; o exemplo mais direto de exceção de
verdade capturada centralmente é tentar consumir um link de login inválido/expirado
(`GET /login-links/{token}` com um token que não existe): `LinkService` lança
`LoginLinkInvalidException`, capturada por `ApiExceptionHandler.handleLoginLinkInvalid` → `400`
com `{"message":"Link invalid or expired"}` — o schema `Error` do contrato, desta vez via
exceção de verdade, não retorno direto.

### Caso 4 — Consultas Spring Data além do CRUD básico

**Objetivo:** identificar consultas derivadas (method-name query) além de `save`/`findById`,
e o SQL que cada uma gera.

`GET`/`DELETE /sessions` usam duas consultas dedicadas, nenhuma delas fornecida pronta por
`JpaRepository`:

| Consulta | Usada por | SQL equivalente |
|---|---|---|
| `LoginSessionRepository.findByUserIdAndEndedAtIsNullOrderByCreatedAtAsc(Long userId)` | `GET /sessions` (listagem) e `LoginLinkSessionService.enforceDeviceLimit` (limite de dispositivos, spec 05-002) | `SELECT * FROM login_session WHERE user_id = ? AND ended_at IS NULL ORDER BY created_at ASC` |
| `LoginSessionRepository.findByIdAndUserId(Long id, Long userId)` | `DELETE /sessions/{sessionId}` (revogação) | `SELECT * FROM login_session WHERE id = ? AND user_id = ?` |

A segunda existe especificamente para que "id não existe" e "id existe mas é de outro
jogador" caiam na mesma consulta, devolvendo vazio nos dois casos — sem revelar qual dos dois
aconteceu (ver Caso 3).

**Log esperado:** os mesmos trechos dos Casos 1 e 3 acima já mostram as duas consultas em
ação (`findByUserIdAndEndedAtIsNullOrderByCreatedAtAsc` e `findByIdAndUserId`).

**Select de verificação:** os dois `SELECT`s equivalentes da tabela acima, rodados direto —
resultado já mostrado no Caso 1 para o primeiro (uma linha, jogador `1`).

### Caso 5 — Documentação da API via OpenAPI/Swagger UI interativa

**Objetivo:** confirmar que o Swagger UI sobe junto da aplicação e permite testar uma operação
com headers customizados preenchidos manualmente — não só visualizar o contrato estático.

**Passos no Swagger UI:**
1. Abrir `http://localhost:8080/api/swagger-ui/index.html` — confirma `200`.
2. Expandir `GET /login-links/{token}` (tag `login`) — confirma 4 campos de header opcionais
   preenchíveis: `Sec-CH-UA`, `Sec-CH-UA-Platform`, `Sec-CH-UA-Platform-Version`,
   `Sec-CH-UA-Mobile` (spec 05-009), além do `token` no path.
3. Preencher esses 4 headers manualmente (simulando o que um browser real mandaria) e um
   token de um link ativo, "Execute" — a resposta reflete o rótulo de dispositivo formatado
   a partir desses valores (ver Caso 1: `"deviceLabel":"Windows 15.0.0 · Chromium 131"`).

Confirmado executando: `curl http://localhost:8080/api/openapi.yaml` devolve o contrato
completo (o mesmo `docs/openapi.yaml` do repositório, copiado pro classpath em build time — não
duplicado à mão), com os 4 parâmetros de header como `$ref` para
`#/components/parameters/SecChUa*`.

**Log esperado:** não aplicável — servir a UI e o contrato estático não passa pelos três
aspectos (não são `@RestController`/repositório/fila da aplicação).

**Select de verificação:** não aplicável.

### Caso 6 — Organização de pacotes por domínio/funcionalidade

**Objetivo:** confirmar que os pacotes principais são organizados por domínio de negócio
(`login/`, `link/`, `competition/`, `log/`, `email/`, `captcha/`), não por camada técnica
(`controller/`, `service/`, `repository/`) — critério resolvido pelas specs 05-001/05-002.

Sem chamada HTTP — nota estrutural. `SessionsController`, `SessionsService` e
`LoginSessionRepository` (usados pelos Casos 1–4 acima) vivem em dois pacotes de domínio:

- `dev.leilaalgarve.jogoacoes.login` — `SessionsController.java`, `SessionsService.java`: a
  gestão de sessões é um assunto de "quem está logado", mesmo critério que já levava
  `LoginController`/`LoginLinkSessionService` a morar aqui.
- `dev.leilaalgarve.jogoacoes.link` — `LoginSession.java`, `LoginSessionRepository.java`: o
  registro de sessão em si é modelo de dados do mecanismo de link mágico (spec 05-002), que
  `login` depende de volta (nunca o contrário — `link` não conhece `login`, ver spec 05-003).

Nenhum pacote chamado `controller`, `service` ou `repository` existe no projeto — confirmável
com `find app/src/main/java -type d`.

## Referências

- Spec [05-009](../../specs/05-009-identificacao-dispositivo-client-hints/spec.md) — Swagger UI
  interativa, User-Agent Client Hints.
- Spec [05-010](../../specs/05-010-gestao-sessoes-ativas/spec.md) — `GET`/`DELETE /sessions`,
  o exemplo central deste caderno.
- Spec [05-011](../../specs/05-011-logging-aspectos/spec.md) — os três aspectos de logging
  citados nos trechos de log.
- [alinhamento-projeto-disciplina.md](alinhamento-projeto-disciplina.md) — mapeamento completo
  do projeto contra as quatro Etapas da disciplina.
