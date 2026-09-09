-- =====================================================================
--  ArenaLeague — schéma initial
--  Migration Flyway V1 · PostgreSQL 16
--  Tâche 1.5 · Phase 1 — cohérent avec le diagramme de classes (1.4)
--
--  Chaque contrainte porte en commentaire la règle de gestion (RG-xx)
--  qu'elle implémente. Voir « ArenaLeague — Règles de gestion » v1.0.
-- =====================================================================


-- ---------------------------------------------------------------------
--  utilisateur
--  Héritage aplati : Organisateur et Arbitre partagent une table,
--  discriminés par la colonne role. Aucun attribut propre à une
--  sous-classe ne justifierait une table par classe.
-- ---------------------------------------------------------------------
CREATE TABLE utilisateur (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    login             VARCHAR(40)  NOT NULL,
    mot_de_passe_hash VARCHAR(60)  NOT NULL,
    role              VARCHAR(16)  NOT NULL,

    CONSTRAINT uq_utilisateur_login UNIQUE (login),
    -- RG-01 / RG-02 : deux rôles, et seulement deux
    CONSTRAINT ck_utilisateur_role
        CHECK (role IN ('ORGANISATEUR', 'ARBITRE'))
);


-- ---------------------------------------------------------------------
--  equipe
-- ---------------------------------------------------------------------
CREATE TABLE equipe (
    id  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nom VARCHAR(60) NOT NULL,

    -- RG-14 : le nom d'équipe est unique dans toute l'application
    CONSTRAINT uq_equipe_nom UNIQUE (nom)
);


-- ---------------------------------------------------------------------
--  joueur
--  Composition Equipe ◆— Joueur : un joueur n'existe pas hors équipe,
--  d'où NOT NULL + ON DELETE CASCADE.
-- ---------------------------------------------------------------------
CREATE TABLE joueur (
    id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    pseudo    VARCHAR(40) NOT NULL,
    equipe_id BIGINT      NOT NULL,

    -- RG-13 : le pseudo est unique dans toute l'application
    CONSTRAINT uq_joueur_pseudo UNIQUE (pseudo),
    -- RG-12 : un joueur appartient à une seule équipe
    CONSTRAINT fk_joueur_equipe FOREIGN KEY (equipe_id)
        REFERENCES equipe (id) ON DELETE CASCADE
);

CREATE INDEX ix_joueur_equipe ON joueur (equipe_id);

-- RG-10 (effectif entre 2 et 5) est une contrainte d'agrégat : elle ne
-- s'exprime pas en CHECK de colonne. Elle est portée par EquipeService
-- au moment de l'inscription (RG-11). Limitation assumée, à annoncer.


-- ---------------------------------------------------------------------
--  tournoi
-- ---------------------------------------------------------------------
CREATE TABLE tournoi (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nom        VARCHAR(80) NOT NULL,
    date_debut DATE        NOT NULL,
    format     VARCHAR(24) NOT NULL,
    demarre    BOOLEAN     NOT NULL DEFAULT FALSE,

    CONSTRAINT uq_tournoi_nom UNIQUE (nom),
    -- §5 : deux formats, pas un de plus
    CONSTRAINT ck_tournoi_format
        CHECK (format IN ('ELIMINATION_DIRECTE', 'POULE'))
);


-- ---------------------------------------------------------------------
--  inscription  (table de liaison tournoi ↔ equipe)
--  Agrégation Tournoi ◇— Equipe : l'équipe survit à la fin du tournoi,
--  d'où ON DELETE RESTRICT côté equipe.
-- ---------------------------------------------------------------------
CREATE TABLE inscription (
    tournoi_id BIGINT    NOT NULL,
    equipe_id  BIGINT    NOT NULL,
    inscrit_le TIMESTAMP NOT NULL DEFAULT now(),

    -- RG-21 : une équipe ne peut être inscrite deux fois au même tournoi
    CONSTRAINT pk_inscription PRIMARY KEY (tournoi_id, equipe_id),
    CONSTRAINT fk_inscription_tournoi FOREIGN KEY (tournoi_id)
        REFERENCES tournoi (id) ON DELETE CASCADE,
    CONSTRAINT fk_inscription_equipe FOREIGN KEY (equipe_id)
        REFERENCES equipe (id) ON DELETE RESTRICT
);

