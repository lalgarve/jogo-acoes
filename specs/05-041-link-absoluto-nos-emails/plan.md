# Plan: Link absoluto nos e-mails de login e convite

Traduz `spec.md` em decisões técnicas. Valida contra `memory/constitution.md`.

## Contexto técnico

`LinkService.create` (módulo `link`) devolve `LinkCreationResult(id, token)`. Cada um dos quatro
pontos de envio monta `"/login-links/" + created.token()` e passa o resultado como `link` do
`EmailRequest`. A rota `GET /login-links/{token}` é a do consumo genérico de link: o
`LinkRouter` despacha pelo `serviceKey` gravado no `LinkRecord`, seja login
(`LoginLinkHandler`) ou competição (`CompetitionLinkHandler`). O caminho do link é, portanto, do
módulo `link`, não de quem o envia.

Configurações de endereço já existentes seguem um padrão: `email-service.base-url` vem de
`EMAIL_SERVICE_BASE_URL`, inclui o context-path `/api` e não tem padrão em
`application-staging.yml`/`application-production.yml`.

## Decisões de arquitetura

| Pergunta | Decisão | Status | Raciocínio |
|---|---|---|---|
| Onde montar a URL | No módulo `link`: `LinkCreationResult` ganha o campo `url` (`<base>/login-links/<token>`), preenchido por `LinkService.create`. Os quatro pontos passam `created.url()` no `EmailRequest`. | proposta | Um lugar só monta o caminho, o mesmo módulo que é dono da rota de consumo. Um helper chamado em cada ponto de envio (sugestão da Issue) manteria a regra espalhada em quatro chamadas e deixaria um ponto novo esquecer de usar. |
| Nome da propriedade | `links.base-url`, variável `LINKS_BASE_URL`. | resolvida (2026-10-13) | Prefixo do módulo que a lê, como `email-service.base-url`. Descartada: `app.base-url`, sugerida na Issue. |
| Valor por ambiente | `application-docker.yml`: `${LINKS_BASE_URL:http://localhost:8080/api}`. `application-staging.yml` e `application-production.yml`: `${LINKS_BASE_URL}`, sem padrão. Nada em `application.yml`. | resolvida (2026-10-13) | `docker` é o perfil padrão, então testes e `docker compose up` funcionam sem configurar nada. Staging/production seguem a regra de não fixar endereço no repositório; a variável ausente faz o placeholder falhar e o `app` não sobe. Produção recebe `https://jogoacoes.leilaalgarve.de/api` de quem opera o ambiente; staging fica sem valor até existir. |
| Validação | `@ConfigurationProperties("links")` + `@Validated`, record `LinksProperties(@NotBlank @Pattern(regexp = "^https?://\\S+$") String baseUrl)`. Barra final removida ao montar a URL. | proposta | Erro na inicialização em vez de e-mail com link quebrado. Nenhuma classe de exceção nova: a falha é o `BindValidationException` do próprio Spring, coberto por teste com `ApplicationContextRunner` (base ausente, base relativa, base sem esquema). |
| `sent_email.link` | Sem migration. | resolvida | A coluna é `VARCHAR(2048)` (V4), folga de sobra para a URL absoluta. |
| Testes Cucumber do `app` | Não mudam: leem o token do `LinkRecord`, não do e-mail. Um teste de integração novo confere `sent_email.link` igual a `http://localhost:8080/api/login-links/<token>` para os quatro pontos de envio. | proposta | Verifica o requisito no lugar onde o stub grava o que seria enviado, com o PostgreSQL real. |
| `blackbox-tests` | `_token_from_link` (em `seed/flows.py` e `features/steps/public_competition_entry_steps.py`, e o uso em `manage_active_sessions_steps.py`) passa a pegar o segmento depois de `/login-links/`. Os testes de `tests/test_mailbox.py` ganham um caso com link absoluto. | proposta | Hoje fazem `link.strip("/").split("/")[1]`, que com `http://localhost:8080/api/login-links/<token>` devolve string vazia: a suíte quebra se não mudar junto. |
| Scripts do caderno | Sem mudança de código: `login-jogador.sh` e `.ps1` já cortam pelo `/login-links/`. Atualizar só o texto de `docs/disciplina/caderno-de-testes.md` que diz que o link é relativo (seção "Como clicar no link do e-mail") e as saídas de exemplo que mostram o link. | proposta | O caderno passa a poder abrir o link direto. |

## Estrutura de módulos/pacotes

- `link/LinksProperties.java` (novo), registrado com `@EnableConfigurationProperties` numa
  configuração do próprio módulo `link`. É o primeiro `@ConfigurationProperties` do `app` (hoje as
  propriedades são lidas com `@Value`); escolhido pela validação na inicialização.
- `link/LinkCreationResult.java`: campo `url`.
- `link/LinkService.java`: monta a URL.
- `loginsession/LoginController.java`, `competition/CompetitionService.java`,
  `competition/EntryRequestService.java`, `competition/PlayerManagementService.java`: passam
  `created.url()`.
- `application-docker.yml`, `application-staging.yml`, `application-production.yml`.
- `blackbox-tests/`: extração do token.
- `docs/disciplina/caderno-de-testes.md`: texto sobre o link.

Nenhuma dependência nova entre módulos: os quatro pontos já dependem de `link`.

## Verificações (antes das correções, falhando)

1. Teste de integração: para cada um dos quatro pontos de envio, `sent_email.link` é
   `http://localhost:8080/api/login-links/<token>` (falha hoje: o valor é relativo).
2. Teste com `ApplicationContextRunner`: base ausente, `localhost:8080/api` e `/api` impedem o
   contexto de subir; `http://localhost:8080/api/` gera o link sem `//`.
3. `blackbox-tests/tests/test_mailbox.py`: extração do token a partir de link absoluto.
4. Manual (Leila, no Windows): com `docker compose up`, pedir login, abrir o e-mail no
   `ses-viewer` e conferir que o link aparece com `/` e faz o login ao ser aberto.

## Riscos e trade-offs

- **Link aponta para a API, não para uma página.** Abrir o link no navegador faz o login e
  mostra o JSON `{"redirectTo": ...}`. Funciona, mas não é a experiência final; fica para quando
  houver frontend (fora de escopo na `spec.md`).
- **Staging sem base definida.** O `app` em staging não sobe até alguém definir
  `LINKS_BASE_URL`. É o comportamento desejado (falhar cedo), mas precisa estar na lista de
  variáveis de quem montar staging.
- **A barra invertida no Windows pode ter outra causa.** A análise da `spec.md` é inferência; se
  a verificação manual 4 ainda mostrar `\` com o link absoluto, a causa está em quem exibe o
  e-mail e vira Issue própria.
