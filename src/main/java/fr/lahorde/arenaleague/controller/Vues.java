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

    /**
     * Donnée transmise d'un écran à l'autre — en pratique, l'identifiant du
     * tournoi choisi dans la liste.
     *
     * La fabrique de contrôleurs n'injecte que le contexte et la navigation :
     * elle ne sait pas construire un contrôleur qui exigerait un tournoi en
     * paramètre. Plutôt que d'ouvrir la fabrique à des arguments arbitraires,
     * la navigation transporte une valeur, que l'écran d'arrivée réclame avec
     * son type attendu.
     */
    private Object parametre;

    /** Contrôleur actuellement à l'écran, pour pouvoir le libérer. */
    private Object controleurCourant;

    /** Affiche la vue en lui transmettant une donnée de navigation. */
    public void afficher(String nomDeVue, String titre, Object parametre) {
        this.parametre = parametre;
        afficher(nomDeVue, titre);
    }

    /**
     * Donnée transmise par l'écran précédent, consommée au passage : deux
     * lectures successives ne rendraient pas la même chose, ce qui évite
     * qu'un écran hérite par accident du paramètre d'un autre.
     */
    public <T> T parametre(Class<T> type) {
        Object valeur = parametre;
        parametre = null;
        return type.isInstance(valeur) ? type.cast(valeur) : null;
    }

    /** Remplace le contenu de la fenêtre par la vue demandée. */
    public void afficher(String nomDeVue, String titre) {
        // Le contrôleur sortant rend ce qu'il détenait avant d'être remplacé.
        // Sans ce point de sortie, un écran abonné à un service laisserait un
        // écouteur derrière lui à chaque navigation.
        if (controleurCourant instanceof Liberable liberable) {
            liberable.liberer();
        }

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
            Parent racine = chargeur.load();
            controleurCourant = chargeur.getController();
            return racine;
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
