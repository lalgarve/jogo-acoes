# Spec: Caderno de testes — Etapa 1 (Swagger)

**Status:** rascunho
**Issue:** #<número da Issue-épico, quando criada>
**Iteração:** iteration-5

## Resumo

Documento (`docs/disciplina/`) com os casos de teste manuais da Etapa 1 da disciplina,
executáveis via Swagger UI (spec 05-009) — cada caso indica os passos, o que deve aparecer no
log (spec 05-011) confirmando que a operação rodou, e os `SELECT`s no PostgreSQL (via a UI web
da spec 05-013, ou `psql`) que confirmam o estado final no banco.

## Motivação

A Issue #43 já decidiu escrever o "caderno de testes do Swagger" em paralelo ao código, uma
Etapa por vez, em vez de deixar tudo pro fim da iteração — e a Etapa 1 (Organização
Arquitetural) é a única já totalmente atendida hoje
(`docs/disciplina/alinhamento-projeto-disciplina.md`), faltando só o Swagger UI rodando de
verdade (spec 05-009) pra esse caderno ser executável. As specs 05-009/05-010/05-011 desta mesma
leva dão exatamente os três ingredientes que faltavam: UI interativa, uma operação nova simples
de demonstrar (sessões ativas) e logs previsíveis pra confirmar execução.

## Cenários (comportamento esperado)

Não aplicável — é o próprio documento de teste manual, não código com `.feature`.

## Requisitos funcionais

- Um caso de teste por critério da Etapa 1 já listado em `alinhamento-projeto-disciplina.md`
  (fluxo Controller→Service→Repository, Bean Validation, tratamento de exceção centralizado,
  consultas Spring Data, OpenAPI/Swagger UI, organização por domínio) — usando a operação de
  sessões ativas (spec 05-010) como exemplo central por tocar todos esses pontos numa única
  funcionalidade pequena.
- Cada caso de teste tem: (1) passo a passo no Swagger UI (endpoint, headers/corpo a preencher);
  (2) trecho de log esperado (spec 05-011) que confirma que a chamada passou pelo
  controller/repositório certo; (3) `SELECT` no PostgreSQL que confirma o estado gravado.
- Arquivo novo em `docs/disciplina/` (nome exato definido em `plan.md`).

## Requisitos não-funcionais

Nenhum.

## Fora de escopo

- Etapas 2, 3 e 4 do caderno — ficam pra quando o trabalho de código de cada uma existir (mesmo
  princípio incremental da Issue #43).
- Geração do PDF/`.tex` final — este documento é só o rascunho Markdown, seguindo o fluxo já
  estabelecido em `docs/disciplina/CLAUDE.md` ("Markdown primeiro, `.tex` só no final").

## Decisões em aberto

Nenhuma — decisões (nome do arquivo, branch de execução) resolvidas em conversa antes de
escrever este documento, ver `plan.md`.
