# ArenaLeague

Application de gestion de tournois eSport amateurs — projet M1, La Horde.

Un organisateur crée un tournoi, un arbitre en saisit les résultats, le
classement se recalcule automatiquement à chaque score enregistré.

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

**Option B — PostgreSQL installé localement**

Installer PostgreSQL 16 (postgresql.org/download), puis créer la base :

```sql
CREATE USER arenaleague WITH PASSWORD 'arenaleague';
CREATE DATABASE arenaleague OWNER arenaleague;
```

Laisser `app.profile=postgres`. Le **même** SQL de migration s'applique
aux deux moteurs : H2 tourne en mode compatibilité PostgreSQL.

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

---

## Traçabilité

Chaque règle métier porte un identifiant stable `RG-xx`, défini dans le
document *Règles de gestion*. Cet identifiant se retrouve :

- en commentaire sur les contraintes SQL (`V1__schema.sql`)
- en commentaire dans le code de la couche service
- dans le nom des tests : `testRG33_egaliteInterditeEnEliminationDirecte`

Répondre à « où est implémentée cette règle ? » se fait par une recherche.

---

## Tests

```bash
mvn test
```

Les tests portent sur la couche service — c'est là que vit la logique.
Les repositories sont remplacés par des implémentations en mémoire, donc
aucune base n'est nécessaire.

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
