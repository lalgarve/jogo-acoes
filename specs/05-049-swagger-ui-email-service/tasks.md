# Tasks: Swagger UI no Serviço de E-mail

Quebra `plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`).

**Ordem:** o teste primeiro, falhando pelo motivo esperado (`401`), depois a mudança.

Issue-épico: a criar quando a spec for aprovada.

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| ~~T001~~ | Resolver a decisão em aberto de `spec.md` (ambientes) e registrar com commit `decision:` | — | | — |
| ~~T002~~ | `SwaggerUiAccessTest` (`plan.md`, "Decisões de arquitetura", linha "Teste"). Confirmar que falha com `401` na tela e no contrato | — | [P] | — |
| ~~T003~~ | `email-service/pom.xml` (springdoc e cópia do contrato), bloco `springdoc` no `application.yml`, `shouldNotFilter` no `ApiKeyAuthenticationFilter`; `config-repo/` (desligado em `staging` e `production`, T001). Confirmar T002 verde e a suíte do serviço verde | T001, T002 | | — |
| ~~T004~~ | `README.md`: endereço do Swagger UI do `email-service` | T003 | [P] | — |
| ~~T005~~ | Caderno de testes do Serviço de E-mail: em vez de reescrever os passos, uma nota dizendo que agora há Swagger UI (decisão da Leila, 2026-10-20: o caderno é parte do documento já entregue e continua com o `chamar.sh`) | T003 | [P] | — |
| ~~T006~~ | Critério de aceite de exceções (`memory/constitution.md`, "Critério de aceite: exceções e caminhos de erro exercitados por teste"): nenhuma classe de exceção nova; o branch novo é o `shouldNotFilter` (caminho liberado e caminho da API barrado), coberto pela T002. Registrar aqui | T003 | | — |
| T007 | Verificação manual (Leila): rodar o roteiro "Verificação manual (T007)" abaixo | T003–T006 | | — |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em qualquer
  ordem/em paralelo.
- Marcar o ID como concluído (`~~T001~~` ou checkbox `[x]`) na mesma PR que resolve a task,
  junto com o registro da verificação.

## Registro da implementação (2026-10-19)

Na sessão de nuvem da Claude (`scripts/cloud-up.sh`, spec 05-052), branch
`claude/swagger-ui-email-service`.

- **T002**: `SwaggerUiAccessTest`, antes da mudança: 4 de 5 vermelhos com `401` (tela, contrato,
  `swagger-config`, `/v3/api-docs`); "as rotas da API pedem a chave" já passava.
- **T003**: springdoc 3.1.1, cópia do contrato pelo `maven-antrun-plugin`, bloco `springdoc` no
  `application.yml`, `shouldNotFilter` no `ApiKeyAuthenticationFilter` e `config-repo` de
  `staging`/`production` desligando o Swagger UI. Achado ao conferir o "só `docker`" rodando o
  jar com o springdoc desligado: tela e `/v3/api-docs` respondiam `404`, mas `openapi.yaml`
  continuava `200` sem chave (arquivo estático). O filtro passou a liberar os caminhos só com o
  Swagger UI ligado (`plan.md`), e `SwaggerUiSwitchedOffTest` cobre o caso: antes da correção,
  4 de 4 vermelhos (`404`, `200`, `404`, `404`); depois, `401` nos quatro.
  Suíte do `email-service`: 131 testes, 129 verdes e 2 ignorados (`@requires-real-ses`), piso de
  cobertura atendido. `config-server`: 11 verdes. Uma primeira rodada deu `500` nos envios por
  sujeira de build (`target/` com a `V7__add_delivery_state.sql` de outro branch); refeita depois
  de `mvn clean` e de um ambiente novo.
- **T004**: `README.md`, tabela de endereços.
- **T006**: nenhuma classe de exceção nova. Os dois caminhos do `shouldNotFilter` (liberado com o
  Swagger UI ligado; barrado desligado e nas rotas da API) estão em `SwaggerUiAccessTest` e
  `SwaggerUiSwitchedOffTest`.
- **Roteiro da T007 rodado pela Claude** com o serviço de pé: passos 1, 3, 4 e 7 por `curl`
  (`200` e `title: Serviço de E-mail`; `401`; `200`/`[]` e `403`; `0` rotas geradas) e passos 2,
  4, 5 e 6 no navegador (Chromium pelo Playwright): título, as três tags, **Authorize**, nenhum
  "Failed to load remote configuration"; a chave continua depois de recarregar; troca de chave
  com `403` nas rotas sem a permissão. A T007 continua sendo da Leila.
- **T005** (2026-10-20): o caderno do Serviço de E-mail é parte do documento já entregue da
  disciplina; por decisão da Leila, os passos continuam com o `chamar.sh` e o caderno ganhou só
  uma nota sobre o Swagger UI, logo depois do parágrafo que diz que o serviço não tem um.

## Verificação manual (T007)

Na raiz do repositório, no WSL, com o branch da implementação. Sobe só o `email-service` e o
que ele precisa; `--build` garante a imagem com a mudança.

```bash
export COMPOSE_FILE=docker-compose.yml
docker compose down -v
docker compose up -d --wait --build db-email-service localstack dns
./scripts/test-api-key.sh restore
docker compose up -d --wait --build email-service
./scripts/test-api-key.sh restore
docs/disciplina/caderno-de-testes-email-service/scripts/gerar-chaves.sh
```

1. O contrato abre sem chave:
   ```bash
   curl -s -w '\n[%{http_code}]\n' http://localhost:8082/api/openapi.yaml | head -3
   ```
   Esperado: `openapi: 3.0.3`, `info:`, `  title: Serviço de E-mail` e, no fim, `[200]`.
2. A tela abre sem chave: no navegador, `http://localhost:8082/api/swagger-ui.html`. Esperado:
   o título "Serviço de E-mail", as tags `templates`, `emails` e `disposable-domains`, e o
   botão **Authorize**. Nenhuma mensagem "Failed to load remote configuration".
3. Sem chave, pela tela: `GET /templates` → **Try it out** → **Execute**. Esperado: `401`,
   `{"message":"Missing or invalid X-API-Key"}`.
4. Com a chave de envio: **Authorize**, colar o valor de `CHAVE_ENVIO` de
   `docs/disciplina/caderno-de-testes-email-service/chaves.env`, **Authorize**, **Close**.
   `GET /templates` → `200`, `[]`. `GET /disposable-domain-sources` → `403`,
   `{"message":"Missing permission: disposable-email:admin"}`.
5. Recarregar a página (F5) e repetir `GET /templates`. Esperado: `200`, sem colar a chave de
   novo.
6. Trocar para a chave de operações: **Authorize** → **Logout** → colar `CHAVE_OPERACOES`.
   `GET /disposable-domain-sources` → `200`; `GET /templates` → `403`,
   `{"message":"Missing permission: email:send"}`.
7. Nada gerado pelas anotações:
   ```bash
   curl -s http://localhost:8082/api/v3/api-docs | jq '.paths | length'
   ```
   Esperado: `0`.

Para derrubar: `docker compose down -v`.
