# Plan: Bootstrap do primeiro administrador e do remetente de e-mail via variável de ambiente

Traduz `spec.md` em mudanças arquivo por arquivo. Valida contra `memory/constitution.md`.

## Contexto — ordem em relação à spec 05-027

Esta spec é implementada **antes** da [05-027](../05-027-dividir-pacote-login/spec.md) (decisão
de sequenciamento, sessão 2026-10-01). Por isso o serviço de criação de usuário nasce em
`login/` (onde `User`/`Role`/`UserRole*` ainda moram hoje), não em `user/`. Quando a 05-027 for
implementada, ele migra junto com o resto do pacote, sem mudança de conteúdo.

A spec 05-023 (remove `BlackboxController`/`BlackboxSecurityConfigContributor`) **ainda não foi
implementada** (confirmado nesta sessão). Por isso este plano só mexe em `BlackboxDataSeeder` e
nos testes/comentários que a referenciam diretamente — `BlackboxController`,
`BlackboxSecurityConfigContributor` e os testes de `GET /blackbox/last-email` continuam
intocados, exatamente como estão hoje, até a 05-023 rodar.

## Decisões de arquitetura

| Pergunta (de `spec.md`) | Decisão | Raciocínio |
|---|---|---|
| Nome do serviço novo em `login/` | `UserProvisioningService` | Mais específico que `UserAccountService` sobre o que o serviço faz (provisiona/cria), evita ambiguidade com um futuro serviço de "conta" que lide com edição/exclusão. |
| `CompetitionLinkHandler.complete()` migra pra usar o serviço novo? | **Não nesta spec.** Fica como está, duplicação reconhecida (Issue #95), migração tratada em separado | Mantém esta spec focada em bootstrap (seu objetivo original) em vez de misturar com a limpeza de duplicação da Issue #95 — a migração de `CompetitionLinkHandler` muda um caminho de código já em produção (registro de jogador), merece sua própria revisão/teste, não uma alteração "de carona" aqui. |
| Nome do administrador | Env var opcional `ADMIN_NAME`, valor fixo `"Administrator"` quando ausente | Nome é só um rótulo de exibição, não identidade (o e-mail é a chave) — não justifica obrigar mais uma variável; continua configurável pra quem quiser personalizar. |
| `ADMIN_EMAIL` malformado | Valida com `jakarta.validation.constraints.Email` (já usado no resto do projeto via os modelos gerados do contrato); se inválido, loga `ERROR` com o valor recebido e **não** cria ninguém — não derruba a subida da aplicação | Travar o startup inteiro de produção por causa de um typo numa variável de bootstrap é desproporcional; mas falhar em silêncio deixaria alguém sem saber por que o admin nunca apareceu. |
| Condição de idempotência | "Existe algum `UserRole` com papel `ADMINISTRATOR`?" (método novo `UserRoleRepository.existsByRole_Name(String)`) | Idêntico ao raciocínio já em `spec.md`: bootstrap do *primeiro* administrador, não garantia de um e-mail fixo. |
| Nomes das env vars | `ADMIN_EMAIL`, `ADMIN_NAME`, `EMAIL_SENDER_ADDRESS` | Já é literalmente o nome das properties Spring/Quarkus em maiúsculas com underscore (`admin.email`/`admin.name`/`email.sender-address`) — convenção de *relaxed binding* já usada em `EMAIL_QUEUE_NAME`/`AWS_REGION` no projeto. |

## Mudanças arquivo por arquivo

### Apagar

- `app/src/main/java/dev/leilaalgarve/jogoacoes/blackbox/BlackboxDataSeeder.java`

`blackbox/` **não** fica vazio depois desta spec sozinha — `BlackboxController.java` e
`BlackboxSecurityConfigContributor.java` continuam lá até a spec 05-023 rodar.

### Criar: `app/src/main/java/dev/leilaalgarve/jogoacoes/login/UserProvisioningService.java`

```java
package dev.leilaalgarve.jogoacoes.login;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Creates a user and assigns it one or more roles in a single place -- consolidates what
 * BlackboxDataSeeder and CompetitionLinkHandler.complete() each implemented separately
 * (Issue #95). A user can hold more than one role at once (UserRole is its own association
 * table, not a single column on User), so callers always pass a list, even a single-role one.
 */
@Service
public class UserProvisioningService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;

    public UserProvisioningService(UserRepository userRepository, RoleRepository roleRepository,
                                    UserRoleRepository userRoleRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
    }

    /** @return {@code true} if at least one user already holds {@code roleName}. */
    public boolean existsAnyWithRole(String roleName) {
        return userRoleRepository.existsByRole_Name(roleName);
    }

    @Transactional
    public User createUser(String email, String name, List<String> roleNames) {
        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setRegistered(true);
        user = userRepository.save(user);

        for (String roleName : roleNames) {
            assignRole(user, roleName);
        }
        return user;
    }

    private void assignRole(User user, String roleName) {
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new IllegalStateException("Role not seeded: " + roleName));
        UserRole userRole = new UserRole();
        userRole.setId(new UserRoleId(user.getId(), role.getId()));
        userRole.setUser(user);
        userRole.setRole(role);
        userRole.setAssignedAt(LocalDateTime.now());
        userRoleRepository.save(userRole);
    }
}
```

### Editar: `app/src/main/java/dev/leilaalgarve/jogoacoes/login/UserRoleRepository.java`

```diff
 public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {

     List<UserRole> findByUser_Id(Long userId);
+
+    boolean existsByRole_Name(String roleName);
 }
```

### Criar: `app/src/main/java/dev/leilaalgarve/jogoacoes/bootstrap/AdministratorBootstrap.java`

```java
package dev.leilaalgarve.jogoacoes.bootstrap;

import dev.leilaalgarve.jogoacoes.login.RoleName;
import dev.leilaalgarve.jogoacoes.login.UserProvisioningService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Bootstraps the system's first administrator from the ADMIN_EMAIL environment variable, the
 * same pattern docker-compose.yml's own `db` service already uses (POSTGRES_USER/
 * POSTGRES_PASSWORD, effective only while the initial state -- an empty volume -- still holds).
 * Runs in every profile, not gated by any @Profile: before this, there was no way at all to
 * create an administrator in a staging/production deploy (only the now-removed, blackbox-only
 * BlackboxDataSeeder created one, and only for tests). There is still no API to create an
 * administrator (only an administrator can create competitions) -- this is the only way one
 * comes to exist, same reasoning as the class it replaces.
 *
 * Idempotent by "does any administrator already exist", not by e-mail: once any administrator
 * exists, this never acts again, even if ADMIN_EMAIL changes or is unset on a later restart.
 */
@Component
public class AdministratorBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdministratorBootstrap.class);
    private static final String DEFAULT_NAME = "Administrator";

    private final UserProvisioningService userProvisioningService;
    private final Validator validator;

    @Value("${ADMIN_EMAIL:}")
    private String adminEmail;

    @Value("${ADMIN_NAME:" + DEFAULT_NAME + "}")
    private String adminName;

    public AdministratorBootstrap(UserProvisioningService userProvisioningService, Validator validator) {
        this.userProvisioningService = userProvisioningService;
        this.validator = validator;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (adminEmail.isBlank()) {
            return; // Unset -- same as today's staging/production behavior.
        }
        if (userProvisioningService.existsAnyWithRole(RoleName.ADMINISTRATOR)) {
            return; // Already bootstrapped (or an administrator exists for any other reason).
        }
        if (!isValidEmail(adminEmail)) {
            log.error("ADMIN_EMAIL is set but not a valid e-mail address: '{}' -- skipping administrator bootstrap", adminEmail);
            return;
        }

        userProvisioningService.createUser(adminEmail, adminName, List.of(RoleName.ADMINISTRATOR));
        log.info("Bootstrapped the first administrator: {}", adminEmail);
    }

    private boolean isValidEmail(String value) {
        Set<ConstraintViolation<EmailHolder>> violations = validator.validate(new EmailHolder(value));
        return violations.isEmpty();
    }

    private record EmailHolder(@Email String email) {
    }
}
```

Ponto a confirmar na implementação (não bloqueia o `plan.md`): se `Validator` (Jakarta Bean
Validation) já é injetável como bean Spring sem configuração extra — deveria, via
`spring-boot-starter-validation`/Hibernate Validator, já que os modelos gerados usam `@Email` —
mas vale um teste rápido isolado antes de depender disso no bootstrap.

### `app/src/test/java/dev/leilaalgarve/jogoacoes/blackbox/BlackboxProfileIntegrationTest.java`

Remove **só** o teste `administratorIsSeededIdempotently()` e o que só ele usava (`@Autowired
BlackboxDataSeeder`, `@Autowired UserRepository`, `@Autowired UserRoleRepository`, import de
`RoleName` se não for usado em outro lugar do arquivo). **Mantém intocado**: `captchaIsAlwaysAccepted()`,
os dois testes de `lastEmail...` e tudo que eles usam (`@Value port`, `SentEmailRepository`,
`RestAssured`, `saveSentEmail`, imports de `EmailTemplate`/`SentEmail`) — isso é escopo da spec
05-023, não desta.

Javadoc da classe: ajustar "proves the three blackbox-only mechanisms" pra refletir que agora são
só dois testados aqui (captcha + last-email) — o bootstrap do admin ganhou seu próprio teste
(`AdministratorBootstrapTest`, abaixo).

### Criar: `app/src/test/java/dev/leilaalgarve/jogoacoes/login/UserProvisioningServiceTest.java`

Teste unitário (sem `@SpringBootTest`), casos:

- `createUser` com uma lista de um papel → salva `User`, salva um `UserRole`.
- `createUser` com uma lista de dois papéis → salva dois `UserRole`.
- `existsAnyWithRole` delega pra `userRoleRepository.existsByRole_Name`.
- `assignRole` com papel não semeado → lança `IllegalStateException`.

### Criar: `app/src/test/java/dev/leilaalgarve/jogoacoes/bootstrap/AdministratorBootstrapTest.java`

Teste unitário (mock de `UserProvisioningService`/`Validator`, mesmo padrão de testes unitários
já usados no projeto). Casos:

- `ADMIN_EMAIL` vazio/não setado → não chama `createUser`.
- Já existe um `ADMINISTRATOR` (`existsAnyWithRole` retorna `true`) → não chama `createUser`,
  mesmo com `ADMIN_EMAIL` setado.
- `ADMIN_EMAIL` setado, nenhum administrador existe → chama `createUser(email, "Administrator",
  List.of(RoleName.ADMINISTRATOR))` quando `ADMIN_NAME` não setado.
- `ADMIN_NAME` setado → usa esse valor em vez do default.
- `ADMIN_EMAIL` malformado (ex. `"não é um email"`) → não chama `createUser`.

### `docker-compose.blackbox.yml`

```diff
 services:
   app:
     environment:
       SPRING_PROFILES_ACTIVE: docker,blackbox
+      ADMIN_EMAIL: success+admin@simulator.amazonses.com
       JAVA_TOOL_OPTIONS: -javaagent:/app/jacocoagent.jar=output=tcpserver,address=*,port=6300,includes=dev.leilaalgarve.jogoacoes.*
     ports:
       - "6300:6300"
```

### `docker-compose.yml`

`localstack` ganha `EMAIL_SENDER_ADDRESS`:

```diff
   localstack:
     image: localstack/localstack:4
     depends_on:
       email-lambda-builder:
         condition: service_completed_successfully
     environment:
       SERVICES: sqs,ses,s3,lambda
+      EMAIL_SENDER_ADDRESS: no-reply@jogo-acoes.example
```

`app` continua sem `ADMIN_EMAIL` — comportamento inalterado pra `docker compose up` sem a
sobreposição `blackbox`.

### `docker/localstack/init/02-verify-ses-sender.sh`

```diff
 set -e

-awslocal ses verify-email-identity --email-address no-reply@jogo-acoes.example
+awslocal ses verify-email-identity --email-address "${EMAIL_SENDER_ADDRESS:?EMAIL_SENDER_ADDRESS not set}"
```

### `docker/localstack/init/03-deploy-email-lambda.sh`

Acrescenta `EMAIL_SENDER_ADDRESS` ao bloco `--environment Variables={...}` já existente:

```diff
   --environment 'Variables={
     QUARKUS_SES_ENDPOINT_OVERRIDE=http://localhost.localstack.cloud:4566,
     QUARKUS_SES_AWS_REGION=us-east-1,
     QUARKUS_SES_AWS_CREDENTIALS_TYPE=static,
     QUARKUS_SES_AWS_CREDENTIALS_STATIC_PROVIDER_ACCESS_KEY_ID=test,
-    QUARKUS_SES_AWS_CREDENTIALS_STATIC_PROVIDER_SECRET_ACCESS_KEY=test
+    QUARKUS_SES_AWS_CREDENTIALS_STATIC_PROVIDER_SECRET_ACCESS_KEY=test,
+    EMAIL_SENDER_ADDRESS='"${EMAIL_SENDER_ADDRESS:?EMAIL_SENDER_ADDRESS not set}"'
   }'
```

Sintaxe shell a conferir com cuidado na implementação (misturar aspas simples do bloco
`Variables={...}` com a expansão de variável exige escapar certo); `sh -n` confirma a sintaxe
antes de rodar de verdade.

### `email-lambda/src/main/resources/application.properties`

```diff
-email.sender-address=no-reply@jogo-acoes.example
+email.sender-address=${EMAIL_SENDER_ADDRESS:no-reply@jogo-acoes.example}
```

(`EmailSendHandlerTest` não precisa mudar — o valor padrão continua o mesmo pra quem roda sem
Docker.)

### Limpeza de documentação

- `app/src/main/java/dev/leilaalgarve/jogoacoes/blackbox/BlackboxController.java` — o Javadoc
  tem `{@link BlackboxDataSeeder}`, que fica pendurado depois que ela é apagada. Trocar por
  `{@code BlackboxDataSeeder}` (ou reescrever a frase) — não é código de produção afetado, só
  comentário; `BlackboxController` em si não muda de comportamento aqui (isso é escopo da
  05-023).
- `memory/constitution.md` (seção "Débito reconhecido, correção planejada", por volta da linha
  398): remove `BlackboxDataSeeder` da lista (resolvido por esta spec) e `EmailQueuePoller`
  (já removido pela Issue #84/#87, achado nesta sessão que a nota tinha ficado desatualizada) —
  fica só `BlackboxController`/`BlackboxSecurityConfigContributor` (pendente, 05-023). Commit
  `decision` separado do resto desta spec, mesma convenção já usada em `constitution.md` pra
  mudanças de decisão.

## Testes

- `UserProvisioningServiceTest` (novo).
- `AdministratorBootstrapTest` (novo).
- `BlackboxProfileIntegrationTest` — perde um teste, os outros três continuam exatamente como
  estão.
- `mvn -pl email-lambda -am test` — confirmar `EmailSendHandlerTest` continua verde com o
  placeholder novo.

## Validação manual (antes de fechar a parte do `email-lambda`/admin da Issue #84)

1. `docker compose up` (sem sobreposição), banco limpo — confirmar que o administrador é criado
   automaticamente com `success+admin@simulator.amazonses.com` (valor padrão do profile `docker`,
   decisão de sessão abaixo) mesmo sem `ADMIN_EMAIL` setada, e que o e-mail de teste continua
   saindo com o remetente de sempre. **Validado duas vezes nesta sessão (2026-10-02), banco limpo
   via `docker volume rm jogoacoes_db_data` nas duas**: antes da decisão abaixo, sem a variável e
   sem padrão de profile, `app_user` ficava em 0 linhas (comportamento antigo, intencional até
   ali); depois de aplicar o padrão de profile, o log confirma
   `Bootstrapped the first administrator: success+admin@simulator.amazonses.com` e o login via
   link mágico chega em `/admin`.
2. `docker compose -f docker-compose.yml -f docker-compose.blackbox.yml up` — confirmar que o
   administrador semeado aparece e consegue logar (mesmo fluxo já validado nas Issues
   #84/#87/#88 desta sessão, agora passando pelo `AdministratorBootstrap` em vez do
   `BlackboxDataSeeder`). **Validado nesta sessão.**
3. Reiniciar o `app` com `ADMIN_EMAIL` apontando pra outro endereço depois do primeiro bootstrap
   já ter rodado — confirmar que **nenhum segundo administrador** é criado. **Validado nesta
   sessão** (via `docker compose run --rm -e ADMIN_EMAIL=... app`).

## Decisão de sessão (2026-10-02): valor padrão de `ADMIN_EMAIL` em `docker`/`sandbox`

Depois da validação acima (item 1) mostrar que um `docker compose up` simples não cria
administrador nenhum, achado reavaliado: exigir configuração explícita faz sentido em
`staging`/`production` (onde um e-mail errado criaria um administrador inacessível e
permanente, dado que o bootstrap só cria o *primeiro*), mas não em ambiente de
desenvolvimento/teste, onde não ter um administrador de jeito nenhum sem passar uma variável a
mais é só fricção.

**Decisão**: `application-docker.yml` e `application-sandbox.yml` ganham

```yaml
ADMIN_EMAIL: success+admin@simulator.amazonses.com
```

Mesma chave que `AdministratorBootstrap` já lê (`@Value("${ADMIN_EMAIL:}")`) — precedência
padrão do Spring Boot garante que uma variável de ambiente `ADMIN_EMAIL` real (produção, ou o
`docker-compose.blackbox.yml` que já a define) continua vencendo esse padrão; ele só se aplica
quando nada mais a define. Nenhuma mudança em `AdministratorBootstrap.java` — a lógica já lê
"ambiente" de forma genérica, só o `application-{profile}.yml` ganha um valor a mais.
`staging`/`production` não ganham essa chave — comportamento inalterado lá.

**Validado nesta sessão**, depois do Docker ficar livre: banco limpo (`docker volume rm
jogoacoes_db_data`), `docker compose up` sem sobreposição, imagem reconstruída — log confirma
`Bootstrapped the first administrator: success+admin@simulator.amazonses.com`, e o login via link
mágico chega em `/admin` normalmente.
