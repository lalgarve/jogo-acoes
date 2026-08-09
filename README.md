# Jogo de Ações

Um jogo de simulação de investimentos em bolsa: administradores criam competições
(públicas ou privadas), jogadores entram via link de login enviado por e-mail e negociam
ações com dados de mercado reais, competindo pela melhor evolução de portfólio.

## Estado atual do projeto

O projeto está na fase de especificação/planejamento. Ainda não há código de aplicação —
o que existe hoje:

- **Especificação de domínio (BDD)** em `src/test/resources/features`: cenários Gherkin de
  login, criação de competição, gerência de jogadores e pedido de entrada em competição.
- **Modelo de dados** em [`docs/diagrams/der.md`](docs/diagrams/der.md): diagrama
  entidade-relacionamento do domínio.
- **Plano de iterações** em [`docs/roadmap.md`](docs/roadmap.md): o que será feito em cada
  iteração, da especificação atual até negociação de ações, gráficos e acessibilidade.

## Arquitetura planejada

- Backend em Spring Boot, persistência em PostgreSQL.
- Cotações de ações via [Brapi](https://brapi.dev).
- Envio assíncrono de e-mail: VPS (Spring Boot) publica mensagens numa fila Amazon SQS,
  consumida por uma AWS Lambda que envia via Amazon SES.
- Contabilidade da competição modelada como partida dobrada (lançamentos insert-only,
  débito/crédito em contas separadas), no estilo de sistemas financeiros reais.
- Acessibilidade de gráficos com dois modos: descrição textual por pontos relevantes da
  série e sonorização (grave/agudo por valor, estéreo por tempo), com a parte mais pesada
  implementada em Rust compilado para WebAssembly.

Detalhes de cada etapa estão em [`docs/roadmap.md`](docs/roadmap.md).

## Licença

Este projeto está licenciado sob a GNU General Public License v3.0 (ou, a seu critério,
qualquer versão posterior) — veja o arquivo [LICENSE](LICENSE) para o texto completo.

```
Jogo de Ações
Copyright (C) 2026 Leila Algarve

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <https://www.gnu.org/licenses/>.
```
