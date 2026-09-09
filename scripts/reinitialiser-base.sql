-- =====================================================================
--  ArenaLeague — remise à zéro
--
--    psql -U postgres -f scripts/reinitialiser-base.sql
--
--  Supprime tout et recrée une base vide. Au démarrage suivant, Flyway
--  rejoue V1 et V2 : on retrouve exactement le jeu de données de
--  démonstration. À faire avant chaque répétition de la soutenance.
-- =====================================================================

DROP DATABASE IF EXISTS arenaleague;

CREATE DATABASE arenaleague
    OWNER    arenaleague
    ENCODING 'UTF8';

\connect arenaleague
GRANT ALL ON SCHEMA public TO arenaleague;
