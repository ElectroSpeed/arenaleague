# ArenaLeague

Application de gestion de tournois eSport amateurs — projet M1, La Horde.

Un organisateur crée un tournoi, un arbitre en saisit les résultats, le
classement se recalcule automatiquement à chaque score enregistré.

**Java 21 · JavaFX 21 · PostgreSQL 16 · JDBC + Flyway · Maven**

---

## Démarrage

### 1. Configurer

```bash
cp src/main/resources/application.properties.example src/main/resources/application.properties
```

### 2. Préparer la base

Deux options, au choix, sans aucun conteneur.

**Option A — H2 embarqué (rien à installer)**

Dans `application.properties` :

```properties
app.profile=h2
```

La base est un simple fichier dans `./data/`. Les migrations Flyway
s'appliquent seules au premier lancement : schéma (`V1`) puis jeu de
données de démonstration (`V2`). Supprimer le dossier `data/` remet tout
à zéro — pratique pour répéter la démonstration.

**Option B — PostgreSQL installé localement** *(mode par défaut)*

Installer PostgreSQL 16 depuis postgresql.org/download, puis :

```bash
psql -U postgres -f scripts/creer-base.sql
```

Le script crée le rôle `arenaleague` et la base du même nom. Laisser
`app.profile=postgres` dans la configuration, les valeurs par défaut y
correspondent déjà.

Pour repartir de zéro entre deux répétitions de la démonstration :

```bash
psql -U postgres -f scripts/reinitialiser-base.sql
```

Le **même** SQL de migration s'applique aux deux moteurs : H2 tourne en
mode compatibilité PostgreSQL.

### 3. Compiler et lancer

```bash
mvn clean install
mvn javafx:run
```

Ou, à partir du jar packagé :

```bash
java -jar target/arenaleague-1.0.0.jar
```

---

## Basculer de moteur

Une seule ligne à changer dans `application.properties` :
`app.profile=h2` ou `app.profile=postgres`.

Aucun script de migration n'est à adapter — c'est tout l'intérêt d'avoir
gardé du SQL standard plutôt que des extensions propriétaires. Si
PostgreSQL refuse de démarrer le jour de la soutenance, le basculement
prend dix secondes.

**Le tri du classement ne dépend pas du moteur.** Le départage
alphabétique (RG-75) est appliqué en Java, pas par une clause `ORDER BY`.
Une collation française et une collation C ne classent pas `École` et
`Ecole` de la même façon : faire porter le tri par la base rendrait le
classement dépendant de l'installation.

---

## Comptes de démonstration

| Login     | Mot de passe | Rôle         |
|-----------|--------------|--------------|
| `orga`    | `orga`       | Organisateur |
| `arbitre` | `arbitre`    | Arbitre      |

Les mots de passe sont stockés en BCrypt, jamais en clair.

---

## État d'avancement

### Phase 1 — Conception · terminée

Les cinq diagrammes UML, les règles de gestion (44 règles `RG-01` à `RG-83`),
le périmètre fonctionnel, le dossier d'architecture et le script de
démonstration. Livrés hors dépôt, dans les documents de projet.

### Phase 2 — Production

| Tâche | État | Contenu |
|-------|------|---------|
| 2.0 Setup | à valider | Maven, arborescence en couches, Flyway |
| 2.1 Schéma et jeu de données | à valider | `V1__schema.sql`, `V2__seed.sql` |
| 2.2 Couche model | terminée | 21 classes, entités et interfaces de patterns |
| 2.3 Couche repository | terminée | 4 DAO JDBC, transactions |
| 2.4 Authentification et rôles | terminée | BCrypt, contrôle de droits |
| 2.5 Tournoi et Strategy de format | terminée | Élimination directe, poule |
| 2.6 Match et machine à états | terminée | Saisie de score, RG-63 |
| 2.7 Classement | terminée | Cache invalidé par l'Observer |
| 2.8 Tests unitaires | à valider | 38 tests, 5 classes |
| 2.9 IHM connexion | à valider | `login.fxml`, `accueil.fxml` |
| 2.10 à 2.13 IHM | à faire | Création, saisie, classement en direct |

*« à valider » signifie que le code est écrit mais qu'une vérification sur
poste reste nécessaire : `mvn clean install`, `mvn test`, `mvn javafx:run`.*

---

## Architecture

Quatre couches, avec une règle de dépendance stricte : chaque couche ne
connaît que celle immédiatement en dessous, aucune ne remonte.

```
controller  →  service  →  repository  →  model
```

