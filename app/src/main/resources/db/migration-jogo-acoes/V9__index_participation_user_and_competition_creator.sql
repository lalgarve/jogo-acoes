-- Spec 05-038: the player summary counts a user's participations and the competitions they
-- created with correlated subqueries (participation.user_id, competition.creator_id). PostgreSQL
-- doesn't index foreign keys on its own, and neither column had an index, so each subquery would
-- scan the whole table once per user returned.
CREATE INDEX participation_user_id_idx ON participation (user_id);
CREATE INDEX competition_creator_id_idx ON competition (creator_id);
