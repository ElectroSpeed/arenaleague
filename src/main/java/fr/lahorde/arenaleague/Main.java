package fr.lahorde.arenaleague;

import fr.lahorde.arenaleague.config.AppConfig;
import fr.lahorde.arenaleague.config.Database;
import fr.lahorde.arenaleague.controller.Vues;
import javafx.application.Application;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

/**
 * Démarrage de l'application.
 *
 * init() construit le contexte — configuration, base, migrations Flyway,
 * câblage des services — avant que la moindre fenêtre s'affiche. Si la base
 * est injoignable, l'utilisateur voit un message clair plutôt qu'une trace de
 * pile sur une fenêtre à moitié dessinée.
 */
public class Main extends Application {

    private AppContext contexte;
    private String erreurDeDemarrage;

    @Override
    public void init() {
        try {
            AppConfig config = new AppConfig();
            Database database = new Database(config);
            this.contexte = new AppContext(config, database);
        } catch (RuntimeException e) {
            // On ne lève pas ici : sans fenêtre, le message serait invisible.
            this.erreurDeDemarrage = e.getMessage();
            System.err.println("Démarrage impossible : " + e);
        }
    }

    @Override
    public void start(Stage fenetre) {
        if (contexte == null) {
            signalerEchec();
            return;
        }
        new Vues(contexte, fenetre).afficher("login", "Connexion");
        fenetre.show();
    }

    private void signalerEchec() {
        Alert alerte = new Alert(Alert.AlertType.ERROR);
        alerte.setTitle("ArenaLeague");
        alerte.setHeaderText("L'application n'a pas pu démarrer");
        alerte.setContentText(
            erreurDeDemarrage
            + "\n\nVérifiez que PostgreSQL est démarré et que la base a bien été créée :"
            + "\n    psql -U postgres -f scripts/creer-base.sql"
            + "\n\nÀ défaut, basculez sur H2 dans application.properties :"
            + "\n    app.profile=h2");
        alerte.showAndWait();
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
