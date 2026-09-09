package fr.lahorde.arenaleague.controller;

import fr.lahorde.arenaleague.AppContext;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.lang.reflect.Constructor;

/**
 * Chargement des vues FXML et navigation.
 *
 * Le point important est la **fabrique de contrôleurs**. Par défaut, JavaFX
 * instancie les contrôleurs avec leur constructeur sans argument : ils
 * devraient alors aller chercher leurs services eux-mêmes, typiquement dans
 * une variable statique. C'est exactement ce qu'on veut éviter.
 *
 * Ici, chaque contrôleur déclare un constructeur prenant l'AppContext, et
 * cette fabrique le lui fournit. L'injection reste faite par constructeur,
 * sans framework et sans état global.
 */
public final class Vues {

    private static final String CHEMIN_FXML = "/fxml/";
    private static final String FEUILLE_DE_STYLE = "/css/app.css";

    private final AppContext contexte;
    private final Stage fenetre;

    public Vues(AppContext contexte, Stage fenetre) {
        this.contexte = contexte;
        this.fenetre = fenetre;
    }

    /** Remplace le contenu de la fenêtre par la vue demandée. */
    public void afficher(String nomDeVue, String titre) {
        Parent racine = charger(nomDeVue);
        Scene scene = new Scene(racine);
        var style = getClass().getResource(FEUILLE_DE_STYLE);
        if (style != null) {
            scene.getStylesheets().add(style.toExternalForm());
        }
        fenetre.setScene(scene);
        fenetre.setTitle("ArenaLeague — " + titre);
        fenetre.centerOnScreen();
    }

    private Parent charger(String nomDeVue) {
        try {
            FXMLLoader chargeur = new FXMLLoader(
                getClass().getResource(CHEMIN_FXML + nomDeVue + ".fxml"));
            chargeur.setControllerFactory(this::instancier);
            return chargeur.load();
        } catch (IOException e) {
            throw new IllegalStateException("Chargement de la vue " + nomDeVue + " impossible", e);
        }
    }

    /**
     * Un contrôleur reçoit le contexte et la navigation par constructeur.
     * Le repli sur le constructeur sans argument sert aux vues purement
     * décoratives, qui n'ont besoin d'aucun service.
     */
    private Object instancier(Class<?> typeDeControleur) {
        try {
            for (Constructor<?> c : typeDeControleur.getDeclaredConstructors()) {
                Class<?>[] parametres = c.getParameterTypes();
                if (parametres.length == 2
                    && parametres[0] == AppContext.class
                    && parametres[1] == Vues.class) {
                    return c.newInstance(contexte, this);
                }
            }
            return typeDeControleur.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                "Instanciation impossible du contrôleur " + typeDeControleur.getSimpleName()
                + ". Attendu : un constructeur (AppContext, Vues).", e);
        }
    }
}
