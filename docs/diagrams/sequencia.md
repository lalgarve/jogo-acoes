# Diagramas de sequência — Jogo de Ações

Um diagrama por fluxo, na mesma divisão dos arquivos `.feature` em
`app/src/test/resources/features` (Iterações 2–3) mais o pipeline assíncrono de e-mail
(Iteração 4). Os cinco primeiros descrevem código que existe e passa nos testes; o último é
desenho de decisão, não implementado — ver [`docs/iteracao-4.md`](../iteracao-4.md).

## 1. Login (`login.feature`)

Pedido de link e consumo em um dispositivo novo — as regras de dispositivo (reuso de link,
limite por usuário) ficam resumidas numa nota; `LoginService.consumeLoginLink` trata todas no
mesmo método.

```mermaid
sequenceDiagram
    actor J as Jogador
    participant LC as LoginController
    participant LS as LoginService
    participant UR as UserRepository
    participant LLR as LoginLinkRepository
    participant AL as AuditLogService
    participant ES as EmailSender

    J->>LC: POST /login-links {email}
    LC->>LS: requestLoginLink(email)
    LS->>UR: findByEmail(email)
    UR-->>LS: User (ou vazio)
    alt e-mail conhecido
        LS->>LLR: invalida links anteriores do usuário
        LS->>LLR: save(novo LoginLink)
        LS->>AL: record(LOGIN_LINK_ISSUED)
        LS->>ES: send(EmailRequest{template=LOGIN_LINK})
    else e-mail desconhecido
        Note over LS: não revela se o e-mail existe -- retorna igual
    end
    LS-->>LC: void
    LC-->>J: 202 Accepted

    J->>LC: GET /login-links/{token}
    LC->>LS: consumeLoginLink(token)
    LS->>LLR: findByToken(token)
    LLR-->>LS: LoginLink
    Note over LS: valida expiração, já usado,<br/>autenticado ou não, competição fechada
    LS->>LLR: save(link com usedAt)
    LS->>LS: enforceDeviceLimit(user)<br/>encerra sessão mais antiga se no limite
    LS->>LS: cria LoginSession + SecurityContext
    LS-->>LC: LoginResult{redirectTo}
    LC-->>J: 200 + redirecionamento
```

## 2. Criação de competição privada + convite (`create_competition.feature`)

Duas chamadas: criar a competição (gera `Participation` por e-mail convidado, sem enviar
nada ainda) e decidir o momento do envio.

```mermaid
sequenceDiagram
    actor A as Administrador
    participant CC as CompetitionsController
    participant CS as CompetitionService
    participant UR as UserRepository
    participant PR as ParticipationRepository
    participant LLR as LoginLinkRepository
    participant AL as AuditLogService
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
        CS->>LLR: save(LoginLink)
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

## 3. Pedido de entrada em competição pública (`request_competition_entry.feature`)

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
    participant LLR as LoginLinkRepository
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
    ERS->>LLR: save(LoginLink)
    ERS->>AL: record(LOGIN_LINK_ISSUED)
    ERS->>ES: send(EmailRequest)
    ERS->>PR: save(status=EMAIL_SENT)
    ERS->>AL: record(PARTICIPATION_STATUS_CHANGED)
    ERS-->>ERC: void
    ERC-->>J: 202 Accepted
```

## 4. Gerência de jogadores — reenvio e remoção (`manage_competition_players.feature`)

`resendInviteEmail(s)` reaproveita o mesmo `sendInviteEmail` privado usado na criação (fluxo
2); a remoção precisa apagar o `LoginLink` antes da `Participation` por causa da FK real.

