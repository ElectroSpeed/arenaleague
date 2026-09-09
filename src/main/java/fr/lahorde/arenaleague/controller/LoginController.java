package fr.lahorde.arenaleague.controller;

import fr.lahorde.arenaleague.AppContext;
import fr.lahorde.arenaleague.model.Utilisateur;
import fr.lahorde.arenaleague.model.exception.ArenaLeagueException;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

/**
 * Écran de connexion.
 *
 * **Ce contrôleur ne contient aucune logique métier.** Il lit deux champs,
 * appelle AuthService, et traduit le résultat en affichage. Il ne sait pas ce
 * qu'est une empreinte BCrypt, ne connaît aucun rôle par son nom, et n'a
 * aucun accès à la base.
 *
 * Le seul « if » sur le rôle est celui qui choisit l'écran suivant, et encore :
 * il passe par peutCreerTournoi(), donc par le polymorphisme, jamais par un
 * test sur un enum.
 */
public final class LoginController {

    @FXML private TextField champLogin;
    @FXML private PasswordField champMotDePasse;
    @FXML private Label messageErreur;
    @FXML private Button boutonConnexion;

    private final AppContext contexte;
    private final Vues vues;

    public LoginController(AppContext contexte, Vues vues) {
        this.contexte = contexte;
        this.vues = vues;
    }

    @FXML
    private void initialize() {
        messageErreur.setVisible(false);
        messageErreur.setManaged(false);

        // Entrée valide la connexion : on ne fait pas chercher la souris à
        // l'arbitre pendant une démonstration chronométrée.
        champMotDePasse.setOnAction(evenement -> seConnecter());
        champLogin.setOnAction(evenement -> champMotDePasse.requestFocus());
    }

    @FXML
    private void seConnecter() {
        masquerErreur();

        // Le mot de passe est extrait en char[] et effacé par AuthService.
        // On vide aussi le champ : rien ne doit rester à l'écran.
        char[] motDePasse = champMotDePasse.getText().toCharArray();

        try {
            boutonConnexion.setDisable(true);
            Utilisateur connecte = contexte.auth().authentifier(champLogin.getText(), motDePasse);
            champMotDePasse.clear();
            vues.afficher("accueil", connecte.login());

        } catch (ArenaLeagueException e) {
            // Exception métier : message tel quel, il est écrit pour l'utilisateur.
            afficherErreur(e.getMessage());
            champMotDePasse.clear();
            champMotDePasse.requestFocus();

        } catch (RuntimeException e) {
            // Panne technique : on n'expose pas la trace à l'écran.
            afficherErreur("La connexion à la base a échoué. Vérifiez que PostgreSQL est démarré.");
            System.err.println("Erreur technique à la connexion : " + e);

        } finally {
            boutonConnexion.setDisable(false);
        }
    }

    private void afficherErreur(String message) {
        messageErreur.setText(message);
        messageErreur.setVisible(true);
        messageErreur.setManaged(true);
    }

    private void masquerErreur() {
        messageErreur.setVisible(false);
        messageErreur.setManaged(false);
    }
}
