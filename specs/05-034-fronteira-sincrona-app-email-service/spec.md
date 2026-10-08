# Spec: Fronteira síncrona entre `app` e `email-service` (Etapa 2)

**Status:** implementada
**Issue:** [#119](https://github.com/lalgarve/jogo-acoes/issues/119) (sub-issue do épico
[#117](https://github.com/lalgarve/jogo-acoes/issues/117))
**Iteração:** iteration-5

## Resumo

O `jogo-acoes` (`app`) passa a enviar e-mail chamando o Serviço de E-mail (`email-service`) por
REST, com um cliente OpenFeign e autenticação por `X-API-Key`. Junto com isso, a fronteira entre
os dois serviços vira regra escrita (`memory/constitution.md`) e verificada por testes: nenhum
dos dois depende do código Java do outro, cada um é dono dos próprios dados, e as chamadas que o
`app` pode repetir têm resultado previsível quando repetidas.

## Motivação

É o item "Do now — Discipline Stage 2" do épico #117 e o que falta da Etapa 2 da disciplina
(`docs/context/iteracao-5.md`, "Etapa 2 — Separação e Comunicação Síncrona", e seção 5,
"`jogo-acoes` como primeiro cliente").

Hoje os dois serviços estão no mesmo reator Maven, mas não conversam:

- O `app` ainda renderiza os próprios e-mails com Thymeleaf (`EmailContentRenderer`, 5 templates
  em `app/src/main/resources/templates/email/`) e publica na fila SQS (`SqsEmailSender`).
- O `email-service` já é dono do cadastro de templates (spec
  [05-025](../05-025-servico-email-templates/spec.md)), valida a API-KEY de verdade (spec
  [05-030](../05-030-validacao-api-key-servico-email/spec.md)), tem schema próprio (spec
  [05-032](../05-032-schema-proprio-por-servico/spec.md)) e, com a spec 05-031, ganha
  `POST /emails`, que publica na fila e passa a ser o único formato que a Lambda aceita.

Quando a 05-031 for mesclada, o `SqsEmailSender` do `app` deixa de funcionar (a Lambda só aceita
mensagem com template). A comunicação síncrona `app` → `email-service` é o que o substitui.

As regras de fronteira existem hoje só como intenção (roadmap, specs isoladas). Nada impede, por
exemplo, que o `app` passe a depender do jar do `email-service` para reaproveitar um DTO, ou que
um serviço leia a tabela do outro. Os dois compartilham até o pacote raiz
(`dev.leilaalgarve.jogoacoes` e `dev.leilaalgarve.jogoacoes.emailservice`), o que torna um
acoplamento acidental fácil de passar despercebido.

## Cenários (comportamento esperado)

Nenhum comportamento observável por quem usa a API do `app` muda: convite, link de cadastro e
link de login continuam gerando os mesmos e-mails, agora entregues pelo Serviço de E-mail. Os
cenários existentes continuam valendo:

- `app/src/test/resources/features/login.feature`
- `app/src/test/resources/features/manage_competition_players.feature`
- `app/src/test/resources/features/request_competition_entry.feature`
- `email-service/src/test/resources/features/register_templates.feature`
- `email-service/src/test/resources/features/send_email.feature` (spec 05-031)

O critério de aceite novo é estrutural e de integração, verificado pelos testes listados em
`tasks.md` (regras de arquitetura, proibição de dependência no Maven, cliente Feign contra o
`email-service` real e repetição das operações idempotentes).

## Requisitos funcionais

**Comunicação**

- O `app` chama o `email-service` só por HTTP, pelo contrato
  [`docs/openapi-email-service.yaml`](../../docs/openapi-email-service.yaml), com um cliente
  OpenFeign. O contrato continua sendo a única fonte da verdade: o cliente segue o contrato, não
  o código do `email-service`.
- Toda chamada leva `X-API-Key` com a chave emitida para o cliente `jogo-acoes`. A chave vem do
  ambiente (`EMAIL_SERVICE_API_KEY`, já presente no `docker-compose.yml`), nunca do código, e
  nunca aparece em log.
- A URL do `email-service` vem da configuração do `app` por perfil. Trocar onde o
  `email-service` roda é só trocar essa URL.
- O envio de e-mail do `app` passa a ser: pedir `POST /emails` ao `email-service` com o nome do
  template, o destinatário e as variáveis. O `app` não renderiza e-mail e não publica em fila.
- Os 5 e-mails que o `app` envia hoje (convite, link de cadastro e as três variações de link de
  login) viram templates do cliente `jogo-acoes` no `email-service`, com o mesmo conteúdo visível.
- O `app` cadastra/atualiza os próprios templates no `email-service` (ver "Decisões resolvidas").

**Fronteira entre os serviços**

- Nenhuma dependência Java direta entre `app` e `email-service`, em nenhum sentido: nem
  dependência Maven, nem classe de um usada pelo outro.
- Nada de persistência é compartilhado: entidades JPA, repositórios, migrations, schemas,
  classes de persistência. Cada serviço lê e escreve só no próprio schema (`jogo_acoes`,
  `email_service`).
- Exceções internas não cruzam a fronteira. O que o `app` vê de uma falha do `email-service` é o
  status HTTP e o corpo de erro do contrato; o `app` traduz isso para exceções próprias.
- Os DTOs que o `app` usa para chamar o `email-service` pertencem ao `app` (gerados a partir do
  contrato), não são classes importadas do `email-service`.

**Dono de cada dado e responsabilidade**

| Dado / responsabilidade | Dono |
|---|---|
| Quem recebeu qual link, para qual usuário, por qual fluxo (`sent_email`) | `app` |
| Quando e por que um e-mail é enviado (regra de negócio de convite, login, cadastro) | `app` |
| Conteúdo dos templates do cliente `jogo-acoes` (o que o e-mail diz) | `app` decide, `email-service` guarda e sincroniza com o SES |
| Cadastro de templates, nome no SES, validação pelo SES | `email-service` |
| Remetente de cada cliente | operações, gravado no `email-service` (spec 05-031) |
| Registro do pedido de envio, fila, entrega | `email-service` (e `email-lambda`) |
| API-KEYs válidas | `email-service` (CLI do `api-key` contra o banco dele) |

- O `sent_email` do `app` guarda o `id` devolvido pelo `email-service` no `202`, para que um
  envio possa ser seguido de um lado ao outro sem que um serviço leia o banco do outro.

**Repetição de chamadas síncronas (idempotência)**

- Cadastrar os templates do `app` duas vezes seguidas deixa o `email-service` no mesmo estado que
  cadastrar uma vez, sem erro: um template que já existe é atualizado, não duplicado nem
  rejeitado.
- `PUT /templates/{name}` com o mesmo conteúdo, repetido, devolve o mesmo resultado e não muda o
  estado.
- `POST /templates/{name}/preview` não muda estado e pode ser repetido livremente.
- O `app` só repete automaticamente (retry) chamadas idempotentes. `POST /emails` não é repetido
  automaticamente: a idempotência dele fica para a Etapa 4 (ver "Decisões resolvidas").

**Falhas**

- `email-service` fora do ar, lento ou respondendo `5xx`/`503` não deixa o `app` pendurado: as
  chamadas têm tempo limite e a falha vira uma exceção do `app`, tratada no mesmo lugar das
  outras.
- `401` do `email-service` (chave ausente ou inválida) é erro de configuração do `app`: aparece
  no log com o motivo (sem a chave) e não é repetido.

## Requisitos não-funcionais

- Testes contra infraestrutura real: PostgreSQL real e LocalStack, e o cliente Feign testado
  contra um `email-service` real rodando, não contra mock HTTP (`memory/constitution.md`,
  "Testes: preferir real a fake sempre que der").
- As regras de fronteira são verificadas automaticamente no build (ArchUnit e Maven), não só
  descritas.
- Nenhum código de teste entra em `app/` ou `email-service/` de produção.
- Java 21 continua sendo a baseline. Se a versão do Spring Cloud OpenFeign compatível com o
  Spring Boot do projeto exigir outra coisa, a incompatibilidade é reportada e decidida à parte
  (`memory/constitution.md`, "Baseline Java e upgrades de LTS").

## Fora de escopo

- Tudo o que é fila e processamento assíncrono: contrato da mensagem SQS, `schemaVersion`,
  `email-lambda`, eventos do SES, entrega repetida pela fila, DLQ. É a Etapa 4 da disciplina
  (épico #117, "Discipline Stage 4").
- O endpoint `POST /emails` em si e a mudança da Lambda para mensagem com template — spec 05-031.
- Juntar os dois PostgreSQL num só (possível desde a 05-032, decisão separada).
- Spring Cloud Config, service discovery, gateway de API — Etapa 3 ou depois.
- Circuit breaker e retry com backoff elaborado (Resilience4j). Nesta spec bastam tempo limite e
  retry simples nas chamadas idempotentes.
- Os itens da Iteração 6 do épico #117 (ciclos entre módulos, autorização por rota, logs
  sensíveis em geral, perfis).
- Tirar o `StubEmailSender` do `app` de produção (regra "Código de teste/dev nunca dentro da
  aplicação"). É uma pendência real, mas separada; esta spec não a agrava.

## Decisões resolvidas

- ~~Idempotência de `POST /emails` para repetição pelo cliente~~ — fica na Etapa 4, junto com a
  entrega repetida pela fila, mantendo a decisão da spec 05-031 (Leila, 2026-10-05). Até lá, o
  `app` não repete `POST /emails` automaticamente. A alternativa considerada, um header
  `Idempotency-Key` em `POST /emails` já nesta spec, foi descartada para não reabrir a 05-031.
- ~~Como os templates do `app` chegam ao `email-service`~~ — o `app` guarda o conteúdo dos
  templates (Handlebars do SES) nos próprios recursos e, ao subir, sincroniza com o
  `email-service`, criando o que falta e atualizando o que mudou (Leila, 2026-10-05). Alternativa
  descartada: um script de operações cadastrar os templates.
- ~~Como o `app` testa o envio~~ — as suítes Cucumber do `app` continuam com `email.sender=stub`
  (gravando em `sent_email`), e um teste de integração próprio exercita o cliente Feign contra o
  `email-service` real no Docker Compose (Leila, 2026-10-05). Alternativa descartada: subir a
  suíte inteira do `app` com o `email-service` real.

## Decisões em aberto

- Nenhuma de requisito.
