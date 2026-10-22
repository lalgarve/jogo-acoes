# Spec: Recriar no SES os templates que só existem no banco do email-service

**Status:** implementada; falta a suíte do `app` (T012). Verificação manual feita em 2026-10-22 (ver `tasks.md`)
**Issue:** [#144](https://github.com/lalgarve/jogo-acoes/issues/144)
**Iteração:** iteration-5

## Resumo

O `email-service` passa a tratar o próprio banco como fonte dos templates e recria no SES o que o
SES perdeu: depois da subida, em segundo plano, e quando uma operação sobre um template descobre
que ele não existe mais no SES. O serviço sobe mesmo sem o SES. Um template que sumiu do SES deixa de travar o `app` e deixa de exigir
`docker compose down -v`.

## Motivação

Cada template vive em dois lugares: na tabela `email_template` (assunto, corpo, schema de
variáveis) e no SES, como `<client>__<name>`. A spec 05-025 garante a ordem na escrita (o SES é
chamado antes do banco), mas nada reconcilia os dois quando o SES perde um template que o banco
ainda tem.

Achado na T002 da spec 05-035 (2026-10-08) e de novo nas specs 05-040 e 05-042: o LocalStack
guarda o SES em memória, então reiniciar só o LocalStack apaga os templates do SES e mantém o
banco. Na subida seguinte, a sincronização de templates do `app`
(`EmailTemplateSynchronizer` → `EmailServiceGateway.upsertTemplate`) falha e o `app` não sobe:

| Chamada | Resposta | Por quê |
|---|---|---|
| `PUT /templates/invite` | 404 | `SesTemplateClient.updateTemplate` recebe `TemplateDoesNotExistException` do SES |
| `POST /templates` | 409 | a linha ainda existe no banco |
| `upsertTemplate` | desiste | `Template invite answered 409 on create and 404 on update` |

O mesmo template perdido também quebra o preview (`POST /templates/{name}/preview` responde 404
com a linha no banco) e o envio (`POST /emails` aceita com 202, e a `email-lambda` falha no
`SendTemplatedEmail`).

Localmente isso acontece a cada reinício do LocalStack. Em produção, quando alguém apaga um
template do SES por fora do `email-service` (console, CLI, outra ferramenta).

Os contornos documentados hoje (`docker compose down -v`, ou `TRUNCATE` das tabelas de template e
envio, em `specs/05-042-.../tasks.md`) apagam dados para reconstruir o que o banco já sabe.

## Cenários (comportamento esperado)

Cenários novos em `email-service/src/test/resources/features/register_templates.feature`, numa
`Rule` própria ("email-service recreates on SES the templates SES lost"), escritos antes da
correção (ver `tasks.md`):

- atualizar um template que o SES perdeu;
- fazer preview de um template que o SES perdeu;
- cadastrar um template cujo nome sobrou no SES sem linha no banco.

A reconciliação na subida não é um cenário de cliente: é verificada por teste de integração
(`plan.md`, "Verificações").

## Requisitos funcionais

- **Fonte da verdade:** para templates do `email-service`, o banco manda. O SES guarda uma cópia
  que o serviço pode recriar a partir de `subject`/`body`/`ses_template_name` da linha.
- **O `email-service` sobe mesmo sem o SES** (decisão da Leila, 2026-10-14). Nenhuma chamada ao
  SES acontece durante a subida, e nenhuma falha do SES impede o serviço de aceitar requisições.
- **Reconciliação depois da subida, em segundo plano:** o serviço lista os templates do SES
  (`ListTemplates`, paginado por `NextToken`), compara com todas as linhas de `email_template`
  (de todos os clientes) e cria no SES cada template que só existe no banco, com o conteúdo do
  banco. Se o SES não responder, ou recusar algum template, o serviço registra a falha no log e
  tenta de novo mais tarde, até conseguir. A lógica de tentar de novo fica dentro do serviço.
- **A reconciliação não sobrescreve:** se o SES responder que o template já existe na hora de
  criar (outra instância, ou um `PUT` que chegou no meio), a reconciliação conta como feita e não
  troca o conteúdo. Assim ela nunca volta um template para o conteúdo antigo.
- **No `PUT /templates/{name}`:** se a linha existe e o SES responde que o template não existe, o
  serviço cria o template no SES com o conteúdo novo e responde 200, como numa atualização
  normal. O 404 continua só para "não existe linha deste cliente com este nome".
- **No `POST /templates/{name}/preview`:** se a linha existe e o SES responde que o template não
  existe, o serviço o recria a partir do banco e renderiza de novo. Responde 200 com o resultado.
- **No `POST /templates`, caso inverso:** se não existe linha e o SES já tem `<client>__<name>`
  (sobra de um cadastro que falhou depois do SES, ou de um banco apagado), o serviço sobrescreve
  o template do SES com o conteúdo pedido, grava a linha e responde 201. O prefixo
  `<client>__` pertence ao `email-service`.
- **Na reconciliação, caso inverso:** um template que existe no SES e não tem linha no banco não
  é apagado nem importado. O serviço registra um aviso no log com o nome dele.
- **Cadastro, atualização e preview com o SES fora do ar** respondem `503`, distinto do `422` de
  "o SES recusou o template" (hoje os dois viram `422`). Ver "Decisões em aberto", item 3.
- **Envio:** a publicação na fila SQS sai da chamada `POST /emails` e passa para uma thread do
  próprio serviço, com status e novas tentativas
  (spec [05-046](../05-046-publicacao-na-fila-em-segundo-plano/spec.md)). Quando a `email-lambda` encontra o
  template ausente no SES, o erro chega ao `email-service` por uma DLQ e o template é recriado
  ali, a partir do banco; a 05-046 especifica esse caminho.

## Requisitos não-funcionais

- A reconciliação faz uma chamada `ListTemplates` por página e uma `CreateTemplate` por template
  ausente; templates presentes nos dois lados não geram chamada extra.
- O `app` não muda nesta spec: com o `PUT` recriando o template, o caminho 404 → 409 do
  `EmailServiceGateway.upsertTemplate` deixa de acontecer no cenário da Issue.

## Fora de escopo

- **Publicação na fila em segundo plano** (`POST /emails` grava pendente, outra thread põe na
  fila): spec 05-046.
- **E-mails repetidos** (não enviar duas vezes o mesmo pedido): outra spec.
- **Conteúdo divergente:** template presente nos dois lados com assunto/corpo diferentes. A
  reconciliação compara só a presença pelo nome.
- **Reconciliação periódica** enquanto o serviço roda, depois da primeira que deu certo.
- Impedir que a divergência apareça durante o cadastro (falha do SES ou do banco no meio de um
  `POST`/`PUT`): é o escopo da Issue #136. Esta spec só garante que o `POST` não trava numa
  sobra do SES.

## Decisões resolvidas

- **Subida sem SES** (Leila, 2026-10-14): o `email-service` sobe mesmo sem o SES. Substitui a
  recomendação anterior de derrubar a subida quando a reconciliação falhasse.
- **Envio com o SES ou a fila indisponível** (Leila, 2026-10-14): `POST /emails` não falha por
  isso. Grava o envio como pendente e responde; a publicação na fila SQS passa para uma thread do
  serviço, que tenta de novo e decide quando desistir. Fica na spec 05-046.

- **Recriar no `PUT`/preview, caso inverso e `503`** (2026-10-14): a Leila pediu a implementação
  sem mudar as recomendações, que foram seguidas: recriar no `PUT` e no preview; na reconciliação,
  só aviso no log para o template que está no SES sem linha, e o `POST` sobrescreve a sobra; `503`
  nas rotas de template com o SES fora do ar (o `app` não sobe sem o SES).

## Decisões em aberto

Nenhuma.
