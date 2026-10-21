# Spec: Importação em lote das listas de domínios de e-mail temporário

**Status:** implementada (T001–T014 em `tasks.md`; falta a verificação manual T015)
**Issue:** [#187](https://github.com/lalgarve/jogo-acoes/issues/187)
**Iteração:** iteration-5 (Etapa 4 da disciplina: processamento em lote)

## Resumo

O `email-service` ganha um processo em lote (Spring Batch) que baixa listas públicas de domínios
de e-mail temporário e atualiza uma tabela de domínios no banco. As listas vêm de repositórios
cadastrados numa tabela, com endereço, descrição, hora do último download e hora da última
atualização no banco. Operações cadastra repositórios e pede a execução do lote pela API do
serviço. A verificação de destinatário da spec
[05-047](../05-047-verificacao-dominio-destinatario/spec.md) passa a recusar endereços desses
domínios.

## Motivação

- **Requisito da disciplina**, Etapa 4: processamento em lote com Spring Batch (`Job`, `Step`,
  `ItemReader`, `ItemProcessor`, `ItemWriter`). Está "Não atendido" em
  `docs/disciplina/alinhamento-projeto-disciplina.md`, planejado como a importação da lista de
  domínios temporários dentro do Serviço de E-mail (`docs/context/iteracao-5.md`, Etapa 4).
- **Anti-bounce e cadastro falso**: endereços de serviços de e-mail temporário (Mailinator,
  10minutemail, etc.) somem depois de minutos. Um jogador que entra com um deles não recebe o
  próximo link de login, e o e-mail para ele vira *bounce*. Esses domínios têm MX válido, então a
  verificação de DNS da 05-047 não os pega.
- As listas mudam com frequência (domínios novos aparecem toda semana) e são grandes (dezenas de
  milhares de linhas), por isso a atualização é um lote, não uma consulta a cada envio.

## Cenários (comportamento esperado)

- Contrato HTTP: [`docs/openapi-email-service.yaml`](../../docs/openapi-email-service.yaml), rotas
  novas de repositórios e de execução do lote (tag `disposable-domains`).
- `.feature` novo: `email-service/src/test/resources/features/disposable_domains.feature`, escrito
  antes do código (ver `tasks.md`). Casos:
  - **Acrescentar repositório:** operações cadastra um repositório com endereço e descrição → o
    repositório aparece na lista, sem download e sem atualização ainda;
  - endereço repetido → `409`; endereço que não é `http`/`https` ou sem descrição → `400`;
  - chave sem a permissão `disposable-email:admin` (a do `jogo-acoes`) → `403`; sem chave → `401`;
  - chave de operações (só `disposable-email:admin`) tentando enviar e-mail ou mudar template → `403`;
  - **Pedir a execução do lote:** com um repositório cadastrado → `202` com o identificador da
    execução; a execução termina `COMPLETED`; os domínios da lista estão na tabela; o repositório
    tem hora de último download e de última atualização;
  - execução de novo com a lista igual → último download muda, última atualização não, a tabela
    não muda;
  - execução com a lista mudada (um domínio a mais, um a menos) → a tabela ganha o novo e perde o
    removido, só daquele repositório;
  - o mesmo domínio em dois repositórios: removido de um, continua valendo pelo outro;
  - pedir a execução de um repositório só;
  - pedir a execução enquanto outra está rodando → `409`;
  - repositório fora do ar ou respondendo erro → execução `FAILED` com o motivo, tabela e horas
    sem mudança, os outros repositórios importados;
  - lista vazia → execução `FAILED`, nada removido;
  - linhas em branco, comentários (`#`), maiúsculas e espaços → ignorados ou normalizados;
    linha que não é um domínio → ignorada e contada;
  - **Uso no envio** (`send_email.feature`): destinatário com domínio da tabela, ou subdomínio
    dele → `422`, nada gravado, nada na fila.

## Requisitos funcionais

### Repositórios

- Tabela de repositórios com: endereço (URL do arquivo da lista), descrição, hora do último
  download e hora da última atualização no banco.
  - **Último download:** a última vez que o lote baixou a lista com sucesso, mesmo que ela não
    tenha mudado.
  - **Última atualização no banco:** a última vez que o lote mudou a tabela de domínios por
    causa desse repositório (incluiu ou removeu algum domínio).
  - As duas ficam vazias até a primeira execução.
- Operações acrescenta repositórios pela API (endereço e descrição) e lista os repositórios com
  as quatro informações e o resultado da última execução de cada um.
- O endereço é único e precisa ser `http` ou `https`.
- Formato da lista: texto, um domínio por linha (o formato de
  [`disposable-email-domains`](https://github.com/disposable-email-domains/disposable-email-domains),
  a fonte já escolhida no roadmap). Linhas em branco e começando com `#` são ignoradas.

### Execução do lote

- Operações pede a execução pela API, para todos os repositórios ou para um só. O pedido responde
  `202` na hora com o identificador de cada execução; o lote roda em segundo plano.
- Operações consulta uma execução pelo identificador: situação (`STARTED`, `COMPLETED`,
  `FAILED`), horas de início e fim, linhas lidas, domínios incluídos, removidos e linhas
  ignoradas, e o motivo da falha.
- Para cada repositório, o lote:
  1. baixa a lista e grava a hora do último download;
  2. se a lista é igual à do último download que atualizou o banco, termina sem mexer na tabela;
  3. senão, lê linha a linha, normaliza (sem espaços, minúsculas, forma ASCII do domínio
     internacionalizado), ignora o que não for domínio, e grava os domínios do repositório;
  4. remove os domínios desse repositório que não estão mais na lista e grava a hora da última
     atualização.
- Um domínio pode estar em mais de um repositório; ele vale enquanto pelo menos um repositório o
  tiver.
- Um repositório que falha (erro de rede, status diferente de `200`, arquivo maior que o limite,
  lista vazia) não muda a tabela nem as horas dele, e não impede os outros.
- Duas execuções do mesmo repositório não rodam ao mesmo tempo (`409`).
- Uma execução interrompida (o serviço caiu no meio) pode ser pedida de novo e termina o
  trabalho, sem duplicar domínios.

### Uso na verificação do destinatário (05-047)

- No `POST /emails`, antes da lista branca e do DNS: se o domínio do destinatário, ou um domínio
  acima dele (`x.mailinator.com` → `mailinator.com`), está na tabela de domínios temporários, o
  pedido é recusado com `422` e a mensagem diz que o domínio é de e-mail temporário. Nada é
  gravado, nada vai para a fila.
- Um domínio temporário nunca entra na lista branca. Se já estava nela, a tabela de temporários
  vence (a consulta a ela vem antes).

### Permissões

Pré-requisito: a spec `013-key-permissions` do repositório
[`lalgarve/api-key`](https://github.com/lalgarve/api-key) (branch
`claude/issue-14-cli-permissions-spec-v1lgmo`), implementada e publicada (versão 1.1.0). Ela
dá a cada chave de API uma lista de permissões, tiradas de um catálogo configurado por serviço,
e a biblioteca de validação devolve as permissões da chave válida.

- O `email-service` configura um catálogo com duas permissões:

  | Permissão | Padrão | O que libera |
  |---|---|---|
  | `email:send` | sim | Enviar e-mails e mudar os próprios templates (`/emails`, `/templates`, `/sender`) |
  | `disposable-email:admin` | não | Administrar as listas de e-mail temporário (repositórios e execução do lote) |

- Cada rota exige a sua permissão; uma chave válida sem ela recebe `403`.
- O `jogo-acoes` continua com a chave de hoje: uma chave emitida antes da 1.1.0 recebe as
  permissões padrão, ou seja, só `email:send`. Não administra as listas.
- Operações recebe uma chave só com `disposable-email:admin`
  (`generate --client operations --permissions disposable-email:admin`). Não envia e-mail nem
  muda template.

## Requisitos não-funcionais

- Spring Batch com `Job`, `Step`, `ItemReader`, `ItemProcessor` e `ItemWriter` (requisito da
  disciplina), e metadados das execuções no schema `email_service` (nunca no `public`).
- Uma lista de 100 mil linhas é importada em poucos segundos, gravando em blocos.
- A consulta ao baixar tem tempo máximo e tamanho máximo configuráveis.
- A consulta da tabela no `POST /emails` usa índice; não percorre a tabela.
- Testes contra PostgreSQL e LocalStack reais: as listas de teste são servidas por HTTP pelo S3
  do LocalStack, sem baixar nada da internet no CI.

## Fora de escopo

- Remover repositório, editar endereço ou descrição (só um `DELETE`/`UPDATE` no banco, por
  enquanto).
- Listas em outros formatos (JSON, CSV) e listas de permissão (*allowlist*) de terceiros.
- Bloquear domínios temporários no cadastro do `jogo-acoes` antes de qualquer envio (o `422`
  chega pelo envio, como na 05-047).

## Decisões resolvidas

- **Lote com Spring Batch dentro do Serviço de E-mail**, importando listas de domínios de e-mail
  temporário (`docs/context/iteracao-5.md`, Etapa 4; Leila, 2026-10-14).
- **Tabela de repositórios** com endereço, descrição, último download e última atualização no
  banco (Leila, 2026-10-14).
- **Cenários para acrescentar repositórios e pedir a execução do lote** (Leila, 2026-10-14).
- **Operações se identifica por chave de API com permissões**, da spec `013-key-permissions` do
  `api-key`, que é pré-requisito (Leila, 2026-10-14). Duas permissões: uma de administração das
  listas e outra de enviar e-mails e mudar templates. Operações não tem permissão de enviar
  e-mails.

- **Execução periódica**: agendamento diário configurável
  (`email.disposable-domains.import-cron`), ligado em `staging`/`production` e desligado no
  `docker`, além da execução sob pedido (Leila, 2026-10-14).
- **Repositório inicial**: a migration cadastra
  `https://raw.githubusercontent.com/disposable-email-domains/disposable-email-domains/main/disposable_email_blocklist.conf`
  ("disposable-email-domains, lista principal"). Nos testes, a suíte apaga os repositórios antes
  de cada cenário (Leila, 2026-10-14). **Nota (2026-10-20):** a spec 05-053 tirou o cadastro da
  V6; ele está em `db/seed-email-service/`, aplicado só em `staging`/`production`. No `docker` e
  nos testes o repositório nem é criado, e nenhum teste depende de URL fora do projeto.
- **Lista que encolhe**: só a lista vazia é recusada; sem regra de porcentagem por enquanto
  (Leila, 2026-10-14).
- **Subdomínios**: os subdomínios de um domínio da lista (`x.mailinator.com`) também são
  recusados (Leila, 2026-10-14).

## Decisões em aberto

Nenhuma.
