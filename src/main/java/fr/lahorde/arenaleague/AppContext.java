package fr.lahorde.arenaleague;

import fr.lahorde.arenaleague.config.AppConfig;
import fr.lahorde.arenaleague.config.Database;

/**
 * Composition root : le seul endroit où les dépendances sont assemblées.
 *
 * Chaque couche reçoit ce dont elle a besoin par constructeur et ne va jamais
 * le chercher elle-même. C'est ce qui rend la couche service testable sans
 * base de données : les tests injectent des repositories en mémoire.
 *
 * Règle de dépendance, vérifiable par une recherche dans l'IDE :
 *   controller -> service -> repository -> model
 * Aucun import javafx.* dans service, aucun import java.sql.* dans service,
 * aucune dépendance technique dans model.
 */
public final class AppContext {

    private final AppConfig config;
    private final Database database;

    // Les repositories arrivent avec la tâche 2.3, les services avec 2.4 à 2.7.

    public AppContext(AppConfig config, Database database) {
        this.config = config;
        this.database = database;
    }

    public AppConfig config() {
        return config;
    }

    public Database database() {
        return database;
    }

    public void fermer() {
        database.close();
    }
}
