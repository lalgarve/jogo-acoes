# Spec: pré-visualização de template devolve o assunto com acentos corretos

**Status:** implementada (correção em `f31f678`); verificação manual T006 feita em 2026-10-22; faltam T004 e T005 (ver `tasks.md`)
**Issue:** [#190](https://github.com/lalgarve/jogo-acoes/issues/190)
**Iteração:** iteration-5

## Resumo

`POST /templates/{name}/preview` do email-service passa a devolver o assunto renderizado com os
caracteres acentuados certos (`"Maria, sua conta está pronta"`), como já acontece com o corpo.

## Motivação

Encontrado na execução de 2026-10-14 do caderno de testes do Serviço de E-mail, versão 2
(`docs/disciplina/caderno-de-testes-email-service.md`, roteiro 2, passo 5, branch
`claude/email-service-test-notebook-5id7pk`): depois de mudar o assunto do template para
`{{nome}}, sua conta está pronta`, a pré-visualização com `nome = Maria` devolve

```json
{
  "body": "<p>Olá, Maria! Sua conta está pronta.</p>",
  "subject": "Maria, sua conta estÃ¡ pronta"
}
```

O corpo e o assunto gravados em `email_template` estão certos; só o assunto da pré-visualização
vem corrompido. Reproduzido de novo em 2026-10-15 contra o email-service do Compose (porta 8082)
com a chave de teste do cliente `jogo-acoes`: o `á` volta como os bytes `c3 83 c2 a1` (os dois
bytes UTF-8 de `á` lidos como ISO-8859-1 e codificados de novo em UTF-8).

O envio de verdade não é afetado: o e-mail do roteiro 3 chega ao `ses-viewer` com o assunto
certo, porque o envio não passa pela pré-visualização (`SendTemplatedEmail` na `email-lambda`).

### Onde está o defeito

Diagnóstico feito nesta spec (detalhe em `plan.md`, "Diagnóstico"):

- O `TestRenderTemplate` do SES do LocalStack devolve a mensagem MIME com o cabeçalho
  `Subject:` em UTF-8 cru (`Subject: Maria, sua conta está pronta`), sem a codificação RFC 2047
  (`=?UTF-8?B?...?=`). O corpo vem em base64 com `charset="utf-8"` declarado.
- O `MimeRenderedTemplateParser` lê essa mensagem com o Jakarta Mail na configuração padrão,
  que interpreta bytes de 8 bits em cabeçalho como ISO-8859-1. É aí que o assunto se corrompe.
  O corpo escapa porque o charset dele é declarado na própria parte MIME.

A saída do LocalStack não segue o RFC 2047, mas é UTF-8 válido e legível (RFC 6532 permite
UTF-8 cru em cabeçalho). A correção fica no nosso parser, que tem de aceitar as duas formas: o
UTF-8 cru do LocalStack e o RFC 2047, caso o SES de verdade codifique o assunto assim.

## Cenários (comportamento esperado)

Cenário novo em `email-service/src/test/resources/features/register_templates.feature`, regra
"A client previews a template without sending an e-mail":

- `Client previews a template with accented characters in the subject and body`

Ele falha hoje (`expected: "Ada, sua conta está pronta"`, `but was: "Ada, sua conta estÃ¡
pronta"`) e passa depois da correção.

## Requisitos funcionais

- O assunto devolvido por `POST /templates/{name}/preview` é o assunto do template com as
  variáveis substituídas, com os caracteres não ASCII intactos (acentos, `ç`, `ã`, ...).
- O corpo continua como hoje (já vem certo).
- Um assunto só com ASCII continua como hoje.
- Um assunto codificado em RFC 2047 na resposta do SES continua sendo decodificado.

## Fora de escopo

- O envio (`POST /emails` → fila → `email-lambda` → `SendTemplatedEmail`): já chega certo.
- Mudar o LocalStack ou pedir a ele que codifique o cabeçalho.
- O `app`: ele não usa a pré-visualização no fluxo do jogo; o DTO `TemplatePreview` do cliente
  gerado só repassa o que o email-service devolve.

## Decisões em aberto

Nenhuma de requisito. A decisão técnica (como configurar o parser) está em `plan.md`.
