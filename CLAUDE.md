# ArenaLeague — contexte projet

Projet M1 noté, soutenance le **vendredi 9 octobre 2026**, 15 minutes.
Développeur seul. Application JavaFX de gestion de tournois eSport.

Le barème compte : démonstration fonctionnelle 30 pts, maîtrise technique
30 pts, présentation 25 pts, cohérence UML ↔ code 15 pts. Une application qui
tourne et qu'on sait expliquer vaut plus qu'un diagramme parfait.

---

## Règles de conception non négociables

Ces choix ont été pris délibérément et servent d'argumentaire en soutenance.
**Ne pas les défaire sans en parler d'abord.**

### Étanchéité des couches

```
controller  →  service  →  repository  →  model
```

Vérifiable par recherche, et vérifié à chaque tâche :

- aucun `import javafx.*` dans `service`
- aucun `import java.sql.*` dans `service` ni dans `model`
- aucune dépendance technique dans `model`
- aucun `import` de `repository` dans `controller`

### Aucun test de format hors de la fabrique

Dans tout le code métier il n'existe **qu'un seul** `switch` sur `Format` :
celui de `FabriqueFormat`. Les services ne testent jamais le format, ils
demandent une stratégie et lui délèguent.

Si une tâche semble exiger un `if (format == ...)` dans un service, c'est
qu'une méthode manque au contrat `FormatTournoi` — l'ajouter avec une
implémentation par défaut, comme `genererTourSuivant()`.

### Aucun test d'état hors des classes d'état

`MatchService` ne contient aucun `if (statut == ...)`. Il appelle
`match.demarrer()` ou `match.saisirScore()`, et l'état courant accepte ou
refuse. `Termine` ne redéfinit rien : elle hérite des refus par défaut de
`EtatMatch`. C'est ce qui distingue le pattern State d'un enum déguisé.

### Le contrôle de droits est dans le service

Masquer un bouton dans l'IHM est du confort, pas une sécurité. Le refus réel
est dans `SessionContext.exigerDroitXxx()`, appelé en **première instruction**
des méthodes de service — un refus ne doit consommer ni ligne ni séquence.

La visibilité des boutons est déduite de `peutCreerTournoi()`, jamais d'un
test sur un nom de rôle.

### Le tri du classement est en Java, jamais en SQL

`Comparateurs` utilise un `Collator`. Une collation française et une
collation C ne classent pas les mêmes chaînes dans le même ordre : confier le
tri à un `ORDER BY` rendrait le classement dépendant de l'installation
PostgreSQL du poste.

Le dernier critère de départage (RG-75, ordre alphabétique) existe pour
garantir un ordre **total**. Sans lui, deux exécutions du même calcul
pourraient produire deux classements différents et les tests deviendraient
instables.

### Les transactions sont ouvertes par le service

Jamais par un DAO. `Transactions.enTransaction(...)` encadre les opérations
composées. Les appels imbriqués rejoignent la transaction en cours.
`ConnectionProvider` refuse tout accès hors transaction, volontairement.

### Traçabilité RG-xx

Chaque règle métier porte un identifiant `RG-01` à `RG-83`, défini dans le
document *Règles de gestion* (hors dépôt). Cet identifiant apparaît en
commentaire sur les contraintes SQL, dans le code service, et dans les tests.

**Toute nouvelle règle doit citer son RG.** Si aucun ne correspond, c'est que
la règle n'est pas dans le document : le signaler plutôt que d'inventer.

---

## Décisions de périmètre déjà tranchées

