# Tasks: Proxy reverso de Client Hints para testar pelo Swagger UI

Quebra `plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`). Todas as decisões técnicas
referenciadas abaixo (aplicação separada, dois métodos em `/blackbox/proxy/headers`, semântica
de "em branco" = removido, `RestClient`, script com portas sobrescrevíveis) já estão resolvidas
em `plan.md` — esta lista só quebra a implementação em passos.

**Sem Cucumber/contrato OpenAPI novo** — infraestrutura/ferramenta de teste, fora de `app/` e de
`docs/openapi.yaml` (ver "Cenários" em `spec.md`).

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| ~~T001~~ | Criar `blackbox-proxy/pom.xml` (parent próprio `spring-boot-starter-parent`, só `spring-boot-starter-web`) e acrescentar `<module>blackbox-proxy</module>` ao `pom.xml` da raiz | — | | #76 |
| ~~T002~~ | Criar `blackbox-proxy/src/main/java/.../blackboxproxy/BlackboxProxyApplication.java` (`@SpringBootApplication`) e `blackbox-proxy/src/main/resources/application.yml` com `server.port: 8090` e `blackbox-proxy.target-base-url: http://localhost:8080`, ambos com padrão mas sobrescrevíveis por variável de ambiente (relaxed binding) | T001 | [P] | #76 |
| ~~T003~~ | Criar `DeviceHeaderStore.java`: `AtomicReference` a um record `DeviceHeaders` (`secChUa`, `secChUaPlatform`, `secChUaPlatformVersion`, `secChUaMobile`, `userAgent`, todos `String` opcionais); `set(DeviceHeaders)` substitui por inteiro (sem merge); `get()` devolve o estado corrente, inicial = todos `null` | T002 | [P] | #76 |
| ~~T004~~ | Criar `DeviceHeaderController.java`: `POST /blackbox/proxy/headers` (corpo `DeviceHeaders`, chama `DeviceHeaderStore.set`, `204`) e `GET /blackbox/proxy/headers` (sem corpo, devolve `DeviceHeaderStore.get()`, `200`) | T003 | | #76 |
| ~~T005~~ | Criar `ReverseProxyController.java`: `@RequestMapping(value = "/**", method = {GET, POST, PUT, PATCH, DELETE})`, exclui `/blackbox/proxy/headers` (delegado a T004) — monta a chamada de saída via `RestClient` pra `blackbox-proxy.target-base-url` + caminho/query string recebidos, copiando os headers de entrada exceto os cinco controlados pelo proxy e os hop-by-hop (`Connection`, `Transfer-Encoding`, `Keep-Alive`, `Host`, `Content-Length`); acrescenta os cinco headers só com o que `DeviceHeaderStore.get()` tiver não-`null` (nunca repassa o valor de entrada); devolve `ResponseEntity` com status/corpo/headers da resposta real, `Set-Cookie` incluso | T003 | | #76 |
| ~~T006~~ | Criar `blackbox-proxy/src/test/java/.../blackboxproxy/ReverseProxyIntegrationTest.java` (`@SpringBootTest`, servidor de destino falso — `com.sun.net.httpserver.HttpServer` embutido no teste, sem dependência nova): confirma que (a) uma chamada sem nada configurado chega no destino sem nenhum dos cinco headers, mesmo mandando um `User-Agent` na entrada; (b) `POST /blackbox/proxy/headers` seguido de qualquer outra chamada faz o destino receber exatamente os valores configurados; (c) um segundo `POST` com só alguns campos limpa os que ficaram de fora (sem merge); (d) `Set-Cookie` da resposta do destino chega na resposta do proxy; (e) `GET /blackbox/proxy/headers` devolve o que foi configurado por último | T004, T005 | | #76 |
| ~~T007~~ | Criar `scripts/blackbox-proxy.sh`: opções `--target-url` (padrão `http://localhost`), `--target-port` (padrão `8080`), `--proxy-port` (padrão `8090`), todas opcionais — roda `SERVER_PORT=<proxy-port> BLACKBOX_PROXY_TARGET_BASE_URL=<target-url>:<target-port> mvn -pl blackbox-proxy -am spring-boot:run` | T002 | [P] | #76 |
| ~~T008~~ | Atualizar `README.md`: nova linha na tabela de módulos (seção "Módulos") e um parágrafo em "Ambiente de testes blackbox" descrevendo o fluxo — subir `app/`, rodar `./scripts/blackbox-proxy.sh`, configurar o dispositivo (`POST http://localhost:8090/blackbox/proxy/headers`), abrir `http://localhost:8090/api/swagger-ui.html` em vez do endereço direto do `app/`; nota sobre rodar o script de novo com `--proxy-port` diferente pra mais de um dispositivo ao mesmo tempo | T007 | [P] | #76 |
| ~~T009~~ | Rodar os testes de `blackbox-proxy/` (`mvn -pl blackbox-proxy -am test`) — confirmar `ReverseProxyIntegrationTest` verde | T006 | | #76 |
| ~~T010~~ | Verificação manual fim a fim: subir `app/` (perfil `blackbox`), rodar `./scripts/blackbox-proxy.sh`, `POST /blackbox/proxy/headers` com um perfil de dispositivo (mesmos valores de `DeviceLabelResolverTest`/spec 05-019), abrir `http://localhost:<porta-do-proxy>/api/swagger-ui.html`, consumir um link de login por ali e conferir em `GET /sessions` que o `deviceLabel` bate com o perfil configurado; repetir com a configuração em branco (`POST` com corpo vazio ou sem nunca configurar) e confirmar que cai no fallback (`User-Agent`/`"unknown-device"`, não no que o navegador mandou de verdade) — registrar aqui qual caminho de verificação foi possível no ambiente de implementação (mesma ressalva de rede a Docker já registrada nas specs 05-014/05-015/05-016/05-018) | T008, T009 | | #76 |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em qualquer
  ordem/em paralelo.
