# Tasks: pré-visualização de template devolve o assunto com acentos corretos

Quebra `plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`).

**Ordem:** o teste que reproduz o defeito vem primeiro, falhando pelo motivo esperado; depois a
correção. A correção (T002 em diante) só começa com pedido explícito (constitution, "Nunca
começar a implementar sem pedido explícito").

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| ~~T001~~ | `register_templates.feature`, regra "A client previews a template without sending an e-mail": cenário `Client previews a template with accented characters in the subject and body` (assunto `{{name}}, sua conta está pronta`, corpo `<p>Olá, {{name}}! Sua conta está pronta.</p>`, pré-visualização com `name = Ada`) e os passos novos em `RegisterTemplatesSteps`. Rodar contra o LocalStack e confirmar que falha pelo assunto | — | | — |
| ~~T002~~ | `MimeRenderedTemplateParser`: `Session.getInstance` com `mail.mime.allowutf8=true` no lugar de `Session.getDefaultInstance(new Properties())`; Javadoc atualizado (UTF-8 cru do LocalStack, RFC 2047 continua aceito). Confirmar T001 verde | T001 | | — |
| ~~T003~~ | Critério de aceite de exceções (`memory/constitution.md`, "Critério de aceite: exceções e caminhos de erro exercitados por teste"): listar as classes de exceção criadas (esperado: nenhuma) e os branches de exceção adicionados ou alterados (esperado: nenhum; o `catch` do parser fica igual). Registrar abaixo | T002 | | — |
| ~~T004~~ | Rodar `mvn -pl email-service -am verify` e `mvn -pl app -am verify` e confirmar verde | T002 | | — |
| ~~T005~~ | Caderno do email-service (`docs/disciplina/caderno-de-testes-email-service.md`, roteiro 2, passo 5): tirar a nota "Falha encontrada na execução de 2026-10-14" quando a correção estiver no `master` | T004 | | — |
| ~~T006~~ | Verificação manual (Leila): rodar o roteiro "Verificação manual (T006)" abaixo e conferir os resultados esperados | T004 | | — |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em qualquer
  ordem/em paralelo.
- A Issue, quando criada, leva o label `iteration-5`, além do label de tipo (`bug`).
- Marcar o ID como concluído (`~~T001~~` ou checkbox `[x]`) na mesma PR que resolve a task,
  junto com o registro da verificação (`memory/constitution.md`, "Rastreamento de trabalho via
  Issues").

## Verificação manual (T006)

Na raiz do repositório, no WSL, com o branch da correção. Só o `email-service` é necessário;
`--build` garante que a imagem tem a correção, e o `down -v` começa de um banco sem templates.

```bash
export COMPOSE_FILE=docker-compose.yml:docker-compose.blackbox.yml
docker compose down -v
docker compose up -d --wait --build db-email-service localstack
./scripts/test-api-key.sh restore
docker compose up -d --wait --build email-service
./scripts/test-api-key.sh restore
API_KEY=dak_IpfF8aaAizW6r1rSC59yi6BwMs4ox3GQDPiAWamRucU
```

1. Cadastrar um template com acento no assunto e no corpo:
   ```bash
   curl -s -w ' [%{http_code}]\n' -X POST http://localhost:8082/api/templates \
     -H "X-API-Key: $API_KEY" -H 'Content-Type: application/json' --data-binary @- <<'JSON'
   {"name":"acentos-assunto","subject":"{{nome}}, sua conta está pronta","body":"<p>Olá, {{nome}}! Sua conta está pronta.</p>"}
   JSON
   ```
   Esperado: `201`, com `"subject":"{{nome}}, sua conta está pronta"` na resposta.
2. Pré-visualizar:
   ```bash
   curl -s -w ' [%{http_code}]\n' -X POST http://localhost:8082/api/templates/acentos-assunto/preview \
     -H "X-API-Key: $API_KEY" -H 'Content-Type: application/json' \
     -d '{"variables":{"nome":"Maria"}}'
   ```
   Esperado:
   ```
   {"body":"<p>Olá, Maria! Sua conta está pronta.</p>","subject":"Maria, sua conta está pronta"} [200]
   ```
   Antes da correção, o mesmo comando devolve `"subject":"Maria, sua conta estÃ¡ pronta"`.

Para derrubar: `docker compose down -v`.

## Registro

- **T001** (2026-10-15): cenário e passos escritos. `mvn -pl email-service -am verify` contra o
  Compose local (Postgres, LocalStack): 122 testes, 1 falha, 2 pulados (`@requires-real-ses`,
  como antes). A falha é a do cenário novo, no passo do assunto: `expected: "Ada, sua conta está
  pronta"`, `but was: "Ada, sua conta estÃ¡ pronta"` (bytes conferidos no relatório do
  surefire). O passo do corpo não chega a rodar porque o do assunto falha antes; o corpo já sai
  certo hoje (diagnóstico do `plan.md`).
- **T002** (2026-10-15): `MimeRenderedTemplateParser` passou a usar
  `Session.getInstance` com `mail.mime.allowutf8=true`. `mvn -pl email-service -am verify`:
  122 testes, 0 falhas, 2 pulados (`@requires-real-ses`); o cenário de T001 passou, com o
  passo do corpo também verde.
- **T003** (2026-10-15): nenhuma classe de exceção criada e nenhum branch de exceção
  adicionado ou alterado; o `catch` do parser (`IllegalStateException`) ficou igual.
- **T004** (2026-10-15, em aberto): a suíte do email-service está verde (ver T002). A do `app`
  (`mvn -pl app -am verify`) rodou uma vez inteira com 284 testes e 1 falha:
  `EmailServiceClientIntegrationTest.previewWithIncompleteDataIsRejectedWithoutRetrying[1]`,
  por tempo (`0.2218486S` contra o limite de `0.2S`), no primeiro caso, o mais frio. Esses
  testes falam com o email-service do Compose (imagem antiga, sem esta correção) e o template
  deles não tem acento, então não exercitam esta mudança. A nova rodada só dessa classe não deu
  resultado: no meio dela o ambiente do Compose foi recriado de fora desta sessão
  (`db`, `db-email-service` e `localstack` recriados, email-service não), e todos os 37 testes
  falharam na subida com `409`/`404` no template `invite` (banco e SES fora de sincronia).
  Falta rodar `mvn -pl app -am verify` de novo com o ambiente recriado do zero.

## Verificação manual no WSL (2026-10-22)

Feita pela Leila com o roteiro único [`docs/verificacao-manual-wsl.md`](../../docs/verificacao-manual-wsl.md), Bloco 1, passo 4.

| Item | Resultado |
|---|---|
| 1. Template com acentos | `201`, `"subject":"{{nome}}, sua conta está pronta"` |
| 2. Pré-visualização | `{"body":"<p>Olá, Maria! Sua conta está pronta.</p>","subject":"Maria, sua conta está pronta"} [200]` |

## T004 e T005 (2026-10-22)

- **T004:** O CI de toda PR para `development` roda `mvn -B -pl app -am verify` e `mvn -B -pl email-service -am verify` contra a infraestrutura do Compose (`.github/workflows/ci.yml`). Verde nas duas suítes, entre outras, na PR #201 (2026-10-22, run [39709919144](https://github.com/lalgarve/jogo-acoes/actions/runs/39709919144)), já com esta spec em `development`.
- **T005:** a correção está no `master` (`f31f678`, PR #175). O caderno já foi refeito em 2026-10-15 com o
  serviço corrigido: o passo 5 do roteiro 2 mostra o assunto certo, seguido de um parágrafo curto que
  registra a falha achada na primeira execução e a correção. Esse parágrafo fica por decisão da Leila
  (`docs/disciplina/CLAUDE.md`, log de decisões; o apêndice B menciona a falha do mesmo jeito); a nota
  que a tarefa mandava tirar não existe mais.
