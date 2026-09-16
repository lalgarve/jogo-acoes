# Diagramas de sequência — Jogo de Ações

Um diagrama por fluxo, cobrindo os pontos de entrada do sistema: login, criação de
competição e convite, verificação de e-mail, pedido de entrada em competição pública,
gerência de jogadores e o pipeline assíncrono de envio de e-mail.

## 1. Login (mecanismo genérico de link — `LinkService`/`LinkRouter`/`LinkHandler`)

Desde a spec 05-003, um único endpoint genérico (`GET /login-links/{token}`) consome **qualquer**
link — tanto o login avulso quanto um link de convite/pedido de entrada de competição — porque
`LinkRouter` despacha pela `service_key` gravada no `LinkRecord` para o `LinkHandler` correto
(`LoginLinkHandler` ou `CompetitionLinkHandler`, ver [`classes.md`](classes.md)). O diagrama 1a
mostra o pedido de um login avulso; o 1b mostra o consumo, genérico o bastante para cobrir os dois
handlers — o ramo específico de `CompetitionLinkHandler` (registro em duas fases) está detalhado
na seção 4b.

### 1a. Pedido de login avulso

```mermaid
sequenceDiagram
    actor J as Jogador
    participant LC as LoginController
    participant LiS as LinkService
    participant UR as UserRepository
    participant AL as AuditLogService
    participant ES as EmailSender

    J->>LC: POST /login-requests {email}
    LC->>UR: findByEmail(email)
    UR-->>LC: User (ou vazio)
    alt e-mail desconhecido
        Note over LC: não revela se o e-mail existe -- retorna igual
        LC-->>J: 202 Accepted
    else e-mail conhecido
        LC->>LiS: invalidateActiveLinksFor(user.id)
        Note over LiS: invalida qualquer LinkRecord ainda ativo<br/>(não usado/expirado) desse usuário -- só se<br/>aplica a login avulso, não a links de competição
        LC->>LiS: create("login", LinkPayload{userId, email, extra={}})
        LiS-->>LC: LinkCreationResult{id, token}
        LC->>AL: record(LOGIN_LINK_ISSUED)
        LC->>ES: send(EmailRequest{template=LOGIN_LINK, link=/login-links/{token}})
        LC-->>J: 202 Accepted
    end
```

### 1b. Consumo do link (genérico)

```mermaid
sequenceDiagram
    actor J as Jogador
    participant LC as LoginController
    participant LiS as LinkService
    participant LRR as LinkRecordRepository
    participant Router as LinkRouter
    participant H as LinkHandler<br/>(LoginLinkHandler ou CompetitionLinkHandler)
    participant SS as LinkSessionService<br/>(LoginLinkSessionService)

    J->>LC: GET /login-links/{token}
    LC->>LiS: consume(token)
    LiS->>LRR: findByToken(token)
    LRR-->>LiS: LinkRecord (ou vazio)
    alt não encontrado, invalidado ou expirado
        LiS--xLC: LoginLinkInvalidException
        LC-->>J: erro (link inválido/expirado)
    else link válido
        LiS->>Router: handlerFor(record.serviceKey)
        Router-->>LiS: LinkHandler concreto
        LiS->>SS: currentAuthenticatedUserId()
        SS-->>LiS: Optional~Long~
        alt já autenticado neste dispositivo
            LiS->>H: alreadyAuthenticated(currentUserId, payload)
            H-->>LiS: LinkOutcome
            Note over LiS: atalho -- não marca o link como usado nem<br/>mexe na sessão, mesmo que já tenha sido<br/>usado noutro dispositivo antes
            LiS-->>LC: LinkOutcome{redirectData}
            LC-->>J: 200 + redirecionamento
        else não autenticado neste dispositivo
            alt link.usedAt != null
                LiS--xLC: LoginLinkUsedOnAnotherDeviceException
                LC-->>J: erro (usado noutro dispositivo)
            else link ainda não usado
                LiS->>H: consume(payload)
                H-->>LiS: LinkOutcome (autenticado ou pending)
                alt outcome pending
                    Note over LiS,H: só acontece com CompetitionLinkHandler<br/>(payload sem userId) -- ver seção 4b
                    LiS-->>LC: LinkOutcome.pending()
                    LC-->>J: 202 Accepted
                else outcome autenticado
                    LiS->>LRR: save(record com usedAt = now)
                    LiS->>SS: establish(outcome.userId(), token)
                    SS->>SS: enforceDeviceLimit(user)<br/>encerra a sessão mais antiga se no limite
                    SS->>SS: monta Authentication (roles) + salva SecurityContext
                    SS->>SS: save(LoginSession{userId, linkRecord, deviceId, createdAt})
                    LiS-->>LC: LinkOutcome{redirectData}
                    LC-->>J: 200 + redirecionamento
                end
            end
        end
    end
```

