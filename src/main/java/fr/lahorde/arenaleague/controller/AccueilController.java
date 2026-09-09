package fr.lahorde.arenaleague.controller;

import fr.lahorde.arenaleague.AppContext;
import fr.lahorde.arenaleague.model.Utilisateur;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;

/**
 * Écran principal après connexion.
 *
 * Le bouton « Créer un tournoi » n'est visible que pour un Organisateur.
 * **Ce masquage est du confort, pas une sécurité.** Le contrôle réel est dans
 * TournoiService, qui refuse l'appel quelle que soit l'interface — c'est ce
 * que démontre le cas CE-01, en appelant le service directement.
 *
 * La visibilité est déduite de peutCreerTournoi(), donc du polymorphisme.
 * Aucun test sur un nom de rôle ici non plus.
 */
public final class AccueilController {

    @FXML private Label nomUtilisateur;
    @FXML private Label badgeRole;
    @FXML private Button boutonCreerTournoi;

    private final AppContext contexte;
    private final Vues vues;

    public AccueilController(AppContext contexte, Vues vues) {
        this.contexte = contexte;
        this.vues = vues;
    }

    @FXML
    private void initialize() {
        Utilisateur connecte = contexte.session().exigerConnecte();

        nomUtilisateur.setText(connecte.login());
        badgeRole.setText(connecte.role().name());

        boolean organisateur = connecte.peutCreerTournoi();
        boutonCreerTournoi.setVisible(organisateur);
        boutonCreerTournoi.setManaged(organisateur);
    }

    @FXML
    private void seDeconnecter() {
        contexte.auth().deconnecter();
        vues.afficher("login", "Connexion");
    }

    @FXML
    private void creerTournoi() {
        // Écran de création : tâche 2.10.
    }
}
