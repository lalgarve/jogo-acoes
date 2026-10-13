-- Spec 05-040, T001: confere se a massa de teste tem o que os roteiros do caderno precisam.
-- Cada linha é uma verificação; a coluna "ok" tem que ser true em todas.
-- Rodar no Adminer (http://localhost:8081) ou com:
--   docker compose exec -T db psql -U jogo_acoes_admin -d jogo_acoes -f - < docs/disciplina/caderno-de-testes/dados/verificar-massa.sql

SET search_path TO jogo_acoes;

WITH jogadores AS (
    SELECT u.id, u.email,
           count(*) FILTER (WHERE p.status = 'IN_COMPETITION' AND c.type = 'PUBLIC')  AS publicas,
           count(*) FILTER (WHERE p.status = 'IN_COMPETITION' AND c.type = 'PRIVATE') AS privadas,
           count(*) FILTER (WHERE p.status = 'IN_COMPETITION'
                              AND c.start_date + c.duration_days <= current_date)     AS terminadas_por_data,
           count(*) FILTER (WHERE p.status = 'IN_COMPETITION'
                              AND c.start_date + c.duration_days > current_date)      AS em_andamento,
           count(*) FILTER (WHERE p.status <> 'IN_COMPETITION')                       AS pendentes
    FROM app_user u
    JOIN participation p ON p.user_id = u.id
    JOIN competition c ON c.id = p.competition_id
    GROUP BY u.id, u.email
)
SELECT 'Roteiro 1: ao menos 3 combinações diferentes de competições públicas/privadas' AS verificacao,
       (SELECT count(DISTINCT (publicas, privadas)) FROM jogadores WHERE publicas + privadas > 0) >= 3 AS ok
UNION ALL
SELECT 'Roteiro 7: jogador em competição terminada por data, em andamento e com convite pendente, sem competição criada',
       EXISTS (SELECT 1 FROM jogadores j
               WHERE j.terminadas_por_data > 0 AND j.em_andamento > 0 AND j.pendentes > 0
                 AND NOT EXISTS (SELECT 1 FROM competition c WHERE c.creator_id = j.id))
UNION ALL
SELECT 'Roteiro 6: competição privada com convite pendente',
       EXISTS (SELECT 1 FROM participation p JOIN competition c ON c.id = p.competition_id
               WHERE c.type = 'PRIVATE' AND p.status IN ('EMAIL_NOT_SENT', 'EMAIL_SENT'))
UNION ALL
SELECT 'Roteiros 2 e 3: nenhuma sessão de login aberta (o dump não leva sessões)',
       NOT EXISTS (SELECT 1 FROM login_session WHERE ended_at IS NULL)
UNION ALL
SELECT 'Administrador semeado existe',
       EXISTS (SELECT 1 FROM app_user u JOIN user_role ur ON ur.user_id = u.id JOIN role r ON r.id = ur.role_id
               WHERE r.name = 'ADMINISTRATOR' AND u.email = 'success+admin@simulator.amazonses.com');
