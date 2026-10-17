# Spec: Ambiente completo numa sessão de nuvem da Claude

**Status:** rascunho
**Issue:** a criar
**Iteração:** iteration-5

## Resumo

Um script `scripts/cloud-up.sh` sobe o ambiente `docker` inteiro (Postgres, DNS de teste,
LocalStack Pro, `config-server`, `email-service` e `app` com o perfil `blackbox`) dentro de uma
sessão do Claude Code na nuvem, onde o `docker compose up` e o `scripts/docker-up.sh` não
funcionam. Junto, `scripts/cloud-blackbox-tests.sh` roda a suíte de caixa-preta contra esse
ambiente e `scripts/cloud-down.sh` derruba tudo. Com isso, a Claude consegue testar a aplicação
como um todo, e não só os módulos isolados.

## Motivação

- **A Leila vai trabalhar sempre com sessões de nuvem** (decisão de 2026-10-17). Sem um jeito de
  subir o ambiente lá, a Claude não roda a aplicação inteira nem a suíte `blackbox-tests/`, e a
  verificação manual de toda spec fica para depois, no WSL.
- **O caminho normal não funciona na nuvem.** Conferido em 2026-10-17 (spec 05-050, `tasks.md`):
  - a sessão só sai para a internet por um proxy em `127.0.0.1`, com uma CA própria;
  - dentro da rede do Compose os containers não alcançam esse proxy. Por isso o Maven dentro dos
    `Dockerfile`s não baixa dependências e o LocalStack Pro não ativa a licença (sai com 55);
  - expor o proxy aos containers com um relay foi bloqueado pela segurança do ambiente, com
    razão: abriria o proxy da sessão para qualquer container. Não é um caminho aceitável.
- **Um caminho alternativo já foi provado na mão, na mesma data:** build no host; Postgres e DNS
  pelo Compose (não precisam de internet); LocalStack Pro num container com rede `host`; os três
  serviços Java com `java -jar` no host. Resultado: licença ativada, e-mail ponta a ponta pelo
  Lambda, `behave` 2/2 e `pytest` 29/29. Feito à mão, levou várias tentativas, e alguns erros só
  apareceram no meio (ver "Armadilhas conhecidas" em `plan.md`). Um script guarda essa receita.
- **O container da sessão é reciclado** quando fica parado. O ambiente se perde (aconteceu na
  mesma sessão) e precisa ser recriado com um comando só.

## Cenários (comportamento esperado)

Não há `.feature`: é ferramenta de ambiente de desenvolvimento, não muda o comportamento de
nenhum serviço. Os cenários são verificados rodando os scripts numa sessão de nuvem (ver
`tasks.md`, "Verificação manual"):

1. **Subida do zero:** numa sessão nova, `./scripts/cloud-up.sh` inicia o Docker se preciso,
   compila, sobe tudo na ordem certa e termina com o `app` respondendo em
   `http://localhost:8080/api`.
2. **Subida repetida:** rodar `./scripts/cloud-up.sh` com o ambiente já de pé derruba o que
   existe e sobe de novo do zero, com bancos e LocalStack vazios.
3. **Suíte de caixa-preta:** `./scripts/cloud-blackbox-tests.sh` prepara o Python, gera o cliente,
   limpa as mensagens do SES e roda `behave` e `pytest`; sai com erro se algum teste falhar.
4. **Derrubar:** `./scripts/cloud-down.sh` encerra os processos Java, o LocalStack, os containers
   do Lambda e o Compose (com volumes). Rodar de novo, sem nada de pé, não dá erro.
5. **Licença inválida:** com um token inválido, o script para logo depois de subir o LocalStack,
   mostra as linhas do log com o motivo e sai com erro, sem imprimir o token.
6. **Fora de uma sessão de nuvem** (sem o proxy da sessão): o script para antes de fazer qualquer
   coisa e indica o `scripts/docker-up.sh`.
7. **Falha de um serviço:** se `config-server`, `email-service` ou `app` não sobe no tempo
   esperado, o script mostra as últimas linhas do log dele e sai com erro.

## Requisitos funcionais

- `scripts/cloud-up.sh [--no-build]`:
  - recusa rodar fora de uma sessão de nuvem;
  - sempre começa do zero (faz o mesmo que `cloud-down.sh` antes);
  - inicia o `dockerd` quando ele não está rodando;
  - compila no host (`scripts/install-api-key-lib.sh` e `mvn package -DskipTests`), a não ser
    com `--no-build`, que reaproveita os artefatos de `target/` e falha se algum faltar;
  - sobe `db`, `db-email-service` e `dns` pelo `docker-compose.yml`, sem overlay;
  - sobe o LocalStack Pro com os scripts de `docker/localstack/init/` e o `function.zip` da
    `email-lambda`, e espera os scripts de init terminarem com sucesso;
  - roda `test-api-key.sh restore`, `email-service`, `restore` de novo e `app`, nessa ordem,
    com a mesma configuração do `docker-compose.yml` e do overlay `blackbox`;
  - grava logs e PIDs numa pasta de estado fora do repositório e diz no fim onde ela está.
- `scripts/cloud-blackbox-tests.sh [argumentos do behave]`: venv na pasta de estado, cliente gerado
  com `blackbox-tests/openapi-python-client-config.yaml`, SES limpo antes de rodar.
- `scripts/cloud-down.sh`: idempotente; só encerra os processos que o `cloud-up.sh` registrou.
- O token do LocalStack vem de `LOCALSTACK_AUTH_TOKEN`, configurado no ambiente de nuvem, e nunca
  é impresso.
- `README.md` ganha uma seção curta sobre os scripts; `CLAUDE.md` ganha um aviso de que, numa
  sessão de nuvem, é assim que se sobe a aplicação.

## Requisitos não-funcionais

- Uma subida completa, com build, cabe em poucos minutos (referência de 2026-10-17: build
  ~2 min, cada serviço Java ~10-15 s).
- Nenhuma mudança em `app/`, `email-service/`, `email-lambda/`, `config-server/`, nos
  `docker-compose*.yml` além de comentário, no `scripts/docker-up.sh` ou no CI.

## Fora do escopo

- **WSL e CI:** continuam com `docker compose`/`scripts/docker-up.sh` (spec 05-050).
- **Ferramentas web** (`adminer`, `ses-viewer`, `log-viewer`): ninguém abre um navegador nas
  portas do container da nuvem.
- **Cobertura de caixa-preta** (agente JaCoCo, `scripts/blackbox-coverage.sh`): pode vir depois,
  se fizer falta.
- **Subir o ambiente automaticamente a cada sessão** (SessionStart hook): ver `plan.md`.
- **Liberar o proxy para os containers** (relay): bloqueado pela segurança do ambiente, ver
  "Motivação".

## Decisões em aberto

1. **Sem token**: usar o LocalStack Community (mesmo comando, outra imagem) com um aviso, ou
   parar com erro? Ver `plan.md`.
