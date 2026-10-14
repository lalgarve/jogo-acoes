# Spec: Verificação do domínio do destinatário por DNS (A e MX) com lista branca

**Status:** rascunho
**Issue:** a criar
**Iteração:** iteration-5

## Resumo

Antes de aceitar um `POST /emails`, o `email-service` confere se o domínio do destinatário recebe
e-mail, consultando os registros MX e A do DNS. Um domínio aprovado entra numa lista branca e não
é mais consultado no DNS. Um domínio que não recebe e-mail é recusado com `422`, sem gravar nem
publicar nada.

## Motivação

Hoje o `email-service` só confere o formato do endereço (`400`). Um endereço com domínio
inexistente ou digitado errado (`jogador@gmial.con`) é aceito, vai para a fila e vira um *bounce*
no SES. *Bounces* pesam na reputação da conta do SES e, acima de um limite, a AWS pausa o envio
da conta inteira. Essa checagem já estava planejada desde a antiga Iteração 5 do roadmap
("Redução de bounce no SES") e foi movida para o Serviço de E-mail
(`docs/context/iteracao-5.md`, seção 3: "renderiza → valida anti-bounce → enfileira"); a spec
05-031 deixou o anti-bounce para a Etapa 4.

A lista branca evita uma consulta ao DNS a cada envio: quase todos os e-mails vão para poucos
domínios (os grandes provedores), e o que já foi aprovado não precisa ser consultado de novo.

A segunda parte do anti-bounce (domínios de e-mail temporário) fica na spec
[05-048](../05-048-importacao-listas-email-temporario/spec.md), que usa esta verificação.

## Cenários (comportamento esperado)

- Contrato HTTP: [`docs/openapi-email-service.yaml`](../../docs/openapi-email-service.yaml), rota
  `POST /emails`, resposta `422` nova.
- `.feature`: `email-service/src/test/resources/features/send_email.feature`, `Rule` nova
  "The recipient's domain must accept e-mail", escrita antes do código (ver `tasks.md`). Casos:
  - domínio com MX → aceito e incluído na lista branca;
  - domínio sem MX e com A → aceito e incluído na lista branca;
  - domínio já na lista branca → aceito sem consultar o DNS;
  - domínio inexistente → `422`, nada gravado, nada na fila, fora da lista branca;
  - domínio que existe sem MX nem A → `422`;
  - domínio com "MX nulo" (RFC 7505, declara que não recebe e-mail) → `422`;
  - DNS sem resposta → ver "Decisões em aberto", item 1.

## Requisitos funcionais

### Verificação

- O domínio verificado é a parte depois do `@` de `recipientEmail`, em minúsculas. Um domínio
  internacionalizado (`exemplo.ção.br`) é convertido para a forma ASCII (`xn--...`) antes de
  consultar e de gravar.
- A verificação roda no `POST /emails` depois das verificações que já existem (`401`, `400`,
  `404`, `409`) e antes de gravar o envio. Um pedido recusado por elas não consulta o DNS.
- **Na lista branca:** aceito, sem consulta ao DNS.
- **Fora da lista branca:** o serviço consulta o DNS:
  1. registros MX. Se existir pelo menos um MX que não seja nulo, o domínio é aprovado;
  2. se o domínio existe e não tem MX, registros A (o "MX implícito" da RFC 5321: sem MX, o
     e-mail vai para o endereço do próprio domínio). Se existir, o domínio é aprovado;
  3. senão, o domínio é recusado.
- Um MX nulo (um único MX com destino `.`) recusa o domínio, mesmo que ele tenha A.
- **Aprovado:** o domínio entra na lista branca com a hora e o registro que o aprovou (`MX` ou
  `A`), e o envio segue como hoje.
- **Recusado:** `422` com uma mensagem que diz que o domínio do destinatário não recebe e-mail.
  Nada é gravado em `email_send`, nada vai para a fila e o domínio não entra na lista branca.
  O serviço registra um `INFO` no log com o domínio e o motivo (sem o endereço completo).
- Uma recusa não fica guardada: o próximo pedido para o mesmo domínio consulta o DNS de novo
  (o domínio pode ter sido corrigido pelo dono, e o resolvedor de DNS já guarda respostas por
  algum tempo).

