package fr.lahorde.arenaleague.controller;

import fr.lahorde.arenaleague.AppContext;
import fr.lahorde.arenaleague.model.Equipe;
import fr.lahorde.arenaleague.model.Format;
import fr.lahorde.arenaleague.model.Match;
import fr.lahorde.arenaleague.model.Tournoi;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Création d'un tournoi : formulaire, inscriptions, génération du calendrier.
 *
 * **Aucun test sur le format ici.** Le libellé est porté par la constante
 * elle-même, la contrainte d'effectif est demandée au service, qui la tient
 * de la stratégie. Ajouter un troisième format ne toucherait pas cette classe.
 *
 * **Aucun test sur le rôle non plus.** L'écran s'ouvre depuis un bouton déjà
 * masqué pour un Arbitre ; si l'on force le passage, c'est le service qui
 * refuse, et son message s'affiche tel quel.
 *
 * Choix d'ergonomie assumés :
 *
 * - inscription par **cases à cocher** et non par sélection multiple. Le
 *   Ctrl+clic d'une ListView ne s'apprend nulle part : il faut le savoir. Une
 *   case cochée se voit, se défait, et survit au défilement ;
 * - un **compteur permanent** dit combien d'équipes sont retenues et ce qui
 *   manque, plutôt que de laisser découvrir le refus après coup ;
 * - une **confirmation** avant la génération, parce que RG-22 rend le
 *   démarrage irréversible et qu'aucun écran ne permet de revenir en arrière.
 */
public final class CreationTournoiController {

    @FXML private TextField champNom;
    @FXML private DatePicker champDate;
    @FXML private ComboBox<Format> champFormat;
    @FXML private Label contrainteFormat;
    @FXML private Label regleEffectif;
    @FXML private ListView<Equipe> listeEquipes;
    @FXML private Label compteurEquipes;
    @FXML private Label messageErreur;
    @FXML private Button boutonGenerer;

    @FXML private Label messageSucces;
    @FXML private Label resumeCalendrier;
    @FXML private TableView<Match> tableCalendrier;
    @FXML private TableColumn<Match, String> colonneTour;
    @FXML private TableColumn<Match, String> colonneEquipeA;
    @FXML private TableColumn<Match, String> colonneEquipeB;
    @FXML private Button boutonTerminer;

    /** Ordre d'inscription conservé : LinkedHashSet, pas HashSet. */
    private final Set<Equipe> retenues = new LinkedHashSet<>();

    private final AppContext contexte;
    private final Vues vues;

    private GestionnaireErreurs erreurs;

    public CreationTournoiController(AppContext contexte, Vues vues) {
        this.contexte = contexte;
        this.vues = vues;
    }

    @FXML
    private void initialize() {
        erreurs = new GestionnaireErreurs(messageErreur, contexte.config().profil());
        erreurs.masquer();

        champDate.setValue(LocalDate.now());

        champFormat.setConverter(new LibelleFormat());
        champFormat.getItems().setAll(Format.values());
        champFormat.valueProperty().addListener((observable, avant, apres) -> {
            annoncerContrainteDuFormat(apres);
            rafraichirCompteur();
        });
        champFormat.getSelectionModel().selectFirst();

        regleEffectif.setText(
            "Cochez les équipes à inscrire. Une équipe doit compter entre "
            + Equipe.EFFECTIF_MIN + " et " + Equipe.EFFECTIF_MAX
            + " joueurs pour être inscrite (RG-10) ; les autres restent visibles "
            + "mais ne peuvent pas être cochées.");

        listeEquipes.setCellFactory(liste -> new CelluleEquipe());
        listeEquipes.setPlaceholder(
            etiquetteVide("Aucune équipe enregistrée : il n'y a rien à inscrire."));

        colonneTour.setCellValueFactory(
            cellule -> texte("Tour " + cellule.getValue().tour()));
        colonneEquipeA.setCellValueFactory(
            cellule -> texte(cellule.getValue().equipeA().nom()));
        colonneEquipeB.setCellValueFactory(
            cellule -> texte(cellule.getValue().equipeB().nom()));
        tableCalendrier.setPlaceholder(etiquetteVide(
            "Le calendrier apparaîtra ici.\n"
            + "Renseignez le tournoi, cochez les équipes, puis lancez la génération."));

        chargerEquipes();
        rafraichirCompteur();
    }

    // ------------------------------------------------------------------
    //  Actions
    // ------------------------------------------------------------------

