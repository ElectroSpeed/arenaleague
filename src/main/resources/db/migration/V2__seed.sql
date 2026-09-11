-- =====================================================================
--  ArenaLeague — jeu de données de démonstration
--  Migration Flyway V2
--
--  Objectif : l'application est utilisable dès le premier démarrage.
--  Aucune saisie préalable devant le jury — chaque minute passée à créer
--  des équipes est une minute volée à la démonstration.
--
--  Aucun identifiant n'est écrit en dur : les colonnes id sont en
--  GENERATED ALWAYS AS IDENTITY, les liaisons passent par des sous-requêtes
--  sur les noms. Le script reste rejouable sur une base vierge.
-- =====================================================================


-- ---------------------------------------------------------------------
--  Comptes
--  Empreintes BCrypt réelles (coût 10). Mots de passe : orga / arbitre.
--  À noter en soutenance : même sur un projet d'école, les mots de passe
--  ne sont jamais stockés en clair.
--
--  Révision « $2a$ » et non « $2b$ » : jbcrypt 0.4, la bibliothèque utilisée
--  par BCryptVerificateur, ne connaît pas la révision 2b et lève « Invalid
--  salt revision » à la vérification. Une empreinte 2b rend la connexion
--  impossible sans qu'aucun test ne le voie, puisque les tests de service
--  utilisent un vérificateur factice. Empreintes produites par la
--  bibliothèque elle-même, via BCrypt.gensalt(10).
-- ---------------------------------------------------------------------
INSERT INTO utilisateur (login, mot_de_passe_hash, role) VALUES
  ('orga',    '$2a$10$xSJy.MKrYTcaQMtZqINUMODI.ihWtjxgZAevR/HVCYJPMJKQanIEC', 'ORGANISATEUR'),
  ('arbitre', '$2a$10$bc2ysLYrjD3VWSGjnXDQReUxkz3LeaG6cMvnJLXIFUIvM1B4fWXAW', 'ARBITRE');


-- ---------------------------------------------------------------------
--  Équipes et joueurs
--  Quatre équipes de la LCK coréenne, trois joueurs chacune : conforme
--  à RG-10 (entre 2 et 5).
--
--  Les noms d'équipes ne sont pas interchangeables. Le tableau de
--  référence du §8.1 repose sur RG-75, le départage alphabétique :
--  Gen.G et Hanwha Life Esports y sont à égalité parfaite, et seul
--  l'ordre des noms les sépare. Renommer une équipe sans vérifier cet
--  ordre casse la démonstration du dernier critère de départage.
-- ---------------------------------------------------------------------
INSERT INTO equipe (nom) VALUES ('Gen.G'), ('Hanwha Life Esports'), ('T1'), ('KT Rolster');

INSERT INTO joueur (pseudo, equipe_id) VALUES
  ('Chovy',     (SELECT id FROM equipe WHERE nom = 'Gen.G')),
  ('Peyz',   (SELECT id FROM equipe WHERE nom = 'Gen.G')),
  ('Canyon',    (SELECT id FROM equipe WHERE nom = 'Gen.G')),

  ('Zeka',   (SELECT id FROM equipe WHERE nom = 'Hanwha Life Esports')),
  ('Viper',    (SELECT id FROM equipe WHERE nom = 'Hanwha Life Esports')),
  ('Delight',   (SELECT id FROM equipe WHERE nom = 'Hanwha Life Esports')),

  ('Faker',   (SELECT id FROM equipe WHERE nom = 'T1')),
  ('Gumayusi',   (SELECT id FROM equipe WHERE nom = 'T1')),
  ('Keria',    (SELECT id FROM equipe WHERE nom = 'T1')),

  ('Bdd',    (SELECT id FROM equipe WHERE nom = 'KT Rolster')),
  ('Deft',    (SELECT id FROM equipe WHERE nom = 'KT Rolster')),
  ('Cuzz',    (SELECT id FROM equipe WHERE nom = 'KT Rolster'));


-- =====================================================================
--  TOURNOI A — « Coupe Automne » · format POULE · démarré
--  Sert à montrer un classement déjà peuplé et son recalcul en direct.
--  Un tableau vide ne prouve rien.
-- =====================================================================
INSERT INTO tournoi (nom, date_debut, format, demarre) VALUES
  ('Coupe Automne', DATE '2026-09-12', 'POULE', TRUE);

INSERT INTO inscription (tournoi_id, equipe_id)
SELECT (SELECT id FROM tournoi WHERE nom = 'Coupe Automne'), id
FROM   equipe
WHERE  nom IN ('Gen.G', 'Hanwha Life Esports', 'T1', 'KT Rolster');