- Cada linha vira um item de checklist na Issue-épico da feature, ou uma Issue própria quando
  grande o suficiente para PR isolada — a Issue leva o label `iteration-5`, além do label de
  tipo (`test`). Coluna "Issue" preenchida quando a Issue-épico for aberta.
- T010 depende de um ambiente `blackbox` (spec 05-014) acessível no ambiente de implementação —
  se `docker compose up` completo não estiver disponível, validar contra o jar empacotado +
  Postgres real (mesmo caminho já usado para verificar as specs 05-014/05-015/05-016/05-018), e
  registrar explicitamente qual dos dois foi usado.
- Marcar o ID como concluído (`~~T001~~` ou checkbox `[x]`) quando o commit que a resolve for
  mesclado — não deixar a tabela dessincronizada do estado real.

## T010 — registro da verificação

Ambiente Docker completo indisponível neste ambiente de implementação (mesmo bloqueio de rede
já registrado nas specs 05-014/05-015/05-016/05-018) — validado contra o `app/` empacotado
(jar + PostgreSQL local) e o `blackbox-proxy/` também empacotado, cada um seu processo, portas
`8080`/`8090`. `mvn -pl blackbox-proxy -am test` verde (5 testes, incluindo os 5 casos de
`ReverseProxyIntegrationTest`).

Dois achados durante a implementação, já registrados em `plan.md`:

1. **`RestClient.uri(URI)` com um `URI` relativo não aplica a base URL** — precisa da variante
   `.uri(String)`, que passa pelo `UriBuilderFactory` configurado com `baseUrl`. Corrigido em
   `ReverseProxyController` antes de qualquer verificação externa (achado pelos próprios testes
   de integração).
2. **`User-Agent` em branco não sai literalmente ausente** — o `java.net.http.HttpClient` da JDK
   sempre preenche um valor próprio (`"Java-http-client/<versão>"`) quando a aplicação não
   define um; não existe API pública pra suprimir isso. A garantia que continua valendo (e é a
   que importa) — o valor real de quem chamou o proxy nunca vaza — foi confirmada manualmente
   abaixo.

Verificação manual fim a fim, com o app rodando em relógio real (não depende de tempo passado):

- Configurado o dispositivo (`POST /blackbox/proxy/headers` com os valores de
  `DeviceLabelResolverTest`/spec 05-019) e consumido um link de login do administrador
  **através do proxy** (`http://localhost:8090/api/login-links/{token}`) — `GET /sessions`
  (também através do proxy) mostrou a nova sessão com `deviceLabel: "Windows 15.0.0 · Chromium
  131"`, batendo exatamente com a configuração.
- Resetado o dispositivo pra em branco (`POST` com corpo `{}`) e repetido o login, desta vez
  mandando um `User-Agent: curl/8.5.0-should-never-leak` explícito na chamada de entrada — a
  nova sessão mostrou `deviceLabel: "Java-http-client/21.0.10"`: o valor real de quem chamou
  **nunca vazou**, exatamente a garantia que a spec pede, mesmo no caso em que "em branco" não
  significa ausência total (achado 2 acima).
- `http://localhost:8090/api/swagger-ui.html` carregou a página real do Swagger UI do `app/`
  através do proxy (redirecionamento + página, `200` depois de seguir o `302`), confirmando que
  os arquivos estáticos também passam — não só as rotas de API.