    @FXML
    private void creerEtGenerer() {
        erreurs.masquer();

        String nom = champNom.getText() == null ? "" : champNom.getText().trim();
        if (nom.isEmpty()) {
            erreurs.afficher("Donnez un nom au tournoi avant de continuer.");
            champNom.requestFocus();
            return;
        }
        if (champDate.getValue() == null) {
            erreurs.afficher("Choisissez une date de début.");
            champDate.requestFocus();
            return;
        }

        List<Equipe> choisies = List.copyOf(retenues);
        Format format = champFormat.getValue();

        // Ces refus évitent de créer un tournoi qu'on ne pourrait pas démarrer :
        // RG-22 interdisant de recommencer, il resterait en base sans usage.
        for (Equipe equipe : choisies) {
            if (!equipe.effectifValide()) {
                erreurs.afficher(messageEffectif(equipe));
                return;
            }
        }
        if (!contexte.tournois().nbEquipesValide(format, choisies.size())) {
            erreurs.afficher(etatSelection(format, choisies.size()));
            return;
        }

        if (!confirme(nom, format, choisies)) {
            return;
        }

        boutonGenerer.setDisable(true);

        // Création, inscriptions et démarrage ne font qu'une opération aux
        // yeux de l'utilisateur : un seul point de traduction d'erreur. Le
        // refus de droit d'un Arbitre qui aurait forcé le passage y passe
        // aussi, et son message s'affiche tel quel (CE-01).
        boolean abouti = erreurs.executer("créer le tournoi", () -> {
            Tournoi tournoi = contexte.tournois().creerTournoi(nom, champDate.getValue(), format);
            for (Equipe equipe : choisies) {
                contexte.tournois().inscrire(tournoi.id(), equipe.id());
            }
            afficherCalendrier(contexte.tournois().demarrer(tournoi.id()));
        });

        if (!abouti) {
            boutonGenerer.setDisable(false);
        }
    }

    @FXML
    private void retourAccueil() {
        vues.afficher("accueil", contexte.session().exigerConnecte().login());
    }

    /**
     * RG-22 : le démarrage ne se rejoue pas. Une action irréversible mérite
     * un récapitulatif, pas seulement un avertissement.
     */
    private boolean confirme(String nom, Format format, List<Equipe> choisies) {
        Alert question = new Alert(Alert.AlertType.CONFIRMATION);
        question.setTitle("ArenaLeague");
        question.setHeaderText("Générer le calendrier de « " + nom + " » ?");
        question.setContentText(
            "Format : " + format.libelle() + "\n"
            + "Équipes inscrites (" + choisies.size() + ") : " + nomsDe(choisies) + "\n\n"
            + "Le tournoi démarrera immédiatement. Les inscriptions seront closes "
            + "et le calendrier ne pourra plus être regénéré (RG-22).");

        ButtonType generer = new ButtonType("Générer le calendrier");
        ButtonType annuler = new ButtonType("Revenir à la saisie", ButtonType.CANCEL.getButtonData());
        question.getButtonTypes().setAll(annuler, generer);

        habiller(question);
        return question.showAndWait().filter(generer::equals).isPresent();
    }

    /**
     * Une boîte de dialogue s'ouvre dans sa propre fenêtre et n'hérite donc
     * pas de la feuille de style de la scène. Sans ce report explicite, la
     * confirmation s'afficherait avec le thème clair par défaut de JavaFX au
     * milieu d'une application sombre.
     */
    private void habiller(Alert boite) {
        Scene scene = boutonGenerer.getScene();
        if (scene != null) {
            boite.getDialogPane().getStylesheets().addAll(scene.getStylesheets());
        }
    }

    // ------------------------------------------------------------------
    //  Affichage
    // ------------------------------------------------------------------

    private void chargerEquipes() {
        erreurs.calculer("charger la liste des équipes", () -> contexte.tournois().listerEquipes())
            .ifPresent(equipes -> listeEquipes.getItems().setAll(equipes));
    }

    private void basculer(Equipe equipe, boolean retenue) {
        if (equipe == null) {
            return;
        }
        if (retenue) {
            retenues.add(equipe);
        } else {
            retenues.remove(equipe);
        }
        erreurs.masquer();
        rafraichirCompteur();
    }

    /**
     * Le compteur porte l'information utile en continu : combien d'équipes
     * sont cochées, et si ce nombre convient au format choisi. La couleur ne
     * fait que renforcer un texte qui se suffit à lui-même.
     */
    private void rafraichirCompteur() {
        Format format = champFormat.getValue();
        int nombre = retenues.size();

        compteurEquipes.setText(etatSelection(format, nombre));

        boolean valide = format != null && contexte.tournois().nbEquipesValide(format, nombre);
        compteurEquipes.getStyleClass().removeAll("compteur-ok", "compteur-ko");
        compteurEquipes.getStyleClass().add(valide ? "compteur-ok" : "compteur-ko");
    }