| Sujet | Décision | Ne pas |
|-------|----------|--------|
| Correction d'un score après clôture | **Interdite**, sans exception | ajouter un état `CONTESTE` |
| Élimination directe | **Puissance de 2 stricte** (4, 8, 16, 32) | implémenter les byes |
| Poules | Poule unique, **sans phase finale** | enchaîner poules → élimination |
| Barème poule | 3 / 1 / 0 | — |
| Forfait | **Implémenté** — RG-47 à RG-49. Match par match, 0–3 en poule, 0–1 en élimination | créer un état dédié |
| Petite finale | **Aucune** (RG-37) | — |
| Export CSV/PDF | **Hors périmètre** pour l'instant | — |
| Docker | **Écarté** par choix de l'utilisateur | recréer un docker-compose |
| Framework d'injection | **Aucun** — câblage manuel dans `AppContext` | introduire Spring |
| ORM | **Aucun** — JDBC et DAO écrits à la main | introduire Hibernate/JPA |

---

## Conventions de code

- **Français** pour les noms de classes, méthodes, variables et messages
  d'erreur. Les messages d'exception sont lus par l'utilisateur final : ils
  disent quoi faire, pas seulement ce qui ne va pas.
- Les commentaires expliquent **pourquoi**, pas quoi. Un commentaire qui
  paraphrase le code est à supprimer.
- Table `rencontre` et non `match` : `MATCH` est un mot réservé SQL:2003.
- Toute requête est un `PreparedStatement` paramétré. **Zéro** valeur
  concaténée dans une chaîne SQL.
- Toute ressource JDBC est ouverte en `try-with-resources`.
- Aucune `SQLException` ne franchit la couche `repository` : elle est traduite
  en `PersistenceException`, cause conservée.

---

## Tests

```bash
mvn test
```

50 tests, 7 classes. Les repositories sont remplacés par des implémentations
en mémoire — aucune base nécessaire.

Cinq classes couvrent la couche service. La sixième, `EmpreintesDuSeedTest`,
est d'une autre nature : elle confronte les empreintes de `V2__seed.sql` au
**vrai** `BCryptVerificateur`. Les autres injectent un vérificateur factice —
le bon choix pour tester la logique, mais c'est ce qui a laissé passer des
empreintes `$2b$` illisibles avec 38 tests au vert. **Ne pas la neutraliser
pour gagner une demi-seconde de build.**

`CasErreurDemonstrationTest` couvre les cinq cas `CE-01` à `CE-05` du script
de démonstration, **chaque test nommé par son identifiant**. Si la
démonstration change, ces tests changent avec elle.

Le tableau de référence du classement est celui du §8.1 des règles de gestion,
et c'est **aussi** le jeu de données de `V2__seed.sql` : un seul jeu de
chiffres pour la démo et pour les tests.

```
Rang Equipe                Pts  J  V  N  D  BM BE Diff
 1   T1                     9   3  3  0  0   7  0  +7
 2   Gen.G                  4   3  1  1  1   3  3   0
 3   Hanwha Life Esports    4   3  1  1  1   3  3   0
 4   KT Rolster             0   3  0  0  3   0  7  -7
```

Gen.G et Hanwha Life Esports sont à égalité parfaite sur les points, la
différence, les points marqués **et** leur confrontation directe (nul 1–1).
Seul RG-75 les départage. **Si ce tableau change, quelque chose est cassé.**

Les noms d'équipes ne sont donc **pas interchangeables** : c'est l'ordre
alphabétique qui place Gen.G devant Hanwha Life Esports. Renommer une équipe
sans vérifier cet ordre casse la démonstration du dernier critère de
départage — le seul argument qui justifie son existence.

Le seed contient les **dix équipes de la LCK 2026**, mais seules les quatre
ci-dessus sont inscrites à un tournoi. Les six autres forment un vivier : huit
équipes disponibles permettent de générer une élimination directe complète,
à trois tours. Les pseudos de joueurs sont **illustratifs** — les rosters
changent à chaque split, et seule leur quantité compte (RG-10).

---

## Base de données

PostgreSQL 18 installé localement (`postgresql-x64-18`, service automatique),
ou H2 embarqué en repli (`app.profile=h2` dans `application.properties`). Le
**même** SQL de migration s'applique aux deux.

`psql` n'est pas dans le `PATH` : l'appeler par son chemin complet,
`C:\Program Files\PostgreSQL\18\bin\psql.exe`.

