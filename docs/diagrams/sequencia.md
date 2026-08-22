# Diagramas de sequência — Jogo de Ações

Um diagrama por fluxo, cobrindo os pontos de entrada do sistema: login, criação de
competição e convite, verificação de e-mail, pedido de entrada em competição pública,
gerência de jogadores e o pipeline assíncrono de envio de e-mail.

## 1. Login

Pedido de link e consumo em um dispositivo novo — as regras de dispositivo (reuso de link,
limite por usuário) ficam resumidas numa nota; `LoginService.consumeLoginLink` trata todas no
mesmo método.

```mermaid
sequenceDiagram
    actor J as Jogador
    participant LC as LoginController
    participant LS as LoginService
    participant EV as EmailValidationService
    participant UR as UserRepository
    participant LLR as LoginLinkRepository
    participant AL as AuditLogService
    participant ES as EmailSender

    J->>LC: POST /login-links {email}
    LC->>LS: requestLoginLink(email)
    LS->>EV: validate(email)
    alt domínio sem MX ou descartável
        EV--xLS: EmailRejectedException
        LS-->>LC: erro
        LC-->>J: 422 Unprocessable Entity
    else domínio ok
        EV-->>LS: ok
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
    end

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

## 2. Verificação de e-mail antes do cadastro

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
    participant EV as EmailValidationService
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
        CS->>EV: validate(email)
        alt domínio sem MX ou descartável
            EV--xCS: EmailRejectedException
            Note over CS: e-mail rejeitado não entra na<br/>competição -- não cria Participation
        else domínio ok
            EV-->>CS: ok
            CS->>UR: findByEmail(email).filter(isRegistered)
            UR-->>CS: User (ou vazio) -- já tem conta?
            CS->>PR: save(Participation, status=EMAIL_NOT_SENT, requestType=INVITE)
            CS->>AL: record(PARTICIPATION_STATUS_CHANGED)
        end
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

## 4. Pedido de entrada em competição pública

Jogador sem sessão, com captcha — cobre tanto quem nunca teve conta quanto quem já tem conta
de outra competição (o `EmailTemplate` muda, mas o fluxo é o mesmo).

```mermaid
sequenceDiagram
    actor J as Jogador (sem login)
    participant ERC as EntryRequestsController
    participant ERS as EntryRequestService
    participant Cap as CaptchaService
    participant EV as EmailValidationService
    participant UR as UserRepository
    participant PR as ParticipationRepository
    participant LLR as LoginLinkRepository
    participant AL as AuditLogService
    participant ES as EmailSender

    J->>ERC: POST /competitions/{id}/entry-requests {email, captchaToken}
    ERC->>ERS: requestEntry(id, request)
    ERS->>Cap: verify(captchaToken)
    Cap-->>ERS: ok (ou CaptchaInvalidException)
    ERS->>EV: validate(email)
    alt domínio sem MX ou descartável
        EV--xERS: EmailRejectedException
        ERS-->>ERC: erro
        ERC-->>J: 422 Unprocessable Entity
    else domínio ok
        EV-->>ERS: ok
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
    end
```

## 5. Gerência de jogadores — reenvio e remoção

`resendInviteEmail(s)` reaproveita o mesmo `sendInviteEmail` privado usado na criação (fluxo
3); a remoção precisa apagar o `LoginLink` antes da `Participation` por causa da FK real.

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
    Note over ES: templateFor(participation):<br/>já tem conta → LOGIN_LINK;<br/>senão, INVITE ou REGISTRATION_LINK<br/>conforme requestType
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
