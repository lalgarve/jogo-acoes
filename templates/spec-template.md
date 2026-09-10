# Spec: <nome da feature>

**Status:** rascunho | em revisão | aprovada
**Issue:** #<número da Issue-épico, quando criada>
**Iteração:** <label `iteration-N` correspondente>

## Resumo

<1-2 frases: o que esta feature faz, do ponto de vista de quem usa>

## Motivação

<Por que esta feature é necessária — o problema que resolve>

## Cenários (comportamento esperado)

Este projeto já escreve os cenários de aceite como Gherkin executável em
`app/src/test/resources/features` (ver `memory/constitution.md`) — não duplicar aqui.
Referenciar o(s) arquivo(s) `.feature` correspondentes:

- `app/src/test/resources/features/<nome>.feature`

Se a feature ainda não tem `.feature` escrito, escrever primeiro (antes do código de
implementação) e só então linkar aqui — este `spec.md` não é o lugar para rascunhar cenário
novo em prosa.

## Requisitos funcionais

- <requisito 1>
- <requisito 2>

## Requisitos não-funcionais

<performance, segurança, limites — quando relevante>

## Fora de escopo

<o que esta feature explicitamente não cobre, para não crescer sem limite>

## Decisões em aberto

<perguntas de requisito ainda não respondidas — decisões técnicas vão em plan.md, não aqui>