    private String etatSelection(Format format, int nombre) {
        String debut = nombre == 0
            ? "Aucune équipe cochée."
            : nombre + " équipe(s) cochée(s) : " + nomsDe(retenues) + ".";

        if (format == null) {
            return debut;
        }
        if (contexte.tournois().nbEquipesValide(format, nombre)) {
            return debut + " Cet effectif convient au format " + format.libelle() + ".";
        }
        return debut + " " + contexte.tournois().contrainteEffectif(format);
    }

    private void afficherCalendrier(Tournoi tournoi) {
        tableCalendrier.getItems().setAll(tournoi.matchs());

        messageSucces.setText("Tournoi « " + tournoi.nom() + " » créé et démarré.");
        montrer(messageSucces);

        resumeCalendrier.setText(
            tournoi.format().libelle() + " · " + tournoi.equipes().size() + " équipes · "
            + tournoi.matchs().size() + " match(s) répartis sur "
            + tournoi.dernierTour() + " tour(s).");
        montrer(resumeCalendrier);

        // Le formulaire devient une trace de ce qui a été saisi : on ne le
        // vide pas, mais on rend visible qu'il n'est plus modifiable.
        champNom.setDisable(true);
        champDate.setDisable(true);
        champFormat.setDisable(true);
        listeEquipes.setDisable(true);
        boutonGenerer.setDisable(true);
        boutonGenerer.setText("Calendrier généré");

        montrer(boutonTerminer);
        boutonTerminer.requestFocus();
    }

    private void annoncerContrainteDuFormat(Format format) {
        contrainteFormat.setText(
            format == null ? "" : contexte.tournois().contrainteEffectif(format));
    }

    private static void montrer(javafx.scene.Node noeud) {
        noeud.setVisible(true);
        noeud.setManaged(true);
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

    private static String nomsDe(Iterable<Equipe> equipes) {
        StringBuilder noms = new StringBuilder();
        for (Equipe equipe : equipes) {
            if (noms.length() > 0) {
                noms.append(", ");
            }
            noms.append(equipe.nom());
        }
        return noms.toString();
    }

    private static String messageEffectif(Equipe equipe) {
        return "L'équipe " + equipe.nom() + " compte " + equipe.effectif()
             + " joueur(s) ; il en faut entre " + Equipe.EFFECTIF_MIN
             + " et " + Equipe.EFFECTIF_MAX + " (RG-10).";
    }

    private static final class LibelleFormat extends StringConverter<Format> {
        @Override
        public String toString(Format format) {
            return format == null ? "" : format.libelle();
        }

        @Override
        public Format fromString(String texte) {
            throw new UnsupportedOperationException("Liste non éditable");
        }
    }

    /**
     * Ligne d'équipe : une case à cocher et son effectif.
     *
     * Les cellules d'une ListView sont recyclées au défilement — d'où la
     * remise à zéro systématique du style et de l'infobulle avant de
     * reconfigurer la ligne pour l'équipe courante.
     */
    private final class CelluleEquipe extends ListCell<Equipe> {

        private final CheckBox coche = new CheckBox();
        private final Label effectif = new Label();
        private final HBox ligne = new HBox(10);

        CelluleEquipe() {
            effectif.getStyleClass().add("cellule-effectif");
            Region espace = new Region();
            HBox.setHgrow(espace, Priority.ALWAYS);
            ligne.setAlignment(Pos.CENTER_LEFT);
            ligne.getChildren().addAll(coche, espace, effectif);
            coche.setOnAction(evenement -> basculer(getItem(), coche.isSelected()));
        }

        @Override
        protected void updateItem(Equipe equipe, boolean vide) {
            super.updateItem(equipe, vide);
            getStyleClass().removeAll("equipe-inscriptible", "equipe-refusee");

            if (vide || equipe == null) {
                setGraphic(null);
                setTooltip(null);
                return;
            }

            boolean inscriptible = equipe.effectifValide();

            coche.setText(equipe.nom());
            coche.setSelected(retenues.contains(equipe));
            coche.setDisable(!inscriptible);
            effectif.setText(equipe.effectif() + " joueur(s)");

            getStyleClass().add(inscriptible ? "equipe-inscriptible" : "equipe-refusee");
            setTooltip(inscriptible ? null : new Tooltip(messageEffectif(equipe)));
            setGraphic(ligne);
        }
    }
}
