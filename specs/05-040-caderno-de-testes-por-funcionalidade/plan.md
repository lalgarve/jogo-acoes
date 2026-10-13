# Plan: Caderno de testes por funcionalidade

Traduz `spec.md` em decisões técnicas. Valida contra `memory/constitution.md`.

## Contexto técnico

- O caderno atual é `docs/disciplina/caderno-de-testes.md` (spec 05-012), organizado por
  critério da Etapa 1.
- O gerador de dados existe: `blackbox-tests/seed/` (spec 05-018), roda só por HTTP contra o
  ambiente `blackbox` com o relógio deslocado por `scripts/blackbox-clock-offset.sh`.
- O ambiente `blackbox` é o `docker-compose.yml` mais o overlay `docker-compose.blackbox.yml`:
  perfil `docker,blackbox`, captcha sempre aceito, administrador
  `success+admin@simulator.amazonses.com`.
- Os e-mails não saem de verdade: ficam no SES do LocalStack, legíveis em
  `GET http://localhost:4566/_aws/ses` (JSON) e no visualizador `ses-viewer`
  (`http://localhost:3005`). O link no corpo do e-mail é relativo (`/login-links/<token>`,
  Issue #92).
- Banco da aplicação: PostgreSQL do serviço `db`, porta 5432, schema `jogo_acoes`, histórico do
  Flyway em `jogo_acoes_schema_history`. Adminer em `http://localhost:8081`.
- Limite de dispositivos: `login.max-devices-per-user: 3` (`application.yml`); o quarto login
  encerra a sessão mais antiga.

## Decisões de arquitetura

| Pergunta | Decisão | Status | Raciocínio |
|---|---|---|---|
| Arquivo do caderno | Reescrever `docs/disciplina/caderno-de-testes.md` no lugar, sem manter a versão por Etapa. A spec 05-012 passa a "substituída pela 05-040". | proposta | Um caderno só; o histórico da versão antiga fica no git. |
| Arquivos de apoio | Pasta `docs/disciplina/caderno-de-testes/` com `scripts/` (bash e PowerShell), `dispositivos/` (JSON de cabeçalhos) e `dados/` (dump). | proposta | Tudo que o caderno usa fica ao lado dele, dentro de `docs/disciplina/` como pedido, e fora dos módulos de produção. |
| Ambiente em que o caderno roda | `docker compose -f docker-compose.yml -f docker-compose.blackbox.yml up`, com o relógio real. | proposta | O roteiro 4 pede entrada em competição pública, que exige captcha; só o perfil `blackbox` aceita captcha vazio. O relógio deslocado só é necessário para gerar a massa, não para executar o caderno. |
| Como a massa chega ao banco | Dump SQL versionado, restaurado por script. O gerador Python fica documentado para quem quiser recriar a massa. | proposta | Gerar a massa exige subir com `faketime` e rodar o gerador; restaurar um dump é um comando. Também garante que os ids e e-mails citados nos roteiros e nos `SELECT`s sejam sempre os mesmos. |
| Formato e escopo do dump | `pg_dump` em SQL puro (`--schema=jogo_acoes`), incluindo `jogo_acoes_schema_history`, sem os dados de `spring_session`, `spring_session_attributes` e `login_session` (`--exclude-table-data`). Arquivo: `dados/jogo-acoes-massa.sql`. | proposta | SQL puro dá diff legível. O histórico do Flyway precisa ir junto para a aplicação aceitar o schema restaurado. Sessões abertas no momento do dump ocupariam vagas de dispositivo dos jogadores e atrapalhariam os roteiros 2 e 3. |
| Restauração | Script `restaurar-massa` (bash e PowerShell): com o `app` parado, apaga e recria o schema `jogo_acoes`, carrega o dump pelo container `db` (`docker compose exec -T db psql`), e avisa para subir o `app` depois. Recusa rodar se a última migration do repositório for mais nova que a do dump, mandando regenerar. | proposta | Mesmo padrão de `scripts/test-api-key.sh restore`. A checagem de versão evita um dump antigo quebrando a subida do Flyway sem explicação. |
| Geração do dump | Script `gerar-massa` (bash): sobe com `scripts/blackbox-clock-offset.sh -30`, roda `python -m seed --profile standard --clock-offset-days -30`, derruba, e faz o `pg_dump`. Só quem mantém o caderno roda. | proposta | Junta os três passos manuais de hoje num comando, com o deslocamento fixo para os roteiros não mudarem de uma geração para outra. |
| Leitura do e-mail nos scripts | `GET http://localhost:4566/_aws/ses`, filtrando pelo destinatário, mensagem mais recente, token tirado do `href` com `login-links/`. Até 5 tentativas com 2 s entre elas. Não apaga a mensagem. | proposta | Mesmo mecanismo do `blackbox_fixtures.py`. Não apagar mantém o e-mail visível no `ses-viewer` para quem acompanha a demonstração. |
| Cabeçalhos de dispositivo | Um JSON por dispositivo em `dispositivos/<número>.json`, com os quatro cabeçalhos `Sec-CH-UA*` como chave/valor. Valores tirados de `blackbox-tests/common/device_profiles.py` e dos testes Java do `DeviceLabelResolver` (um rótulo diferente por dispositivo). | proposta | O roteiro 3 só fica legível se cada sessão listada tiver um rótulo diferente. Reaproveitar valores já calibrados evita rótulo inesperado. |
| Cookie dos scripts | Arquivo `cookie<número>` com a linha `SESSION=<valor>`, mesmo formato no bash e no PowerShell, enviado como cabeçalho `Cookie`. | proposta | O formato de cookie jar do `curl` não é lido pelo PowerShell; uma linha simples serve aos dois. |
| Mensagens dos scripts | Cada script imprime, antes de cada passo, o que vai fazer e a operação HTTP (método e caminho), e depois o resultado resumido (status, rótulo do dispositivo, número de sessões). Erros saem com a resposta da API. | proposta | Pedido da Leila: o script tem que dizer o que está fazendo, para servir de explicação durante a demonstração. |
| "Clicar no link" no Swagger UI | Abrir o e-mail no `ses-viewer`, copiar o token do link e chamar `GET /login-links/{token}` pelo Swagger UI no mesmo navegador. | proposta | O link é relativo (Issue #92) e não abre direto. Pelo Swagger UI no mesmo navegador o cookie de sessão fica nesse navegador, e as chamadas seguintes já saem autenticadas. |

## JSON de referência dos roteiros

Formato que o caderno usa: um bloco por corpo, completo, com marcadores `<...>` explicados antes
do bloco. Datas e nomes finais são escolhidos na implementação.

Listar todos os jogadores (roteiro 1, `POST /players/search`) — sem filtro, primeira página
com o tamanho máximo:

```json
{
  "page": 0,
  "size": 100
}
```

Pedido de link de login (roteiros 1, 2, 3, 7):

```json
{
  "email": "<email do jogador>"
}
```

Competição pública (roteiro 4):

```json
{
  "name": "Copa do caderno de testes",
  "type": "PUBLIC",
  "startDate": "<amanhã, AAAA-MM-DD>",
  "durationDays": 30,
  "recurring": false,
  "buyFee": 0.5,
  "sellFee": 0.5
}
```

Pedido de entrada sem sessão (roteiro 4) — o captcha é aceito vazio no ambiente `blackbox`:

```json
{
  "email": "jogador.caderno@example.com",
  "captchaToken": ""
}
```

Cadastro (roteiros 4 e 5):

```json
{
  "name": "Jogador do Caderno"
}
```

Competição privada (roteiro 5):

```json
{
  "name": "Copa privada do caderno de testes",
  "type": "PRIVATE",
  "startDate": "<amanhã, AAAA-MM-DD>",
  "durationDays": 30,
  "recurring": false,
  "buyFee": 0.5,
  "sellFee": 0.5,
  "emails": [
    "<email de um jogador já registrado da massa>",
    "convidado1.caderno@example.com",
    "convidado2.caderno@example.com"
  ]
}
```

Decisão de envio dos convites (roteiro 5):

```json
{
  "timing": "later"
}
```

Envio dos convites para um grupo (roteiro 5):

```json
{
  "participationIds": [<id1>, <id2>, <id3>]
}
```

Correção de e-mail (roteiro 6):

```json
{
  "email": "convidado2.corrigido@example.com"
}
```

## Estrutura de arquivos

```
docs/disciplina/
  caderno-de-testes.md                 # reescrito
  caderno-de-testes/
    dados/jogo-acoes-massa.sql         # dump
    dispositivos/1.json 2.json 3.json
    scripts/
      login-jogador.sh        login-jogador.ps1
      dispositivos-jogador.sh dispositivos-jogador.ps1
      restaurar-massa.sh      restaurar-massa.ps1
      gerar-massa.sh
```

Possíveis mudanças fora de `docs/disciplina/`: só no catálogo do gerador
(`blackbox-tests/seed/profiles.py`), se a conferência da massa (item 1 do trabalho de apoio)
mostrar que falta algum caso para os roteiros.

## Riscos e trade-offs

- **Dump desatualizado após nova migration**: o script de restauração recusa e manda regenerar;
  regenerar exige subir com `faketime`, então o caderno tem que ser revisado junto.
- **Datas da massa envelhecem**: a massa foi gerada 30 dias no passado em relação ao dia da
  geração. Uma competição "em andamento" na geração pode aparecer "terminada" semanas depois. O
  caderno registra a data da geração e o roteiro 7 indica o que conferir se os grupos não
  baterem.
- **Variação entre WSL e PowerShell**: dois scripts por função duplicam lógica. Aceito porque
  são curtos e a Leila usa os dois ambientes; um script Python único exigiria instalar o
  ambiente do `blackbox-tests` só para executar o caderno.
