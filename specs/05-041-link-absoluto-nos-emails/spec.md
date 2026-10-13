# Spec: Link absoluto nos e-mails de login e convite

**Status:** rascunho (decisões em aberto abaixo)
**Issue:** [#92](https://github.com/lalgarve/jogo-acoes/issues/92)
**Iteração:** iteration-5

## Resumo

Todo e-mail que leva um link de acesso (login, convite, pedido de entrada, registro) passa a
levar uma URL absoluta, `<base>/login-links/{token}`, em vez do caminho relativo
`/login-links/{token}` de hoje. A base vem da configuração de cada ambiente.

## Motivação

Os quatro pontos do `app` que montam o link concatenam só o caminho:

| Arquivo | Linha (master `8fc6db2`) |
|---|---|
| `loginsession/LoginController.java` | 84 |
| `competition/CompetitionService.java` | 123 |
| `competition/EntryRequestService.java` | 130 |
| `competition/PlayerManagementService.java` | 127 |

Esse valor vira a variável `link` do `TemplateData` (`EmailServiceEmailSender`, linha 65) e cai
direto no `href="{{link}}"` e no texto "copie e cole este link" dos templates
(`app/src/main/resources/email-templates/`). Num cliente de e-mail não existe origem contra a qual
resolver um caminho relativo, então o link não funciona para quem recebe. O e-mail registrado no
SES do LocalStack confirma (exemplo de 2026-10-13):

```json
"TemplateData": "{\"name\":\"Administrator\",\"link\":\"/login-links/cbce55b1-b9df-4ac5-9db9-5f336a8a5da2\"}"
```

Os testes não pegaram isso porque quem consome o link (suíte `blackbox-tests`, scripts do caderno
de testes) já conhece a URL da API e extrai só o token.

### A barra invertida no Windows

No Windows o fim do link apareceu com `\` em vez de `/`. O código do `app` não usa o separador
do sistema: os quatro pontos acima concatenam a string literal `"/login-links/"`, e nenhum código
de produção de `app`, `email-service` ou `email-lambda` usa `File.separator`, `Path` ou
`FileSystems` (busca no master `8fc6db2`). O link sai igual em qualquer sistema operacional, como
mostra o JSON acima, todo com `/`.

Duas causas possíveis, ambas fora do código e ambas ligadas ao link ser relativo ou a como ele é
lido (inferência, não reproduzida no Windows):

1. **Cliente de e-mail tratando o caminho como arquivo.** Sem esquema (`http://`), um cliente no
   Windows pode interpretar `/login-links/<token>` como caminho local e exibi-lo com `\`. Um link
   absoluto `http(s)://` elimina essa interpretação.
2. **Escape do JSON cru.** O `TemplateData` é uma string JSON dentro do JSON de
   `GET /_aws/ses`, então o link termina em `5da2\"`: o `\` é o escape da aspa, não parte do
   link. Copiar o link desse JSON à mão leva o `\` junto. Isso independe do sistema operacional e
   do link ser absoluto; quem lê pelo `ses-viewer`, pelos scripts do caderno (que decodificam o
   `TemplateData`) ou pela tabela `sent_email` não vê o `\`.

O critério de aceite inclui conferir no Windows que o link do e-mail abre como URL.

## Requisitos funcionais

- Todo link de acesso enviado por e-mail é uma URL absoluta: `<base>/login-links/{token}`, onde
  `<base>` já inclui o context-path `/api` (mesma convenção de `EMAIL_SERVICE_BASE_URL` e de
  `API_BASE_URL` nos scripts do caderno).
- Vale para os quatro pontos de envio acima e para qualquer novo ponto que envie link.
- A base por ambiente:
  - `docker` (inclui `docker,blackbox`): `http://localhost:8080/api` por padrão, sobrescrevível
    por variável de ambiente.
  - `production`: `https://jogoacoes.leilaalgarve.de/api` (valor da Issue #92), vindo da
    variável de ambiente.
  - `staging`: ainda não definida. Vem da variável de ambiente, sem padrão.
- Base ausente em `staging`/`production`, ou base que não seja URL `http`/`https` absoluta → o
  `app` não sobe (erro de configuração na inicialização, não um e-mail com link quebrado).
- Barra no fim da base é tolerada: `http://localhost:8080/api/` gera o mesmo link que
  `http://localhost:8080/api` (sem `//login-links`).
- O link gravado em `sent_email.link` (envio pelo stub) é o mesmo link absoluto enviado.

## Requisitos não-funcionais

- O link não depende do sistema operacional onde o `app` roda nem de onde o e-mail é lido.

## Critérios de aceite

- Um e-mail de login pedido em `docker compose up` chega ao SES do LocalStack com
  `link = http://localhost:8080/api/login-links/<token>`, e esse link, aberto direto, faz o login
  (`GET /login-links/{token}`).
- O mesmo vale para convite (competição privada), pedido de entrada em competição pública e
  inclusão de jogador por administrador.
- No Windows, o link do e-mail aberto pelo `ses-viewer` (ou pelo `.eml` exportado) aparece com
  `/` e abre a URL. Verificação manual da Leila.
- Suíte `blackbox-tests` e scripts do caderno continuam passando com o link absoluto.

## Fora de escopo

- Frontend: o link continua apontando para a API (`GET /login-links/{token}` devolve JSON e o
  cookie de sessão). Trocar o destino para uma página web fica para quando houver frontend.
- Outros links que o sistema venha a montar fora dos e-mails.
- Mudar como o `email-service`/`email-lambda` renderizam os templates.

## Decisões em aberto

1. **Nome da propriedade e da variável.** Proposta: `links.base-url` / `LINKS_BASE_URL`. Ver
   `plan.md`.
2. **Produção com padrão no arquivo ou só por variável.** Proposta: só por variável, sem padrão,
   como `EMAIL_SERVICE_BASE_URL` (o `application-production.yml` não fixa endereços hoje). A
   alternativa é fixar `https://jogoacoes.leilaalgarve.de/api` no `application-production.yml`.
3. **Base de staging.** Fica sem valor até staging existir; o `app` em staging não sobe sem ela.
