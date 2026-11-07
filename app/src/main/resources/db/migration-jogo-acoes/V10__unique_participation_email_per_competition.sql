-- Spec 05-057: at most one participation per e-mail in a competition, compared ignoring case
-- (the same rule PlayerManagementService.updateEmail already checked in code). An expression
-- index because the e-mail stays stored as it was typed. Existing duplicates are not handled:
-- the system is in pre-production (memory/constitution.md).
CREATE UNIQUE INDEX participation_competition_email_uidx ON participation (competition_id, lower(email));
