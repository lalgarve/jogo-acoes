# Spec: Caderno de testes por funcionalidade

**Status:** implementada (ver `tasks.md`)
**Issue:** [#157](https://github.com/lalgarve/jogo-acoes/issues/157)
**Iteração:** iteration-5
**Substitui:** [`05-012-caderno-de-testes-etapa1`](../05-012-caderno-de-testes-etapa1/spec.md)
**Depende de:** [`05-018-gerador-dados-teste`](../05-018-gerador-dados-teste/spec.md) (massa de
dados), [`05-039-busca-de-jogadores`](../05-039-busca-de-jogadores/spec.md) (roteiro 1,
`POST /players/search`)

## Resumo

Reescrever `docs/disciplina/caderno-de-testes.md` como um roteiro de testes manuais organizado
pelas funcionalidades do sistema (login, competições), e não pelos critérios de cada Etapa da
disciplina. O caderno parte de uma massa de dados pronta (dump do banco, versionado em
`docs/disciplina/`), explica como subir o sistema e as ferramentas auxiliares, e traz em cada
passo o JSON pronto para copiar, o trecho de log esperado e o `SELECT` que confirma o resultado.

## Motivação

O primeiro caderno (spec 05-012) foi organizado por critério da Etapa 1: cada "caso" demonstra
um requisito da disciplina (fluxo Controller→Service→Repository, Bean Validation, exceção
centralizada...) usando `/sessions` como exemplo. Na prática:

- quem executa o caderno não pensa em "Etapa", pensa em "o que o sistema faz" — o roteiro fica
  difícil de seguir e não testa as funcionalidades de ponta a ponta;
- ele parte de um banco vazio e prepara os dados à mão, com passos espalhados pelo texto;
- os corpos de requisição aparecem em prosa no meio de frases, difíceis de copiar;
- o login em vários dispositivos, que exige repetir o pedido de link com cabeçalhos diferentes,
  não tem como ser feito de forma prática só pelo Swagger UI.

A versão nova mantém o que funcionou (log esperado e `SELECT` de verificação em cada passo) e
corrige o resto.

## Cenários (comportamento esperado)

Não aplicável — documento de teste manual e scripts de apoio, sem `.feature` novo nem mudança
de comportamento do sistema (mesmo caso da spec 05-012).

## Requisitos funcionais

### Organização do caderno

- Seções por funcionalidade do sistema (`## Login`, `## Competições`), cada uma com roteiros
  numerados. Nenhuma seção por Etapa da disciplina; a correspondência roteiro → critério da
  disciplina, se for necessária, fica no documento de entrega, não no caderno.
- Antes dos roteiros, três seções de preparação:
  1. **Gerar os dados de teste** — como rodar o gerador Python (spec 05-018) com o relógio no
     passado. Serve para quem quiser recriar a massa; quem só vai executar o caderno usa o dump.
  2. **Inicializar os dados de teste** — como restaurar o dump do banco que está em
     `docs/disciplina/`.
  3. **Subir o sistema e acessar os programas auxiliares** — comando para subir, e endereço de
     cada ferramenta: Swagger UI, Adminer (banco), visualizador de e-mails do SES, logs da
     aplicação.
- Cada roteiro tem: objetivo (uma frase), pré-condições (quais dados da massa usa), passos
  numerados, e para cada passo que chama a API: operação, JSON do corpo (quando houver),
  resposta esperada, trecho de log esperado e `SELECT` de verificação (quando o passo grava ou
  muda algo no banco).

### Formato dos passos

- Todo corpo JSON de `POST`/`PATCH` fica sozinho num bloco de código ` ```json `, completo e
  válido, pronto para colar no Swagger UI — nunca descrito em prosa nem com campos soltos no
  meio do texto. Valores que dependem da execução (id de competição, e-mail do jogador escolhido)
  aparecem como marcador `<...>` explicado logo antes do bloco.
- Trechos de log e resultados de `SELECT` são capturados executando o sistema de verdade sobre a
  massa do dump, como no caderno anterior.
- "Clicar no link do e-mail" é descrito de forma executável: onde ver o e-mail (visualizador do
  SES) e o que fazer com o link recebido.

### Roteiros de login

Incorporados do rascunho da Leila (2026-10-12), com o texto final escrito na implementação.

**1. Logar como administrador**

1. Pedir o e-mail de login do administrador.
2. Clicar no link do e-mail de login do administrador.
3. Listar todos os jogadores (`POST /players/search`, sem filtro).
4. Escolher três jogadores com números diferentes de competições públicas e privadas (usados
   nos roteiros seguintes como jogadores A, B e C).
5. Fazer logout do administrador.
6. Listar todos os jogadores de novo e verificar a mensagem de erro (`401`).

**2. Deslogar de outro dispositivo**

1. Navegador 1: pedir o e-mail de login do jogador A.
2. Navegador 1: clicar no link do e-mail do jogador A.
3. Navegador 1: listar as competições do jogador A.
4. Navegador 2: abrir o Swagger UI.
5. Navegador 2: pedir o e-mail de login do jogador A.
6. Navegador 2: clicar no link do e-mail.
7. Navegador 2: listar os dispositivos logados (duas sessões).
8. Navegador 2: encerrar a sessão do navegador 1.
9. Navegador 1: listar os dispositivos logados e verificar a mensagem de erro (`401`), porque
   a sessão deste navegador foi encerrada no passo 8.
10. Navegador 2: fazer logout.

**3. Logar em mais dispositivos que o máximo permitido**

Introdução no caderno: como é preciso logar quatro vezes, cada vez com cabeçalhos de
dispositivo diferentes, usamos scripts para o login. Há outras soluções possíveis, mas essa é a
mais simples. Os scripts existem para WSL (bash, exige `jq`) e para PowerShell:

- `login-jogador <número> <email>` — lê o arquivo JSON do dispositivo `<número>` para montar os
  cabeçalhos; pede o link de login; lê o e-mail pela API do LocalStack (se ainda não chegou,
  tenta de novo a cada 2 s, até 5 vezes); faz o login e grava o cookie como `cookie<número>`.
- `dispositivos-jogador <número>` — mostra os dispositivos em que o jogador está logado, usando
  o cookie `cookie<número>`.

Execução:

1. Swagger UI: pedir o e-mail de login do jogador B; clicar no link; listar os dispositivos.
2. `login-jogador 1 <email B>` e `dispositivos-jogador 1` — jogador logado em 2 dispositivos.
3. `login-jogador 2 <email B>` e `dispositivos-jogador 2` — jogador logado em 3 dispositivos.
4. `login-jogador 3 <email B>` e `dispositivos-jogador 3` — continua em 3 dispositivos, e o
   primeiro (o login pelo Swagger UI) foi deslogado.
5. Swagger UI: listar os dispositivos e verificar o erro (`401`).

### Roteiros de competições (novos)

**4. Criar uma competição pública e entrar como jogador novo**

1. Logar como administrador (atalho: passos 1–2 do roteiro 1).
2. Criar uma competição pública (`POST /competitions`, JSON completo).
3. Sem sessão (outro navegador ou depois do logout): listar as competições públicas e achar a
   nova.
4. Pedir entrada com um e-mail que nunca foi usado (`POST .../entry-requests` com `email` e
   `captchaToken`).
5. Ler o e-mail de cadastro, completar o cadastro com o nome (`POST
   /login-links/{token}/registration`), o que já abre a sessão.
6. Listar "minhas competições" e ver a nova em `participating`.
7. `SELECT`s: a competição criada, o usuário novo e a participação `IN_COMPETITION` com
   `requestType = REQUEST`.

**5. Criar uma competição privada e convidar jogadores**

1. Logar como administrador.
2. Criar uma competição privada com três e-mails: um jogador já registrado da massa e dois
   e-mails novos.
3. Decidir enviar os convites depois (`timing: later`) e verificar que a competição fica
   `AWAITING_INVITES` e os jogadores `EMAIL_NOT_SENT`.
4. Enviar os convites para o grupo (`POST .../players/invite-emails`) e verificar
   `EMAIL_SENT`.
5. Confirmar que a competição privada não aparece nas públicas e que um pedido de entrada de
   quem não foi convidado responde `404`.
6. O jogador já registrado clica no convite e confirma a entrada; um dos e-mails novos clica e
   completa o cadastro.
7. Listar os jogadores da competição filtrando por status e conferir quem está
   `IN_COMPETITION` e quem continua `EMAIL_SENT`.

**6. Gerenciar os jogadores de uma competição privada**

1. Logar como administrador, usando a competição privada do roteiro 5 (ou uma da massa com
   convites pendentes).
2. Corrigir o e-mail de um convite pendente (`PATCH .../players/{participationId}`) e reenviar
   o convite só para ele.
3. Tentar corrigir com um e-mail inválido e verificar o `400` com a mensagem de erro do
   contrato.
4. Cancelar um convite pendente (`DELETE .../players/{participationId}`).
5. `SELECT`s antes e depois de cada mudança.

**7. Ver as competições de um jogador com histórico**

1. Escolher na massa um jogador que participa de várias competições, incluindo uma já
   terminada e um convite ainda não confirmado.
2. Logar como esse jogador.
3. Listar "minhas competições" e conferir os quatro grupos (`participating`,
   `pastParticipations`, `pendingConfirmation`, `created`).
4. Abrir o detalhe da competição pendente, ver `canConfirmEntry = true` e confirmar a entrada
   (`POST .../entry-requests` sem corpo, com sessão).
5. Listar de novo e ver a competição mudar de `pendingConfirmation` para `participating`.
6. Abrir o detalhe de uma competição de que ele não participa e verificar o `404`.

### Trabalho de apoio (a fazer na implementação, não nesta PR)

1. **Gerar os dados de teste do passado com Python** — rodar o gerador da spec 05-018 com o
   relógio deslocado e conferir que a massa cobre o que os roteiros 1–7 precisam (jogadores com
   contagens diferentes de competições públicas e privadas, competição terminada, convite
   pendente). O que faltar entra no catálogo do gerador.
2. **Dump do banco de teste em `docs/disciplina/`** — depois de gerar a massa, fazer o dump do
   banco da aplicação e versionar em `docs/disciplina/`, com um script de restauração. O caderno
   parte desse dump.
3. **Criar os scripts descritos no caderno** — `login-jogador` e `dispositivos-jogador` em bash
   (WSL) e PowerShell, e os scripts de dump e restauração. Todo script escreve na tela o que
   está fazendo a cada passo (qual operação chama, qual arquivo lê, quantas tentativas de ler o
   e-mail, qual cookie grava), para que a execução sirva de explicação durante a demonstração.

## Requisitos não-funcionais

- Os scripts rodam no Windows: bash no WSL (com `curl` e `jq`) e PowerShell nativo, sem
  dependência além dessas.
- Nada disso entra em módulo de produção (`app/`, `email-lambda/`): scripts e dump ficam em
  `docs/disciplina/` (regra do `CLAUDE.md` sobre código de teste).
- Valores de teste apenas — o dump nunca é usado em `staging`/`production`.

## Fora de escopo

- Interface web: os roteiros usam o Swagger UI, os scripts e as ferramentas auxiliares.
- Roteiros de negociação de ações — a funcionalidade ainda não existe.
- Geração do PDF/`.tex` — continua a regra de `docs/disciplina/CLAUDE.md` ("Markdown primeiro").
- Corrigir o link relativo nos e-mails (Issue #92): o caderno descreve como usar o link como ele
  é hoje.

## Decisões em aberto

Nenhuma. Resolvidas com a Leila em 2026-10-12:

- Roteiro 1, "listar todos os jogadores": usa a busca da spec 05-039 (`POST /players/search`),
  mesclada no mesmo dia.
- Roteiro 2, passo 9: a listagem é feita no navegador 1, cuja sessão foi encerrada, e deve dar
  `401`.