CREATE INDEX ix_inscription_equipe ON inscription (equipe_id);


-- ---------------------------------------------------------------------
--  rencontre  (un match)
--  « match » est un mot réservé SQL:2003 (MERGE ... WHEN MATCHED) et un
--  mot-clé dans plusieurs SGBD. On le renomme pour éviter d'avoir à
--  citer l'identifiant partout.
--
--  Composition Tournoi ◆— Match : ON DELETE CASCADE.
-- ---------------------------------------------------------------------
CREATE TABLE rencontre (
    id          BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tournoi_id  BIGINT      NOT NULL,
    tour        INT         NOT NULL,
    equipe_a_id BIGINT      NOT NULL,
    equipe_b_id BIGINT      NOT NULL,
    score_a     INT,
    score_b     INT,
    etat        VARCHAR(12) NOT NULL DEFAULT 'PLANIFIE',
    arbitre_id  BIGINT,

    CONSTRAINT fk_rencontre_tournoi FOREIGN KEY (tournoi_id)
        REFERENCES tournoi (id) ON DELETE CASCADE,
    CONSTRAINT fk_rencontre_equipe_a FOREIGN KEY (equipe_a_id)
        REFERENCES equipe (id) ON DELETE RESTRICT,
    CONSTRAINT fk_rencontre_equipe_b FOREIGN KEY (equipe_b_id)
        REFERENCES equipe (id) ON DELETE RESTRICT,
    CONSTRAINT fk_rencontre_arbitre FOREIGN KEY (arbitre_id)
        REFERENCES utilisateur (id) ON DELETE SET NULL,

    -- RG-80 : une équipe ne s'affronte pas elle-même
    CONSTRAINT ck_rencontre_equipes_distinctes
        CHECK (equipe_a_id <> equipe_b_id),

    -- RG-32 / RG-42 : le tour commence à 1
    CONSTRAINT ck_rencontre_tour CHECK (tour >= 1),

    -- RG-50 à RG-53 : les trois états du cycle de vie, et rien d'autre
    CONSTRAINT ck_rencontre_etat
        CHECK (etat IN ('PLANIFIE', 'EN_COURS', 'TERMINE')),

    -- RG-60 : les scores sont des entiers positifs
    CONSTRAINT ck_rencontre_scores_positifs
        CHECK ((score_a IS NULL OR score_a >= 0)
           AND (score_b IS NULL OR score_b >= 0)),

    -- RG-83 : un score existe si et seulement si le match est TERMINE.
    -- C'est la contrainte qui rend impossible, au niveau du SGBD, un
    -- match clôturé sans score ou un score sur un match non démarré.
    CONSTRAINT ck_rencontre_score_si_termine
        CHECK ((etat = 'TERMINE') = (score_a IS NOT NULL AND score_b IS NOT NULL))
);

CREATE INDEX ix_rencontre_tournoi     ON rencontre (tournoi_id, tour);
CREATE INDEX ix_rencontre_equipe_a    ON rencontre (equipe_a_id);
CREATE INDEX ix_rencontre_equipe_b    ON rencontre (equipe_b_id);

-- RG-82 : « les deux équipes d'un match sont inscrites au même tournoi »
-- exigerait une clé étrangère composite vers inscription, donc de
-- dupliquer tournoi_id dans chaque référence. Contrôle applicatif dans
-- MatchService, documenté comme tel.

-- RG-33 : « pas de match nul en élimination directe » dépend du format
-- porté par la table tournoi. Un CHECK ne peut pas lire une autre table ;
-- la règle est portée par EliminationDirecte.validerScore().


-- ---------------------------------------------------------------------
--  Commentaires de schéma — utiles quand le jury ouvre la base
-- ---------------------------------------------------------------------
COMMENT ON TABLE  rencontre        IS 'Un match entre deux équipes d''un tournoi';
COMMENT ON COLUMN rencontre.etat   IS 'PLANIFIE -> EN_COURS -> TERMINE (RG-50 à RG-54)';
COMMENT ON COLUMN rencontre.tour   IS 'Rang dans l''arbre d''élimination ; toujours 1 en poule';
COMMENT ON TABLE  inscription      IS 'Liaison tournoi-équipe (RG-20, RG-21)';
COMMENT ON COLUMN utilisateur.role IS 'ORGANISATEUR ou ARBITRE (RG-01, RG-02)';