| Couche       | Contenu                                                        | Ne connaît pas          |
|--------------|----------------------------------------------------------------|-------------------------|
| `controller` | Contrôleurs JavaFX, un par écran FXML                          | JDBC, SQL               |
| `service`    | Droits, règles de format, transitions d'état, classement       | JavaFX, FXML            |
| `repository` | DAO écrits à la main, mapping `ResultSet` → objet              | les règles métier       |
| `model`      | Entités du domaine, aucune annotation, aucune dépendance technique | tout le reste       |

Cette règle est vérifiable en dix secondes : aucun import `javafx.*` dans
`service`, aucun import `java.sql.*` dans `service`, rien de technique
dans `model`.

Le câblage des dépendances est fait à la main dans `AppContext`, le seul
endroit où les objets sont assemblés. Pas de framework d'injection : sur
un projet noté sur la compréhension de l'architecture, écrire soi-même le
câblage prouve qu'on l'a comprise.

### Patterns

| Pattern        | Où                            | Ce qu'il remplace                          |
|----------------|-------------------------------|--------------------------------------------|
| **Strategy**   | `service/format`              | un `if (format == POULE)` répété 3 fois     |
| **State**      | `model/etat`                  | un enum + des tests conditionnels           |
| **Repository** | `repository`                  | du SQL dans les services                    |
| **Observer**   | `ObservableList` du classement | un `tableView.refresh()` manuel            |

Les sous-paquets `service/format` et `model/etat` rendent les deux
premiers patterns visibles dans l'arborescence, avant même d'ouvrir un
fichier.

Dans **tout le code métier**, il n'existe qu'un seul `switch` sur le format —
dans `FabriqueFormat`. Ajouter un troisième format demanderait une valeur
d'enum, une classe, une ligne dans la fabrique. Aucune modification de
`TournoiService`, `MatchService` ou `ClassementService`.

De même, `MatchService` ne teste jamais l'état d'un match : il appelle
`match.demarrer()` ou `match.saisirScore()`, et c'est l'état courant qui
accepte ou refuse.

---

## Traçabilité

Chaque règle métier porte un identifiant stable `RG-xx`, défini dans le
document *Règles de gestion*. Cet identifiant se retrouve :

- en commentaire sur les contraintes SQL (`V1__schema.sql`)
- en commentaire dans le code de la couche service
- dans le nom des tests : `testRG33_egaliteInterditeEnEliminationDirecte`

Répondre à « où est implémentée cette règle ? » se fait par une recherche.

Les cinq cas d'erreur du script de démonstration (`CE-01` à `CE-05`) ont
chacun leur test dans `CasErreurDemonstrationTest`, **nommé par son
identifiant**.

---

## Tests

```bash
mvn test
```

38 tests répartis en 5 classes, tous sur la couche service — c'est là que vit
la logique. Les repositories sont remplacés par des implémentations en
mémoire, donc aucune base n'est nécessaire.

| Classe | Couvre |
|--------|--------|
| `AuthServiceTest` | droits, effacement du mot de passe, message indifférencié |
| `TournoiServiceTest` | inscriptions et démarrage, RG-11 et RG-20 à RG-23 |
| `MatchServiceTest` | cycle nominal, transitions interdites, Observer, propagation |
| `ClassementPouleTest` | un test par critère de départage RG-71 à RG-75, déterminisme |
| `CasErreurDemonstrationTest` | les cinq cas d'erreur de la démonstration |

Le tableau de référence du calcul de classement est celui du §8.1 des
règles de gestion : deux équipes y sont à égalité parfaite sur les points,
la différence, les points marqués **et** la confrontation directe. Seul
l'ordre alphabétique les départage. Sans ce dernier critère, deux
exécutions du même calcul pourraient produire deux classements différents.

---

## Structure

```
arenaleague/
├── pom.xml
├── scripts/                        amorçage et remise à zéro PostgreSQL
└── src/
    ├── main/java/fr/lahorde/arenaleague/
    │   ├── Launcher.java           point d'entrée du jar
    │   ├── Main.java               démarrage JavaFX
    │   ├── AppContext.java         composition root
    │   ├── config/                 configuration et source de données
    │   ├── controller/
    │   ├── service/
    │   │   ├── format/             Strategy
    │   │   └── exception/          hiérarchie ArenaLeagueException
    │   ├── repository/
    │   └── model/
    │       └── etat/               State
    ├── main/resources/
    │   ├── fxml/                   une vue par écran
    │   ├── css/
    │   └── db/migration/           V1__schema.sql, V2__seed.sql
    └── test/java/...
```
