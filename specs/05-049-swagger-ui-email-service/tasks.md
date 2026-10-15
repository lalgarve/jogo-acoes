# Tasks: Swagger UI no Serviço de E-mail

Quebra `plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`).

**Ordem:** o teste primeiro, falhando pelo motivo esperado (`401`), depois a mudança.

Issue-épico: a criar quando a spec for aprovada.

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| T001 | Resolver a decisão em aberto de `spec.md` (ambientes) e registrar com commit `decision:` | — | | — |
| T002 | `SwaggerUiAccessTest` (`plan.md`, "Decisões de arquitetura", linha "Teste"). Confirmar que falha com `401` na tela e no contrato | — | [P] | — |
| T003 | `email-service/pom.xml` (springdoc e cópia do contrato), bloco `springdoc` no `application.yml`, `shouldNotFilter` no `ApiKeyAuthenticationFilter`; `config-repo/` se T001 decidir "só `docker`". Confirmar T002 verde e a suíte do serviço verde | T001, T002 | | — |
| T004 | `README.md`: endereço do Swagger UI do `email-service` | T003 | [P] | — |
| T005 | Caderno de testes do Serviço de E-mail: passos de chamada à API no Swagger UI (JSON em bloco ` ```json `, chave no **Authorize**), endereço na tabela de programas; `chamar.sh` sai ou fica como alternativa | T003 | [P] | — |
| T006 | Critério de aceite de exceções (`memory/constitution.md`, "Critério de aceite: exceções e caminhos de erro exercitados por teste"): nenhuma classe de exceção nova; o branch novo é o `shouldNotFilter` (caminho liberado e caminho da API barrado), coberto pela T002. Registrar aqui | T003 | | — |
| T007 | Verificação manual (Leila): rodar o roteiro "Verificação manual (T007)" abaixo | T003–T006 | | — |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em qualquer
  ordem/em paralelo.
- Marcar o ID como concluído (`~~T001~~` ou checkbox `[x]`) na mesma PR que resolve a task,
  junto com o registro da verificação.

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