`LoginLinkHandler.consume`/`alreadyAuthenticated` decidem o destino (`admin-page` ou
`competitions-list`) consultando o papel do usuário (`UserRoleRepository`) — omitido do diagrama
por brevidade, é uma chamada só.

## 2. Verificação de e-mail antes do cadastro

> ⚠️ **Não implementado.** `EmailValidationService`/`MxRecordResolver`/`DisposableDomainRepository`/
> `DisposableDomain`/`EmailRejectedException`/`DisposableDomainRefreshJob` não existem no código
> atual (`app/src/main/java/`) nem há tabela `disposable_domain` em nenhuma migração Flyway —
> conferido nesta sessão (2026-09-16) ao revisar o módulo `login` para a próxima spec. Esta seção
> documenta um mecanismo que só existe aqui e em [`der.md`](der.md#notas-de-modelagem)/
> [`classes.md`](classes.md#verificação-de-e-mail), nunca implementado. Mantido como está por ora
> (fora do escopo desta revisão) — decidir depois se vira uma spec própria ou se a documentação é
> que deve ser corrigida.

Checagem síncrona feita no momento em que qualquer e-mail é coletado (convite de
administrador, pedido de entrada, pedido de login) — mesmo ponto onde o captcha já é
validado, quando há um. Falha rejeita o cadastro imediatamente, sem criar `Participation`/
`LoginLink` para um endereço que não vai receber nada. Duas checagens, nessa ordem: registro
MX do domínio (pega domínio inexistente ou digitado errado) e domínio descartável/temporário
contra uma lista de bloqueio local. Não cobre a existência real da caixa postal — isso fica
fora de escopo (não confiável, mal-visto por provedores de e-mail).

```mermaid
sequenceDiagram
    actor U as Usuário (jogador ou administrador)
    participant Svc as Serviço de negócio<br/>(Login/Competition/EntryRequest)
    participant EV as EmailValidationService
    participant DNS as Resolvedor DNS
    participant DDB as Domínios descartáveis (Postgres)

    U->>Svc: informa e-mail
    Svc->>EV: validate(email)
    EV->>DNS: consulta registro MX do domínio
    alt sem registro MX (domínio inexistente ou mal digitado)
        DNS-->>EV: nenhum registro
        EV--xSvc: EmailRejectedException("domínio sem MX")
    else registro MX encontrado
        DNS-->>EV: registro(s) MX
        EV->>DDB: domínio está na lista de descartáveis?
        alt domínio descartável/temporário
            DDB-->>EV: sim
            EV--xSvc: EmailRejectedException("domínio descartável")
        else domínio aceito
            DDB-->>EV: não
            EV-->>Svc: ok
            Note over Svc: segue o fluxo normal de cadastro/convite
        end
    end
```

A lista de domínios descartáveis é mantida localmente (não é uma consulta externa a cada
e-mail) e atualizada uma vez por dia a partir de uma fonte pública mantida em ordem
alfabética — o que torna barato calcular só o que mudou desde a última atualização, em vez de
reprocessar a lista inteira.

```mermaid
sequenceDiagram
    participant Sch as Processo agendado<br/>(1x/dia)
    participant Fonte as Lista pública de domínios descartáveis
    participant DDB as Domínios descartáveis (Postgres)

    Sch->>Fonte: busca o arquivo atual
    Fonte-->>Sch: conteúdo (ordem alfabética)
    Sch->>DDB: lê a versão local anterior
    DDB-->>Sch: lista local
    Sch->>Sch: calcula diff (adições/remoções)
    Sch->>DDB: aplica só as entradas adicionadas/removidas
```

## 3. Criação de competição privada + convite

Duas chamadas: criar a competição (gera `Participation` por e-mail convidado, sem enviar
nada ainda) e decidir o momento do envio.

```mermaid
sequenceDiagram
    actor A as Administrador
    participant CC as CompetitionsController
    participant CS as CompetitionService
    participant UR as UserRepository
    participant PR as ParticipationRepository
    participant AL as AuditLogService
    participant LiS as LinkService
    participant ES as EmailSender

    A->>CC: POST /competitions {type: PRIVATE, emails[]}
    CC->>CS: create(request)
    CS->>CS: validate(request)
    CS->>CS: save(Competition, status=AWAITING_INVITES)
    CS->>AL: record(COMPETITION_CREATED)
    loop cada e-mail convidado
        CS->>UR: findByEmail(email).filter(isRegistered)
        UR-->>CS: User (ou vazio) -- já tem conta?
        CS->>PR: save(Participation, status=EMAIL_NOT_SENT, requestType=INVITE)
        CS->>AL: record(PARTICIPATION_STATUS_CHANGED)
    end
    CS-->>CC: Competition
    CC-->>A: 201 Created

    A->>CC: POST /competitions/{id}/decide-invite-timing {timing: NOW}
    CC->>CS: decideInviteEmailTiming(id, NOW)
    loop cada Participation com status=EMAIL_NOT_SENT
        CS->>LiS: create("competition-entry", LinkPayload{userId, email, extra={participationId}})
        Note over CS,LiS: userId vem de participation.getUser(), lido<br/>agora -- LinkRecord não tem mais Participation<br/>pra buscar isso de volta no consumo (spec 05-003)
        LiS-->>CS: LinkCreationResult{id, token}
        CS->>AL: record(LOGIN_LINK_ISSUED)
        CS->>ES: send(EmailRequest)
        Note over ES: template = INVITE (sem conta) ou<br/>LOGIN_LINK (já tem conta) --<br/>EmailContentRenderer escolhe o .html<br/>físico por origin=INVITE + competitionName
        CS->>PR: save(status=EMAIL_SENT)
        CS->>AL: record(PARTICIPATION_STATUS_CHANGED)
    end
    CS->>CS: save(Competition, status=OPEN)
    CS-->>CC: void
    CC-->>A: 200 OK
```

## 4. Pedido de entrada em competição pública

Jogador sem sessão, com captcha — cobre tanto quem nunca teve conta quanto quem já tem conta
de outra competição (o `EmailTemplate` muda, mas o fluxo é o mesmo).

```mermaid
sequenceDiagram
    actor J as Jogador (sem login)
    participant ERC as EntryRequestsController
    participant ERS as EntryRequestService
    participant Cap as CaptchaService
    participant UR as UserRepository
    participant PR as ParticipationRepository
    participant LiS as LinkService
    participant AL as AuditLogService
    participant ES as EmailSender

    J->>ERC: POST /competitions/{id}/entry-requests {email, captchaToken}
    ERC->>ERS: requestEntry(id, request)
    ERS->>Cap: verify(captchaToken)
    Cap-->>ERS: ok (ou CaptchaInvalidException)
    ERS->>UR: findByEmail(email)
    UR-->>ERS: User (ou vazio)
    Note over ERS: template = REGISTRATION_LINK (sem conta)<br/>ou LOGIN_LINK (já registrado)
    ERS->>PR: find ou cria Participation (requestType=REQUEST)
    ERS->>PR: save(participation, status=EMAIL_NOT_SENT)
    ERS->>LiS: create("competition-entry", LinkPayload{userId, email, extra={participationId}})
    LiS-->>ERS: LinkCreationResult{id, token}
    ERS->>AL: record(LOGIN_LINK_ISSUED)
    ERS->>ES: send(EmailRequest)
    ERS->>PR: save(status=EMAIL_SENT)
    ERS->>AL: record(PARTICIPATION_STATUS_CHANGED)
    ERS-->>ERC: void
    ERC-->>J: 202 Accepted
```

### 4b. Consumo de um link de competição (dois casos)

Detalha o ramo `H.consume(payload)`/`H.complete(...)` da seção 1b quando `record.serviceKey ==
"competition-entry"` — os dois casos citados no cenário "New player logs in.../Registered player
confirms entry..." de `login.feature`.

```mermaid
sequenceDiagram
    actor J as Jogador
    participant LC as LoginController
    participant LiS as LinkService
    participant H as CompetitionLinkHandler
    participant PR as ParticipationRepository
    participant UR as UserRepository
    participant RR as RoleRepository /<br/>UserRoleRepository
    participant AL as AuditLogService
    participant SS as LinkSessionService

    Note over LC,H: continuação do consumo genérico (seção 1b),<br/>GET /login-links/{token}

    LC->>LiS: consume(token)
    LiS->>H: consume(payload)
    H->>PR: findById(extra["participationId"])
    PR-->>H: Participation
    alt competição fechada e registro não concluído
        H--xLiS: LoginLinkInvalidException
        LiS-->>LC: erro
        LC-->>J: erro
    else registro concluído, ou competição ainda aberta
        alt payload.userId() == null (nunca teve conta)
            H-->>LiS: LinkOutcome.pending()
            LiS-->>LC: pending
            LC-->>J: 202 Accepted

            J->>LC: POST /login-links/{token}/registration {name}
            LC->>LiS: complete(token, {name})
            LiS->>H: complete(payload, {name})
            H->>UR: save(new User{name, email, registered=true})
            H->>RR: assignRole(user, PLAYER)
            H->>PR: save(Participation{user, status=IN_COMPETITION, joinedAt})
            H->>AL: record(PARTICIPATION_STATUS_CHANGED)
            H-->>LiS: LinkOutcome.authenticated(user.id, redirectData)
            LiS->>SS: establish(user.id, token)
            Note over SS: mesmo mecanismo de sessão/limite<br/>de dispositivos da seção 1b
            LiS-->>LC: LinkOutcome{redirectTo=competition-page}
            LC-->>J: 200 + redireciona
        else payload.userId() != null (já tem conta)
            H-->>LiS: LinkOutcome.authenticated(userId, redirectData)
            Note over LiS: segue igual à seção 1b --<br/>marca o link usado + establish()
            LiS-->>LC: LinkOutcome{redirectTo=competition-page}
            LC-->>J: 200 + redireciona
        end
    end
```

## 5. Gerência de jogadores — reenvio e remoção

`resendInviteEmail(s)` reaproveita o mesmo `sendInviteEmail` privado usado na criação (fluxo
3). A remoção não precisa mais apagar nada em `link/` antes da `Participation` — desde a spec
05-003, `LinkRecord.extraJson` só carrega o `participationId` como dado opaco, sem FK real (ao
contrário da antiga `LoginLink.participation_id`), então um link clicado depois de o jogador
remover só falha ao resolver a participação, como qualquer outro link inválido.

```mermaid
sequenceDiagram
    actor A as Administrador
    participant PC as PlayersController
    participant PS as PlayerManagementService
    participant LiS as LinkService
    participant PR as ParticipationRepository
    participant AL as AuditLogService
    participant ES as EmailSender

    A->>PC: POST /competitions/{id}/players/{pid}/resend-invite
    PC->>PS: resendInviteEmail(id, pid)
    PS->>PS: sendInviteEmail(participation)
    PS->>LiS: create("competition-entry", LinkPayload{userId, email, extra={participationId}})
    LiS-->>PS: LinkCreationResult{id, token}
    PS->>AL: record(LOGIN_LINK_ISSUED)
    PS->>ES: send(EmailRequest)
    Note over ES: templateFor(participation):<br/>já tem conta → LOGIN_LINK,<br/>senão, INVITE ou REGISTRATION_LINK<br/>conforme requestType
    PS->>PR: save(status=EMAIL_SENT)
    PS->>AL: record(PARTICIPATION_STATUS_CHANGED)
    PS-->>PC: void
    PC-->>A: 200 OK

    A->>PC: DELETE /competitions/{id}/players/{pid}
    PC->>PS: removePlayer(id, pid)
    PS->>PR: delete(participation)
    PS->>AL: record(PARTICIPATION_STATUS_CHANGED, "removed")
    PS-->>PC: void
    PC-->>A: 204 No Content
```

## 6. Envio assíncrono de e-mail — produtor → SQS → Lambda → SES

O que todo `EmailSender.send(...)` acima dispara: o sistema principal publica numa fila
Amazon SQS, e uma AWS Lambda consome e envia via Amazon SES.

```mermaid
sequenceDiagram
    participant Svc as Serviço de negócio<br/>(Competition/EntryRequest/Login/PlayerManagement)
    participant Sender as SqsEmailSender
    participant Renderer as EmailContentRenderer
    participant Rec as SentEmailRecorder
    participant SQS as Fila SQS (comando)
    participant Lambda as EmailSendHandler
    participant SES as Amazon SES

    Svc->>Sender: send(EmailRequest)
    Sender->>Renderer: render(EmailRequest)
    Renderer->>Renderer: escolhe 1 dos 5 templates Thymeleaf<br/>(origem + já tem conta)
    Renderer-->>Sender: RenderedEmail{subject, body}
    Sender->>Rec: record(EmailRequest)
    Rec->>Rec: grava SentEmail (Postgres)
    Rec-->>Sender: SentEmail{id}
    Sender->>SQS: send(EmailMessage{correlationId=SentEmail.id, subject, body})

    SQS-->>Lambda: entrega a mensagem
    Lambda->>Lambda: parse EmailMessage (JSON)
    Lambda->>SES: sendEmail(source, destination, subject, body,<br/>tags=[correlationId])
    alt sucesso (aceito para entrega)
        SES-->>Lambda: 200
        Lambda-->>SQS: confirma (deleta a mensagem)
    else erro (rejeitado, throttling, rede)
        SES--xLambda: exceção
        Lambda-->>SQS: não confirma -- redrive (maxReceiveCount) ou DLQ
    end
```
