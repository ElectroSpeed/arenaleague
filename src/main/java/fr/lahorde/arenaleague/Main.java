package fr.lahorde.arenaleague;

import fr.lahorde.arenaleague.config.AppConfig;
import fr.lahorde.arenaleague.config.Database;
import javafx.application.Application;
import javafx.stage.Stage;

/**
 * Démarrage de l'application.
 *
 * Le câblage des dépendances est fait à la main dans AppContext, pas par un
 * framework. C'est un choix assumé : sur un projet noté sur la compréhension
 * de l'architecture, écrire soi-même le câblage prouve qu'on l'a comprise.
 */
public class Main extends Application {

    private AppContext contexte;

    @Override
    public void init() {
        AppConfig config = new AppConfig();
        Database database = new Database(config);
        this.contexte = new AppContext(config, database);
    }

    @Override
    public void start(Stage stage) {
        stage.setTitle("ArenaLeague");
        // L'écran de connexion arrive avec la tâche 2.9.
        stage.show();
    }

    @Override
    public void stop() {
        if (contexte != null) {
            contexte.fermer();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