```mermaid
sequenceDiagram
    actor A as Administrador
    participant PC as PlayersController
    participant PS as PlayerManagementService
    participant LLR as LoginLinkRepository
    participant PR as ParticipationRepository
    participant AL as AuditLogService
    participant ES as EmailSender

    A->>PC: POST /competitions/{id}/players/{pid}/resend-invite
    PC->>PS: resendInviteEmail(id, pid)
    PS->>PS: sendInviteEmail(participation)
    PS->>LLR: save(LoginLink)
    PS->>AL: record(LOGIN_LINK_ISSUED)
    PS->>ES: send(EmailRequest)
    Note over ES: templateFor(participation):<br/>já tem conta -> LOGIN_LINK;<br/>senão, INVITE ou REGISTRATION_LINK<br/>conforme requestType
    PS->>PR: save(status=EMAIL_SENT)
    PS->>AL: record(PARTICIPATION_STATUS_CHANGED)
    PS-->>PC: void
    PC-->>A: 200 OK

    A->>PC: DELETE /competitions/{id}/players/{pid}
    PC->>PS: removePlayer(id, pid)
    PS->>LLR: deleteByParticipation_Id(pid)
    Note over PS: precisa vir antes -- LOGIN_LINK.participation_id<br/>é FK real, diferente de LOG
    PS->>PR: delete(participation)
    PS->>AL: record(PARTICIPATION_STATUS_CHANGED, "removed")
    PS-->>PC: void
    PC-->>A: 204 No Content
```

## 5. Envio assíncrono de e-mail — produtor → SQS → Lambda → SES (Iteração 4)

O que todo `EmailSender.send(...)` acima dispara quando a implementação ativa é
`SqsEmailSender` (perfis `staging`/`production`; `docker`/CI publica de verdade contra
LocalStack desde a Iteração 4, mas não é o padrão ainda — `sandbox`/testes usam
`StubEmailSender`, que pula direto para "grava `SentEmail`").

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
    Note over SQS: LocalStack em docker/CI;<br/>fila real em staging/production (bloqueado por acesso AWS)

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

## 6. Retentativa com política por erro + painel (decidido, não implementado)

Desenho da decisão 4 (`docs/iteracao-4.md`) — nenhuma classe/endpoint deste diagrama existe
no código ainda. `app/` só publica em fila (comando + cancelamento) e só lê a fila de eventos
— nunca acessa DynamoDB/SES diretamente; isso continua exclusivo da Lambda.

```mermaid
sequenceDiagram
    participant SES as Amazon SES
    participant Lambda as EmailSendHandler
    participant DDB as DynamoDB (estado de retentativa)
    participant SQS as Fila SQS (comando)
    participant Cnl as Fila de cancelamento
    participant Evt as Fila de eventos
    participant App as app/ (@SqsListener)
    participant PG as Postgres (EMAIL_EVENT)
    participant Poller as Processo agendado<br/>(EventBridge, 15 min)
    actor Adm as Administrador (painel)

    Lambda->>SES: sendEmail(...)
    SES--xLambda: erro retentável (ex. throttling)
    Lambda->>DDB: PutItem {correlationId, attemptCount+1,<br/>nextRetryAt, lastError}
    Lambda->>Evt: publish RETRY_SCHEDULED
    Evt-->>App: consome evento
    App->>PG: insert EMAIL_EVENT{event_type=RETRY_SCHEDULED}

    Adm->>App: GET /admin/emails (painel)
    App->>PG: select EMAIL_EVENT
    PG-->>App: histórico de tentativas
    App-->>Adm: status, tentativas, próximo horário

    Adm->>App: cancelar retentativa pendente
    App->>Cnl: publish {correlationId}
    Cnl-->>Lambda: consome
    Lambda->>DDB: apaga/marca o item (correlationId)
    Lambda->>Evt: publish RETRY_CANCELLED
    Evt-->>App: consome evento
    App->>PG: insert EMAIL_EVENT{event_type=RETRY_CANCELLED}

    loop a cada 15 min
        Poller->>DDB: query nextRetryAt <= agora
        DDB-->>Poller: itens vencidos (já sem os cancelados)
        Poller->>SQS: republica na fila de comando
        Poller->>Evt: publish RETRY_ATTEMPTED
    end
```