```bash
psql -U postgres -f scripts/creer-base.sql          # une seule fois
psql -U postgres -f scripts/reinitialiser-base.sql  # avant chaque répétition
```

Ne jamais jouer `V1__schema.sql` ou `V2__seed.sql` à la main : Flyway tient
une table de suivi et refuserait ensuite de rejouer.

Comptes : `orga`/`orga` (Organisateur) et `arbitre`/`arbitre` (Arbitre).

---

## État d'avancement

**Phase 1 — conception : terminée.** Cinq diagrammes UML, règles de gestion,
périmètre, dossier d'architecture, script de démonstration. Documents hors
dépôt.

**Phase 2 — production**, tâches suivies dans ClickUp
(dossier *ArenaLeague — Projet*, liste *Phase 2 — Production*) :

- 2.0 à 2.13 : **vérifiées sur poste** le 10 septembre 2026. `mvn clean
  install` réussit, les 50 tests passent, et le scénario nominal se joue
  entièrement en profil H2 — jusqu'à la finale générée seule et au classement
  recalculé à chaque score, sans rafraîchissement. Les cas d'erreur CE-01,
  CE-02 et CE-04 sont démontrables.
- Restent : GATE 2, GATE 3, et les tâches 2.14 (export, hors périmètre) et
  2.15 (README).

**Toutes les erreurs passent par `GestionnaireErreurs`** : message métier
affiché tel quel, panne technique traduite en phrase actionnable et tracée
avec sa pile. Ne pas rajouter de `try/catch` dans un contrôleur — il n'y en a
plus aucun, et c'est ce qui garantit l'uniformité des messages.

**Ctrl+N** ouvre la création quel que soit le rôle : c'est volontaire, c'est
le chemin de CE-01.

**Avant chaque répétition de la démonstration**, supprimer `data/` : Flyway
rejoue le seed. Le tableau de référence du classement n'apparaît que si l'on
saisit exactement Hanwha Life Esports 0–2 T1 puis T1 3–0 KT Rolster.

**Environnement** : ni Maven ni JDK ne sont installés séparément sur le poste.
Les deux viennent d'IntelliJ IDEA Community (Maven 3.9.9, JBR 21) :

```
$IDEA = "C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.6.2"
$env:JAVA_HOME = "$IDEA\jbr"
$env:PATH = "$IDEA\plugins\maven\lib\maven3\bin;$env:JAVA_HOME\bin;$env:PATH"
```

**Avant chaque lancement**, valider les vues — une erreur FXML ne se voit ni à
la compilation ni au démarrage, seulement au clic qui ouvre l'écran :

```bash
python -c "import glob,xml.etree.ElementTree as ET; [ET.parse(f) for f in glob.glob('src/main/resources/fxml/*.fxml')]"
```

Attention : XML interdit `--` dans un commentaire, ce qui est pourtant un
séparateur naturel en Java. C'est ce qui a rendu `creation-tournoi.fxml`
illisible une première fois.

**Une seule instance à la fois** : H2 en mode fichier est à connexion
exclusive. Une seconde application affiche « la base est peut-être en cours
d'utilisation ». Supprimer `data/` remet le jeu de données de référence.

---

## Méthode de travail attendue

- **Une tâche ClickUp à la fois**, lue avant de commencer : sa Definition of
  Done fait foi.
- **Vérifier avant d'affirmer.** Compiler, exécuter, et si un contrôle passe,
  s'assurer qu'il passe pour la bonne raison. Un test vert par accident est
  plus dangereux qu'un test absent.
- **Signaler les écarts** entre le code et les documents de conception plutôt
  que de les laisser s'accumuler — la cohérence UML ↔ code vaut 15 points.
- **Ne pas cocher une Definition of Done qu'on n'a pas pu vérifier.** Laisser
  la tâche en cours et dire ce qui manque.
- **Mettre le README à jour** à chaque commit.
