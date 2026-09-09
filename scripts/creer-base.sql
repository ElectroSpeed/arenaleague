-- =====================================================================
--  ArenaLeague — amorçage PostgreSQL
--
--  À exécuter UNE SEULE FOIS, connecté en superutilisateur (postgres).
--
--    psql -U postgres -f scripts/creer-base.sql
--
--  Ce script ne crée que le rôle et la base vide. Le schéma et le jeu de
--  données sont posés par Flyway au premier démarrage de l'application
--  (V1__schema.sql puis V2__seed.sql). Ne pas les jouer à la main :
--  Flyway tient une table de suivi et refuserait ensuite de rejouer.
-- =====================================================================

CREATE ROLE arenaleague WITH LOGIN PASSWORD 'arenaleague';

CREATE DATABASE arenaleague
    OWNER    arenaleague
    ENCODING 'UTF8';

-- Droits sur le schéma public : depuis PostgreSQL 15, un rôle non
-- propriétaire ne peut plus y créer d'objet par défaut. Sans cette ligne,
-- Flyway échouerait à la création des tables.
\connect arenaleague
GRANT ALL ON SCHEMA public TO arenaleague;
