package fr.lahorde.arenaleague.controller;

import fr.lahorde.arenaleague.AppContext;
import fr.lahorde.arenaleague.model.Tournoi;
import fr.lahorde.arenaleague.model.Utilisateur;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Écran principal après connexion : la liste des tournois, et l'accès à la
 * création pour qui en a le droit.
 *
 * Le bouton « Créer un tournoi » n'est visible que pour un Organisateur.
 * **Ce masquage est du confort, pas une sécurité.** Le contrôle réel est dans
 * TournoiService, qui refuse l'appel quelle que soit l'interface — c'est ce
 * que démontre le cas CE-01, en appelant le service directement.
 *
 * La visibilité est déduite de peutCreerTournoi(), donc du polymorphisme.
 * Aucun test sur un nom de rôle ici non plus.
 *
 * Un point d'ergonomie : quand l'action n'est pas proposée, l'écran le **dit**
 * au lieu de la faire disparaître en silence. Une fonctionnalité absente sans
 * explication se lit comme une panne, et coûte une question en démonstration.
 */
public final class AccueilController {

    private static final DateTimeFormatter JOUR =
        DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRENCH);

    @FXML private Label nomUtilisateur;
    @FXML private Label badgeRole;
    @FXML private Label compteurTournois;
    @FXML private Label noteRoleLecture;
    @FXML private Label messageErreur;
    @FXML private Button boutonCreerTournoi;
    @FXML private Button boutonOuvrir;

    @FXML private TableView<Tournoi> tableTournois;
    @FXML private TableColumn<Tournoi, String> colonneNom;
    @FXML private TableColumn<Tournoi, String> colonneDate;
    @FXML private TableColumn<Tournoi, String> colonneFormat;
    @FXML private TableColumn<Tournoi, String> colonneEtat;

    private final AppContext contexte;
    private final Vues vues;

    private GestionnaireErreurs erreurs;

    public AccueilController(AppContext contexte, Vues vues) {
        this.contexte = contexte;
        this.vues = vues;
    }

    @FXML
    private void initialize() {
        erreurs = new GestionnaireErreurs(messageErreur, contexte.config().profil());
        erreurs.masquer();

        Utilisateur connecte = contexte.session().exigerConnecte();
        nomUtilisateur.setText(connecte.login());
        badgeRole.setText(connecte.role().name());

        boolean organisateur = connecte.peutCreerTournoi();
        boutonCreerTournoi.setVisible(organisateur);
        boutonCreerTournoi.setManaged(organisateur);

        if (!organisateur) {
            noteRoleLecture.setText(
                "Vous consultez les tournois en lecture. La création d'un tournoi "
                + "est réservée à l'Organisateur (RG-01).");
            noteRoleLecture.setVisible(true);
            noteRoleLecture.setManaged(true);
        }

        // Le nom est l'identifiant que l'œil cherche en premier : il porte le
        // poids typographique, les autres colonnes restent en texte courant.
        colonneNom.setCellValueFactory(cellule -> texte(cellule.getValue().nom()));
        colonneNom.setCellFactory(colonne -> new CelluleTexte("cellule-principale"));

        colonneDate.setCellValueFactory(
            cellule -> texte(cellule.getValue().dateDebut().format(JOUR)));
        colonneDate.setCellFactory(colonne -> new CelluleTexte("cellule-secondaire"));

        // Libellé porté par la constante elle-même : aucun test sur le format.
        colonneFormat.setCellValueFactory(
            cellule -> texte(cellule.getValue().format().libelle()));

        colonneEtat.setCellValueFactory(
            cellule -> texte(cellule.getValue().estDemarre() ? "En cours" : "Inscriptions ouvertes"));
        colonneEtat.setCellFactory(colonne -> new CelluleEtat());

        // Ouvrir n'a de sens qu'avec un tournoi choisi : le bouton suit la
        // sélection plutôt que de refuser après coup.
        tableTournois.getSelectionModel().selectedItemProperty().addListener(
            (observable, avant, apres) -> boutonOuvrir.setDisable(apres == null));

        // Le double-clic fait la même chose que le bouton : c'est le geste
        // qu'on tente d'instinct sur une ligne de tableau.
        tableTournois.setRowFactory(table -> {
            TableRow<Tournoi> ligne = new TableRow<>();
            ligne.setOnMouseClicked(evenement -> {
                if (evenement.getClickCount() == 2 && !ligne.isEmpty()) {
                    ouvrir(ligne.getItem());
                }
            });
            return ligne;
        });

        tableTournois.setPlaceholder(etiquetteVide(organisateur
            ? "Aucun tournoi pour l'instant.\nUtilisez « Créer un tournoi » pour en ajouter un."
            : "Aucun tournoi pour l'instant."));

        installerRaccourciCreation();
        chargerTournois();
    }

    /**
     * Ctrl+N ouvre la création de tournoi **quel que soit le rôle**, alors
     * que le bouton reste masqué pour un Arbitre.
     *
     * Ce n'est pas une faille laissée par distraction : c'est le chemin du
     * cas de démonstration CE-01, qui demande d'atteindre l'écran « via le
     * raccourci clavier, l'entrée de menu étant masquée ». Il matérialise
     * l'argument central du projet — masquer un bouton est du confort, le
     * refus qui compte est celui du service, et il tombe même quand l'IHM
     * est contournée.
     *
     * L'accélérateur se pose sur la scène, qui n'existe pas encore quand
     * initialize() s'exécute : d'où l'attente de son affectation.
     */
    private void installerRaccourciCreation() {
        tableTournois.sceneProperty().addListener((observable, avant, scene) -> {
            if (scene != null) {
                scene.getAccelerators().put(
                    new KeyCodeCombination(KeyCode.N, KeyCombination.CONTROL_DOWN),
                    this::creerTournoi);
            }
        });
    }

    @FXML
    private void seDeconnecter() {
        contexte.auth().deconnecter();
        vues.afficher("login", "Connexion");
    }

    @FXML
    private void ouvrirTournoi() {
        ouvrir(tableTournois.getSelectionModel().getSelectedItem());
    }

    /** Le tournoi choisi est transmis à l'écran de saisie par la navigation. */
    private void ouvrir(Tournoi tournoi) {
        if (tournoi != null) {
            vues.afficher("saisie-resultats", tournoi.nom(), tournoi.id());
        }
    }

    @FXML
    private void creerTournoi() {
        vues.afficher("creation-tournoi", "Nouveau tournoi");
    }

    private void chargerTournois() {
        erreurs.calculer("charger la liste des tournois", () -> contexte.tournois().lister())
            .ifPresent(tournois -> {
                tableTournois.getItems().setAll(tournois);
                compteurTournois.setText(tournois.isEmpty()
                    ? "" : tournois.size() + (tournois.size() > 1 ? " tournois" : " tournoi"));
            });
    }

    private static Label etiquetteVide(String texte) {
        Label etiquette = new Label(texte);
        etiquette.getStyleClass().add("zone-vide");
        etiquette.setWrapText(true);
        return etiquette;
    }

    private static ReadOnlyStringWrapper texte(String valeur) {
        return new ReadOnlyStringWrapper(valeur);
    }

    /** Cellule de texte portant une classe de style, pour la hiérarchie. */
    private static final class CelluleTexte extends TableCell<Tournoi, String> {

        private CelluleTexte(String classe) {
            getStyleClass().add(classe);
        }

        @Override
        protected void updateItem(String valeur, boolean vide) {
            super.updateItem(valeur, vide);
            setText(vide ? null : valeur);
        }
    }

    /**
     * L'état s'affiche en pastille plutôt qu'en texte brut : dans une colonne
     * où toutes les valeurs se ressemblent, une forme colorée se repère plus
     * vite qu'un mot. Le libellé reste écrit en toutes lettres — la couleur
     * ne porte jamais l'information seule.
     */
    private static final class CelluleEtat extends TableCell<Tournoi, String> {

        @Override
        protected void updateItem(String valeur, boolean vide) {
            super.updateItem(valeur, vide);

            if (vide || valeur == null) {
                setGraphic(null);
                return;
            }

            Tournoi tournoi = getTableRow() == null ? null : getTableRow().getItem();
            boolean demarre = tournoi != null && tournoi.estDemarre();

            Label pastille = new Label(valeur);
            pastille.getStyleClass().addAll(
                "badge-etat", demarre ? "badge-en-cours" : "badge-ouvert");
            setGraphic(pastille);
        }
    }
}
