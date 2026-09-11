package fr.lahorde.arenaleague.controller;

import fr.lahorde.arenaleague.model.exception.ArenaLeagueException;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Traduction des erreurs en messages destinés à l'utilisateur.
 *
 * Avant cette classe, seize blocs `catch` répétaient le même geste dans
 * quatre contrôleurs, avec des formulations qui divergeaient déjà. La règle
 * tient en deux lignes et n'existe désormais qu'ici :
 *
 * - une **exception métier** hérite d'ArenaLeagueException. Son message est
 *   écrit pour l'utilisateur final : il est affiché **tel quel**, sans
 *   reformulation. C'est ce qui fait que « Action réservée à l'Organisateur »
 *   (CE-01) et le refus de saisie sur match clôturé (CE-02) apparaissent
 *   exactement comme les règles de gestion les énoncent ;
 * - une **panne technique** est tout le reste. L'utilisateur reçoit une
 *   phrase qui dit quoi faire, jamais une trace ; la trace complète, elle,
 *   part dans le journal — masquée à l'écran ne veut pas dire perdue.
 *
 * Le choix de l'encart plutôt que de la boîte de dialogue est délibéré pour
 * les erreurs qui ont un contexte à l'écran : une modale coupe une
 * démonstration chronométrée et fait perdre de vue ce qu'on était en train
 * de faire. La boîte est réservée à ce qui n'a nulle part où s'afficher —
 * un écran qui ne s'ouvre pas, une erreur hors de tout formulaire.
 */
public final class GestionnaireErreurs {

    private static final DateTimeFormatter HORODATAGE =
        DateTimeFormatter.ofPattern("HH:mm:ss");

    /**
     * Journal sur disque, à côté de l'application.
     *
     * La console ne suffit pas : lancée par lancer.bat, l'application
     * tourne sous javaw, qui n'en ouvre aucune — et rediriger la sortie
     * depuis le script ne fonctionne pas, `start` appliquant la
     * redirection à lui-même plutôt qu'au processus lancé.
     *
     * L'écrire depuis l'application règle le problème une fois pour
     * toutes : le journal existe quel que soit le mode de lancement, jar,
     * Maven ou IDE.
     */
    private static final Path JOURNAL = Path.of("journal.log");

    private final Label encart;
    private final String profil;

    /**
     * @param encart étiquette où afficher le message, ou null pour n'utiliser
     *               que la boîte de dialogue
     * @param profil moteur actif, pour ne pas accuser PostgreSQL quand
     *               l'application tourne sur H2
     */
    public GestionnaireErreurs(Label encart, String profil) {
        this.encart = encart;
        this.profil = profil;
    }

    /**
     * Exécute une action et traduit toute erreur qui en sort.
     *
     * @return true si l'action est allée au bout, false si elle a été refusée
     *         ou a échoué — l'appelant sait ainsi s'il doit enchaîner.
     */
    public boolean executer(String operation, Runnable action) {
        return calculer(operation, () -> {
            action.run();
            return Boolean.TRUE;
        }).isPresent();
    }

    /** Même chose pour une action qui produit une valeur. */
    public <T> Optional<T> calculer(String operation, Supplier<T> action) {
        masquer();
        try {
            return Optional.ofNullable(action.get());

        } catch (ArenaLeagueException e) {
            // Message métier : écrit pour l'utilisateur, affiché tel quel.
            afficher(e.getMessage());
            journaliser(operation, e, false);
            return Optional.empty();

        } catch (RuntimeException e) {
            journaliser(operation, e, true);
            afficher(messageTechnique(operation));
            return Optional.empty();
        }
    }

    /** Erreur sans contexte d'affichage : la boîte est alors justifiée. */
    public void signalerEnDialogue(String titre, String operation, RuntimeException e) {
        journaliser(operation, e, !(e instanceof ArenaLeagueException));
        boiteErreur(titre, e instanceof ArenaLeagueException
            ? e.getMessage()
            : messageTechnique(operation));
    }

    public void afficher(String message) {
        if (encart == null) {
            boiteErreur("ArenaLeague", message);
            return;
        }
        encart.setText(message);
        encart.setVisible(true);
        encart.setManaged(true);
    }

    public void masquer() {
        if (encart != null) {
            encart.setVisible(false);
            encart.setManaged(false);
        }
    }

    /**
     * Phrase honnête : elle nomme le moteur réellement configuré. L'ancien
     * message conseillait de vérifier PostgreSQL même en profil H2, ce qui
     * envoyait chercher la panne au mauvais endroit.
     */
    private String messageTechnique(String operation) {
        return "Impossible de " + operation + " : la base de données ne répond pas. "
             + ("h2".equals(profil)
                 ? "Le profil actif est H2 ; supprimez le dossier data/ et relancez."
                 : "Le profil actif est PostgreSQL ; vérifiez que le service est démarré.")
             + " Le détail est dans le journal de l'application.";
    }

    /**
     * Toute erreur laisse une trace, y compris celles qui n'atteignent pas
     * l'écran. Un refus métier n'est pas un incident : il est noté en une
     * ligne. Une panne technique emporte sa pile d'appels — sans elle, la
     * trace ne sert à rien le jour où il faut comprendre.
     */
    private void journaliser(String operation, RuntimeException e, boolean avecPile) {
        String entete = "[" + LocalDateTime.now().format(HORODATAGE) + "] "
                      + (avecPile ? "PANNE" : "refus") + " — " + operation + " — " + e;
        String ligne = entete;
        if (avecPile) {
            StringWriter tampon = new StringWriter();
            e.printStackTrace(new PrintWriter(tampon));
            ligne = entete + System.lineSeparator() + tampon;
        }
        System.err.println(ligne);
        ecrireAuJournal(ligne);
    }

    /**
     * Un journal qui échoue ne doit jamais faire échouer l'application :
     * disque plein, dossier en lecture seule, peu importe — on abandonne
     * la trace, pas l'action de l'utilisateur.
     */
    private static void ecrireAuJournal(String ligne) {
        try {
            Files.writeString(JOURNAL, ligne + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException | RuntimeException ignore) {
            // Volontairement silencieux : voir le commentaire ci-dessus.
        }
    }

    private static void boiteErreur(String titre, String message) {
        Alert alerte = new Alert(Alert.AlertType.ERROR);
        alerte.setTitle(titre);
        alerte.setHeaderText(null);
        alerte.setContentText(message);
        alerte.showAndWait();
    }

    /**
     * Filet de sécurité : rien ne doit disparaître dans la console.
     *
     * Une exception non rattrapée sur le fil JavaFX ne fait pas planter la
     * fenêtre — elle laisse l'application **figée en apparence**, sans que
     * l'utilisateur sache que quelque chose a échoué. C'est le pire des cas
     * en démonstration, et c'est exactement ce que la Definition of Done
     * interdit. Installé une fois au démarrage.
     */
    public static void installerFiletDeSecurite(String profil) {
        GestionnaireErreurs secours = new GestionnaireErreurs(null, profil);
        Thread.setDefaultUncaughtExceptionHandler((fil, erreur) -> {
            secours.journaliser("exécuter l'action demandée",
                erreur instanceof RuntimeException e ? e : new RuntimeException(erreur), true);
            Platform.runLater(() -> boiteErreur("ArenaLeague",
                "Une erreur inattendue s'est produite. L'application reste utilisable, "
                + "mais la dernière action n'a probablement pas abouti. "
                + "Le détail est dans le journal."));
        });
    }
}
