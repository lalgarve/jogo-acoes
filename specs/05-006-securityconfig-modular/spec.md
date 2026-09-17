# Spec: SecurityConfig modular por módulo

**Status:** rascunho
**Issue:** #<número da Issue-épico, quando criada>
**Iteração:** iteration-5

## Resumo

Cada módulo do sistema que expõe endpoint HTTP (`login`, `competition`, e futuramente `email`,
`log` etc.) passa a declarar suas próprias regras de autorização num bean
`SecurityConfigContributor` local, em vez de todas conviverem num único método
`SecurityConfig.securityFilterChain` central — o `SecurityFilterChain` principal (que continua
em `login/`) só agrega o que cada módulo contribuiu.

## Motivação

Hoje `SecurityConfig.securityFilterChain()` concentra os `requestMatchers` de todos os módulos
existentes (`login`, `competition`) num método só, misturando rotas e regras de módulos sem
relação de domínio entre si. Isso já faz esse arquivo crescer a cada feature nova, mesmo quando
a mudança é local a um módulo — as specs 05-004/05-005 tiveram que tocar esse arquivo central só
para adicionar `/competitions/public` ao `permitAll`. Conforme o sistema ganha módulos com
endpoints HTTP próprios (ex. e-mail, log/auditoria administrativa), cada um teria que editar o
mesmo arquivo central para registrar suas próprias rotas — aumentando o acoplamento entre
módulos sem relação de domínio nenhuma, e o risco de conflito de edição simultânea nesse
arquivo à medida que o time/o número de features cresce.

## Cenários (comportamento esperado)

Refactor estrutural puro — nenhuma regra de acesso observável muda (mesmos caminhos, mesmos
papéis exigidos, mesmas respostas 401/403). A suíte Cucumber existente já cobre toda rota
afetada (`login.feature`, `create_competition.feature`, `manage_competition_players.feature`,
`request_competition_entry.feature`, `browse_public_competitions.feature`,
`view_my_competitions.feature`, `redirect_after_login.feature`) — nenhum `.feature` novo nem
passo novo nesta spec; o critério de aceite é a suíte inteira continuar verde, com exatamente os
mesmos cenários, sem nenhuma alteração de texto Gherkin.

## Requisitos funcionais

- Interface `SecurityConfigContributor`, com um método que recebe o registry de autorização do
  Spring Security (`AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry`)
  e registra ali os `requestMatchers`/papéis daquele módulo.
- Cada módulo que expõe endpoint HTTP hoje (`login`, `competition`) ganha seu próprio bean
  `@Component` implementando essa interface, movendo para lá exatamente os matchers que já
  existem em `SecurityConfig` para suas rotas — sem mudar nenhuma regra.
- `SecurityConfig.securityFilterChain` deixa de listar `requestMatchers` de módulo diretamente:
  injeta `List<SecurityConfigContributor>`, aplica a contribuição de cada um, e só por último
  adiciona `.anyRequest().authenticated()` (o catch-all continua central, não é responsabilidade
  de nenhum módulo).
- Configuração transversal que não é de nenhum módulo específico (csrf, formLogin, httpBasic,
  `SecurityContextRepository`, tratamento de exceção 401/403) continua central em
  `SecurityConfig`, sem mudança.
- Teste ArchUnit garante, no build, que todo módulo com pelo menos um `@RestController` também
  tem uma classe implementando `SecurityConfigContributor` no mesmo pacote-base — se um módulo
  novo expuser rota HTTP e esquecer de registrar suas próprias regras, o build falha em vez de
  a rota cair silenciosamente (e sem aviso) no `anyRequest().authenticated()` central.
- Teste dedicado usando `RequestMappingHandlerMapping` (metadados reais de rota que o Spring MVC
  calcula a partir dos `@RequestMapping`/`@GetMapping`/etc. dos controllers — não uma convenção
  lida de fora) garante duas invariantes sobre o mapeamento de rotas da aplicação inteira: (1)
  nenhum controller de nenhum módulo mapeia o path raiz (`/`); (2) cada primeiro segmento de path
  (ex. `competitions`, `login-requests`) é mapeado por controllers de um único módulo — se dois
  módulos mapearem sob o mesmo prefixo, o teste falha listando o prefixo e os módulos em
  conflito.

## Requisitos não-funcionais

- **Nenhuma mudança de comportamento observável**: mesmas rotas públicas, mesmas rotas exigindo
  `ROLE_ADMINISTRATOR`, mesmas mensagens de erro 401/403.
- **Invariante de não-sobreposição entre módulos, verificada automaticamente**: cada contributor
  só registra matchers dentro do prefixo de recurso do próprio módulo (`/login-*` para o módulo
  `login`, `/competitions*` para o módulo `competition`, e assim por diante para módulos
  futuros) — e o teste de `RequestMappingHandlerMapping` acima garante que os módulos, de fato,
  nunca mapeiam rota nenhuma sob o mesmo prefixo em primeiro lugar (deixa de ser só uma
  convenção não verificada). Como módulos diferentes nunca competem pelo mesmo caminho, a ordem
  em que os contributors são aplicados no `SecurityFilterChain` não afeta o resultado (só dentro
  de um mesmo contributor a ordem dos seus próprios matchers importa, exatamente como já é hoje
  dentro do método único).

## Fora de escopo

- Módulos que hoje não expõem endpoint HTTP nenhum (`email`, `log`, `captcha`, `common`) não
  ganham contributor nesta spec — só quando algum deles passar a expor uma rota própria.
- O teste ArchUnit desta spec verifica só "o módulo tem um contributor" (existência), não "o
  contributor cobre exatamente as mesmas rotas que os controllers do módulo expõem" — essa
  checagem fina (contributor ↔ rotas reais do próprio módulo) continua sem cobertura automática,
  ver `plan.md`. A sobreposição de rota *entre módulos diferentes* é coberta pelo teste de
  `RequestMappingHandlerMapping` acima, não pelo ArchUnit.
- Não resolve a divergência entre `x-roles` (anotação OpenAPI, documentação) e a autorização de
  fato em `SecurityConfig`, já sinalizada como risco em aberto no `plan.md` da spec 05-004 — fica
  como possível trabalho futuro habilitado por esta reorganização (cada contributor vira uma
  fonte única e consultável por módulo), não implementado aqui.
- Não muda o mecanismo de autenticação (`LinkService`/`LoginLinkSessionService`) nem o
  `SecurityContextRepository`.

## Decisões em aberto

Nenhuma — decisões técnicas (assinatura da interface, onde vive, mecanismo de agregação)
resolvidas em conversa antes de escrever este documento, ver `plan.md`.