-- Round-robin aller simple : 4 équipes = 6 matchs (RG-41).
-- Quatre sont terminés, deux restent à jouer pour la démonstration.

-- --- Matchs terminés ---
INSERT INTO rencontre (tournoi_id, tour, equipe_a_id, equipe_b_id, score_a, score_b, etat, arbitre_id) VALUES
  ((SELECT id FROM tournoi WHERE nom = 'Coupe Automne'), 1,
   (SELECT id FROM equipe WHERE nom = 'Gen.G'), (SELECT id FROM equipe WHERE nom = 'Hanwha Life Esports'),
   1, 1, 'TERMINE', (SELECT id FROM utilisateur WHERE login = 'arbitre')),

  ((SELECT id FROM tournoi WHERE nom = 'Coupe Automne'), 1,
   (SELECT id FROM equipe WHERE nom = 'Gen.G'), (SELECT id FROM equipe WHERE nom = 'KT Rolster'),
   2, 0, 'TERMINE', (SELECT id FROM utilisateur WHERE login = 'arbitre')),

  ((SELECT id FROM tournoi WHERE nom = 'Coupe Automne'), 1,
   (SELECT id FROM equipe WHERE nom = 'Gen.G'), (SELECT id FROM equipe WHERE nom = 'T1'),
   0, 2, 'TERMINE', (SELECT id FROM utilisateur WHERE login = 'arbitre')),

  ((SELECT id FROM tournoi WHERE nom = 'Coupe Automne'), 1,
   (SELECT id FROM equipe WHERE nom = 'Hanwha Life Esports'), (SELECT id FROM equipe WHERE nom = 'KT Rolster'),
   2, 0, 'TERMINE', (SELECT id FROM utilisateur WHERE login = 'arbitre'));

-- --- Matchs à jouer ---
-- Saisir Hanwha Life Esports 0 – 2 T1 puis T1 3 – 0 KT Rolster reproduit
-- exactement le tableau de référence du §8.1 des règles de gestion :
-- Gen.G et Hanwha Life Esports
-- s'y retrouvent à égalité parfaite, départagés par le seul RG-75.
-- C'est le jeu de test attendu par la tâche 2.8.
INSERT INTO rencontre (tournoi_id, tour, equipe_a_id, equipe_b_id, etat) VALUES
  ((SELECT id FROM tournoi WHERE nom = 'Coupe Automne'), 1,
   (SELECT id FROM equipe WHERE nom = 'Hanwha Life Esports'), (SELECT id FROM equipe WHERE nom = 'T1'), 'PLANIFIE'),

  ((SELECT id FROM tournoi WHERE nom = 'Coupe Automne'), 1,
   (SELECT id FROM equipe WHERE nom = 'T1'), (SELECT id FROM equipe WHERE nom = 'KT Rolster'), 'PLANIFIE');


-- =====================================================================
--  TOURNOI B — « Open Hiver » · format ELIMINATION_DIRECTE · démarré
--  Sert à montrer deux choses qui n'existent pas en poule :
--    - la génération automatique du tour suivant (RG-35)
--    - le refus du match nul (RG-33), cas de démonstration CE-04
-- =====================================================================
INSERT INTO tournoi (nom, date_debut, format, demarre) VALUES
  ('Open Hiver', DATE '2026-09-19', 'ELIMINATION_DIRECTE', TRUE);

INSERT INTO inscription (tournoi_id, equipe_id)
SELECT (SELECT id FROM tournoi WHERE nom = 'Open Hiver'), id
FROM   equipe
WHERE  nom IN ('Gen.G', 'Hanwha Life Esports', 'T1', 'KT Rolster');

-- 4 équipes = puissance de 2 (RG-30), donc 3 matchs sur 2 tours (RG-32).
-- Seules les demi-finales existent : la finale ne sera générée que lorsque
-- les deux seront terminées (RG-35). C'est le moment fort de la démo.
INSERT INTO rencontre (tournoi_id, tour, equipe_a_id, equipe_b_id, etat) VALUES
  ((SELECT id FROM tournoi WHERE nom = 'Open Hiver'), 1,
   (SELECT id FROM equipe WHERE nom = 'Gen.G'), (SELECT id FROM equipe WHERE nom = 'Hanwha Life Esports'), 'PLANIFIE'),

  ((SELECT id FROM tournoi WHERE nom = 'Open Hiver'), 1,
   (SELECT id FROM equipe WHERE nom = 'T1'), (SELECT id FROM equipe WHERE nom = 'KT Rolster'), 'PLANIFIE');
