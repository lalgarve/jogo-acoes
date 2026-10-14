# Spec: Recriar no SES os templates que só existem no banco do email-service

**Status:** rascunho
**Issue:** [#144](https://github.com/lalgarve/jogo-acoes/issues/144)
**Iteração:** iteration-5

## Resumo

O `email-service` passa a tratar o próprio banco como fonte dos templates e recria no SES o que o
SES perdeu: na subida do serviço e quando uma operação sobre um template descobre que ele não
existe mais no SES. Um template que sumiu do SES deixa de travar o `app` e deixa de exigir
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
- **Na subida:** antes de o `email-service` aceitar requisições, ele lista os templates do SES
  (`ListTemplates`, paginado por `NextToken`), compara com todas as linhas de `email_template`
  (de todos os clientes) e cria no SES cada template que só existe no banco, com o conteúdo do
  banco.
- **No `PUT /templates/{name}`:** se a linha existe e o SES responde que o template não existe, o
  serviço cria o template no SES com o conteúdo novo e responde 200, como numa atualização
  normal. O 404 continua só para "não existe linha deste cliente com este nome".
- **No `POST /templates/{name}/preview`:** se a linha existe e o SES responde que o template não
  existe, o serviço o recria a partir do banco e renderiza de novo. Responde 200 com o resultado.
- **No `POST /templates`, caso inverso:** se não existe linha e o SES já tem `<client>__<name>`
  (sobra de um cadastro que falhou depois do SES, ou de um banco apagado), o serviço sobrescreve
  o template do SES com o conteúdo pedido, grava a linha e responde 201. O prefixo
  `<client>__` pertence ao `email-service`.
- **Na subida, caso inverso:** um template que existe no SES e não tem linha no banco não é
  apagado nem importado. O serviço registra um aviso no log com o nome dele.
- **Falha na subida:** se o SES não responder ou rejeitar a recriação de algum template, o
  `email-service` não sobe, e a mensagem diz quais templates não foram recriados e por quê.
- Duas instâncias reconciliando ao mesmo tempo não falham: se a criação no SES responder que o
  template já existe, a reconciliação conta como feita.
- Nenhum status HTTP novo no contrato: `docs/openapi-email-service.yaml` só ganha a descrição do
  comportamento novo em `PUT`, preview e `POST /templates`.

## Requisitos não-funcionais

- A reconciliação na subida faz uma chamada `ListTemplates` por página e uma `CreateTemplate` por
  template ausente; templates presentes nos dois lados não geram chamada extra.
- O `app` não muda: com o `PUT` recriando o template, o caminho 404 → 409 do
  `EmailServiceGateway.upsertTemplate` deixa de acontecer no cenário da Issue.

## Fora de escopo

- **Envio com template ausente no SES (`POST /emails`).** O `email-service` não consulta o SES no
  envio, e passar a consultar custaria uma chamada por e-mail. A reconciliação na subida do
  `email-service` e a sincronização do `app` (que faz `PUT` de todos os templates ao subir) cobrem
  os casos conhecidos.
- **Conteúdo divergente:** template presente nos dois lados com assunto/corpo diferentes. A
  reconciliação compara só a presença pelo nome.
- **Reconciliação periódica** (agendada) enquanto o serviço roda.
- Impedir que a divergência apareça durante o cadastro (falha do SES ou do banco no meio de um
  `POST`/`PUT`): é o escopo da Issue #136. Esta spec só garante que o `POST` não trava numa
  sobra do SES.

## Decisões em aberto

Perguntas da Issue #144 e as que apareceram ao escrever esta spec. Cada uma tem uma recomendação,
já usada nos requisitos acima; falta a confirmação da Leila.

1. **Na subida, no caminho que falha, ou nos dois?** Recomendação: nos dois. Só a subida não
   resolveria o caso da Issue, em que só o LocalStack reinicia e o `email-service` continua no
   ar. Só o caminho que falha deixaria o preview e o envio quebrados até alguém fazer `PUT`.
2. **Caso inverso (template no SES sem linha no banco).** Recomendação: na subida, só um aviso no
   log, sem apagar nem importar (importar exigiria inventar `variables_schema`; apagar arrisca
   algo que outra ferramenta criou). No `POST /templates`, sobrescrever o template do SES, porque
   o nome `<client>__<name>` é do `email-service` e hoje esse caso responde 422 e trava o cadastro.
3. **Falha da reconciliação na subida derruba o serviço?** Recomendação: sim. Sem o SES o serviço
   não funciona de qualquer forma, e subir com templates faltando esconderia o erro até o
   primeiro envio falhar na Lambda.
4. **Envio com template ausente fica de fora?** Recomendação: sim, pelos motivos em "Fora de
   escopo".
