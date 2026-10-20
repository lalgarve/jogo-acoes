# Spec: Perfil LocalStack Pro e script de subida do ambiente

**Status:** implementada (ver `tasks.md`; falta a verificação manual com token, T006)
**Issue:** [#189](https://github.com/lalgarve/jogo-acoes/issues/189)
**Iteração:** iteration-5

## Resumo

Quem tem licença do LocalStack passa a poder subir o ambiente `docker` com o **LocalStack Pro**,
escolhendo isso num perfil opcional; quem não tem continua subindo tudo com o **LocalStack
Community**, sem token e sem passo a mais. Junto, um script `scripts/docker-up.sh` sobe o
ambiente inteiro na ordem certa, lendo as opções (Pro ou Community, overlay `blackbox`,
rebuild, ferramentas web) de um arquivo de configuração: um modelo versionado
(`docker-up.conf.example`) e uma cópia local, não versionada (`docker-up.conf`), onde fica o
token.

## Motivação

- **LocalStack Pro:** decisão da Leila (2026-10-14) de usar o Pro. Um rascunho anterior desta
  spec (branch `claude/project-thread-a6o77l`, nunca mesclado, criado sem pedido) trocava a
  imagem do `docker-compose.yml` para todo mundo, o que obrigaria qualquer pessoa, e o CI, a
  ter um token. Esta versão substitui aquele rascunho: o Pro é **opcional**.
- **Quem não tem licença precisa continuar subindo a aplicação.** Conferido nesta sessão:
  - `localstack/localstack:4` (Community, versão 4.14.0) sobe sem token. O próprio LocalStack
    avisa na subida que a imagem Community vai dar lugar a uma imagem unificada; a tag `:4`
    continua funcionando sem conta.
  - `localstack/localstack-pro:4` sem token **para na subida** com código de saída 55 e a
    mensagem `License activation failed! ... No credentials were found in the environment`.
- **Subir o ambiente hoje é uma sequência de seis comandos** que precisa ser copiada certinha
  (constitution, "Verificação manual: comandos prontos para copiar"): sem o
  `test-api-key.sh restore` entre as subidas, o `email-service` responde 401 e o `app` não sobe.
  Com uma terceira escolha (Pro ou não) a sequência fica ainda mais fácil de errar. Um script com
  arquivo de configuração guarda as escolhas de cada pessoa uma vez só.
- **O token é segredo.** Não pode ir para o repositório nem aparecer em comando de exemplo; o
  arquivo local fora do Git é o lugar dele.

## Cenários (comportamento esperado)

Não há `.feature`: a mudança é de ambiente de desenvolvimento, não muda o comportamento de
nenhum serviço. Os cenários são verificados rodando o script (ver `tasks.md`, "Verificação
manual"):

1. **Sem arquivo local, sem token:** `./scripts/docker-up.sh` usa o modelo
   `docker-up.conf.example`, avisa isso, e sobe tudo com o LocalStack Community e o overlay
   `blackbox`; o `app` termina de pé.
2. **Pro com token:** com `LOCALSTACK_PRO=true` e o token em `docker-up.conf` (ou exportado no
   shell), o script sobe o LocalStack Pro e o resto do ambiente igual ao cenário 1.
3. **Pro sem token:** com `LOCALSTACK_PRO=true` e nenhum token, o script para antes de chamar o
   Docker, dizendo onde pôr o token ou como voltar para o Community. O mesmo vale para quem usa
   o overlay direto no `docker compose`: o Compose recusa o comando com a mesma orientação.
4. **Pro com token inválido:** o LocalStack para na subida; o script mostra as últimas linhas do
   log do LocalStack (com o motivo dado pela licença) e sai com erro.
5. **Configuração inválida:** chave desconhecida, chave obrigatória ausente, valor que não é
   `true`/`false` ou arquivo inexistente: o script para com a linha e o motivo, sem chamar o
   Docker.
6. **`docker compose up` sem o script** continua igual a hoje: Community, sem token.

## Requisitos funcionais

- `docker-compose.yml` continua com `localstack/localstack:4`.
- Overlay novo `docker-compose.localstack-pro.yml`, no mesmo padrão do `docker-compose.blackbox.yml`
  (nunca usado sozinho): troca só a imagem do serviço `localstack` para
  `localstack/localstack-pro:4` e passa `LOCALSTACK_AUTH_TOKEN` do ambiente de quem roda.
- `scripts/docker-up.sh [--config ARQUIVO] [--reset]`:
  - lê `docker-up.conf` da raiz; sem ele, `docker-up.conf.example`; `--config` aponta outro;
  - chaves: `BLACKBOX`, `LOCALSTACK_PRO`, `LOCALSTACK_AUTH_TOKEN`, `BUILD`, `TOOLS`;
  - token do arquivo tem precedência; vazio, vale o `LOCALSTACK_AUTH_TOKEN` exportado;
  - o arquivo é lido como `CHAVE=valor`, nunca executado (`source`);
  - `--reset` faz `docker compose down -v` antes;
  - sobe na ordem da seção 3 do caderno de testes: bancos, LocalStack e DNS; `restore`;
    `email-service`; `restore`; `app`; e, com `TOOLS=true`, `adminer`, `ses-viewer` e
    `log-viewer`;
  - no fim, mostra o `export COMPOSE_FILE=...` para os outros comandos `docker compose` usarem
    os mesmos arquivos.
- `docker-up.conf` no `.gitignore`.
- O token nunca é impresso pelo script.

## Fora do escopo

- **CI:** continua com o Community, sem segredo novo.
- **Testes da `email-lambda`** (Quarkus Dev Services): continuam com a imagem padrão do Quarkus.
- **Caderno de testes, versão 2** do Serviço de E-mail: estava no rascunho anterior desta spec.
  Se for feito, vira spec própria; ele pode usar o `docker-up.sh` na preparação.
- Reescrever os blocos de subida já existentes (cadernos, `tasks.md` de outras specs,
  constitution): continuam valendo. O script é um atalho para a mesma sequência.

## Decisões em aberto

Nenhuma. As decisões técnicas estão em `plan.md`.
