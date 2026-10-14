# Spec: Caderno de testes do Serviço de E-mail, versão 2 (execução real com LocalStack Pro)

**Status:** rascunho
**Issue:** a criar
**Iteração:** iteration-5 (Etapa 4 da disciplina: caderno de testes da entrega)

## Resumo

A versão 1 do [caderno de testes do Serviço de E-mail](../../docs/disciplina/caderno-de-testes-email-service.md)
foi escrita antes da implementação das specs 05-047 e 05-048: as respostas esperadas vêm do
contrato e das specs, e o próprio caderno diz que "os trechos de log e os `SELECT`s de
verificação entram na versão 2, capturados executando o serviço de verdade". Esta spec faz a
versão 2: executar todos os roteiros contra o sistema de pé e registrar em cada passo a resposta
real, o trecho de log e o `SELECT` que confirma o estado gravado, no mesmo formato do caderno do
`jogo-acoes` (spec 05-040).

O envio de e-mail só fecha o ciclo quando a `email-lambda` roda no LocalStack e entrega o e-mail
ao SES. Para isso o Docker Compose passa a usar a imagem do LocalStack Pro
(`localstack/localstack-pro`), com o token de licença lido do ambiente de quem roda, nunca do
repositório.

## Motivação

- **Entrega da disciplina:** o caderno é a evidência dos testes manuais das Etapas 2 a 4
  (Serviço de E-mail, mensageria assíncrona, verificação de domínio e processamento em lote).
  A versão 1 só traz o comportamento esperado; falta a prova de que o sistema faz isso.
- **Preferências já registradas para o caderno** (spec 05-040): organizado por funcionalidade,
  JSON em bloco fácil de copiar, `SELECT`s e logs mantidos, scripts que dizem o que estão
  fazendo, comandos completos prontos para copiar (constitution, "Verificação manual: comandos
  prontos para copiar").
- **LocalStack Pro:** decisão da Leila (2026-10-14). O Compose ainda aponta para
  `localstack/localstack:4`. A troca vale para todo mundo que usa o Compose (WSL da Leila,
  ambiente de nuvem da Claude e CI), por isso entra no repositório e não só num arquivo local.

## Cenários (comportamento esperado)

Não há `.feature` novo: o caderno é documentação de teste manual, e a troca de imagem não muda
o comportamento de nenhum serviço. Os cenários desta spec são os do próprio caderno:

1. **Preparação:** a sequência da seção 1 do caderno sobe o ambiente com o LocalStack Pro, a
   licença ativa e a `email-lambda` implantada, sem nenhum passo além do que está escrito (o
   token é exportado antes, como diz o caderno).
2. **Sem token:** com `LOCALSTACK_AUTH_TOKEN` vazio ou ausente, o LocalStack para na subida com a
   mensagem de licença do próprio LocalStack, e o caderno e o README dizem o que fazer.
3. **Roteiros 1 a 6** executados de ponta a ponta. Para cada passo, o caderno passa a trazer:
   - a resposta real (status e corpo), no lugar da resposta tirada do contrato quando as duas
     diferem;
   - o trecho de log do `email-service` que mostra a chamada passando pelo controller e pelos
     repositórios certos;
   - o `SELECT` que confirma o estado no banco `email_service`, com o resultado;
   - no envio (roteiro 3 e passos de envio dos roteiros 4 e 5): o log da `email-lambda` que
     consumiu a mensagem e o e-mail no SES (`/_aws/ses` e `ses-viewer`).
4. **Divergências:** quando o sistema real responder diferente da versão 1, o caderno registra o
   comportamento real e a divergência vira item em "Achados" no fim da execução, para a Leila
   decidir se é bug (spec própria) ou erro do caderno.

## Fora do escopo

- Swagger UI do `email-service` (spec 05-049, ainda rascunho). Se ela for implementada antes, a
  versão 2 usa a tela; senão, continua com o `chamar.sh`.
- Corrigir bugs que a execução encontrar: cada um vira spec própria.
- O caderno do `jogo-acoes` (05-040) não é refeito; só herda a troca de imagem do LocalStack, sem
  mudança de texto além do token na preparação.
- LocalStack dos testes da `email-lambda` (Quarkus Dev Services): continua com a imagem padrão do
  Quarkus, porque esses testes não executam a Lambda.

## Decisões em aberto

1. **Token ausente:** `${LOCALSTACK_AUTH_TOKEN:-}` no Compose (o LocalStack para com a mensagem
   de licença; os outros serviços continuam subindo sem token) ou `${LOCALSTACK_AUTH_TOKEN:?…}`
   (o Compose recusa qualquer comando sem token, mesmo `docker compose up db`). **Proposta:**
   `:-`, porque o segundo quebra comandos que nem usam o LocalStack.
2. **CI:** o workflow passa a ler o segredo `LOCALSTACK_AUTH_TOKEN` do repositório (a Leila cria
   o segredo no GitHub). **Proposta:** sim, na mesma PR da troca de imagem, senão o CI quebra.
3. **Versão da imagem:** `localstack/localstack-pro:4` (mesma tag de hoje, só troca o
   repositório da imagem) ou uma versão exata. **Proposta:** `:4`, igual ao que já existe.