### Lista branca

- Uma lista só, do serviço inteiro (não por cliente): um domínio que recebe e-mail recebe para
  todos.
- Fica numa tabela do schema `email_service`, com o domínio, a hora da aprovação e o registro
  que aprovou.
- Dois pedidos ao mesmo tempo para o mesmo domínio novo aprovam o domínio uma vez só, sem erro
  para nenhum dos dois.
- Nesta spec, um domínio aprovado continua aprovado (ver "Decisões em aberto", item 3).

### `jogo-acoes`

- O `422` do `email-service` vira uma exceção própria no *gateway* do `app` (constitution,
  "Fronteira entre módulos e serviços") e uma resposta `422` do `app` para quem pediu o e-mail
  (ver "Decisões em aberto", item 4).

## Requisitos não-funcionais

- A consulta ao DNS tem tempo máximo configurável (padrão: 2 s por consulta). O `POST /emails`
  de um domínio já aprovado não fica mais lento.
- Testes contra um servidor de DNS de verdade, no Docker Compose, com uma zona de teste
  conhecida, sem mock do resolvedor (constitution, "Testes: preferir real a fake").
- Em `staging`/`production`, o serviço usa o resolvedor de DNS do sistema; no `docker`, o
  servidor de DNS de teste do Compose.

## Fora de escopo

- Domínios de e-mail temporário: spec 05-048.
- Conferir se a caixa postal existe (conversa SMTP com o servidor do domínio).
- Uma rota para o cliente consultar um domínio sem enviar e-mail.
- Rotas para operações listar ou editar a lista branca (ver "Decisões em aberto", item 2).
- Os eventos de *bounce* do SES (Iteração 6).

## Decisões resolvidas

- **Verificação por registros A e MX, com lista branca dos aprovados** (Leila, 2026-10-14).
- **No Serviço de E-mail, dentro do envio** (`docs/context/iteracao-5.md`, seção 3; spec 05-031
  deixou para a Etapa 4).

## Decisões em aberto

Cada uma tem uma recomendação, já usada nos requisitos e no `plan.md`; falta a confirmação da
Leila.

1. **DNS sem resposta (tempo esgotado, `SERVFAIL`).** Recomendação: aceitar o envio sem incluir o
   domínio na lista branca e registrar um `WARN`. Segue a regra da spec 05-046: o `POST /emails`
   não falha por causa de infraestrutura fora do ar, e o `jogo-acoes` não repete o pedido. O
   risco é deixar passar um *bounce* enquanto o DNS estiver fora. A alternativa é `503`, que
   perde o e-mail de login ou convite.
2. **Operações pode incluir ou remover domínios à mão?** Recomendação: não nesta spec. A lista
   branca só cresce pelo DNS; remover é um `DELETE` no banco, sem script. Se aparecer um caso
   (domínio interno sem MX público, por exemplo), um script no mesmo modelo do
   `scripts/set-email-sender.sh`.
3. **O domínio aprovado vence?** Recomendação: não nesta spec. Um domínio que deixou de receber
   e-mail continua na lista branca até alguém apagar; o *bounce* do SES (Iteração 6) é quem
   avisa. A alternativa é uma nova consulta depois de N dias, com uma coluna a mais.
4. **O que o `jogo-acoes` faz com o `422`.** Recomendação: `EmailServiceGateway` traduz o `422`
   em `RecipientDomainRejectedException` e o `ApiExceptionHandler` do `app` responde `422` com a
   mensagem "The e-mail address domain does not accept e-mail". Vale para o pedido de link de
   login, para o pedido de entrada numa competição e para o convite individual. No convite em
   grupo (`CompetitionService`, um envio por participante), recomendação: o endereço recusado
   não derruba os outros; fica registrado no log e o convite dele não é enviado. Esse ponto pode
   precisar de mudança na resposta do convite em grupo, e pode ir para uma spec do `app` se a
   Leila preferir.
5. **Registro AAAA (IPv6).** Recomendação: tratar AAAA como A no passo 2 (a RFC 5321 fala em
   "registro de endereço"), mesmo a Leila tendo citado só A. Um domínio só com AAAA é raro, mas
   recebe e-mail.
