package fr.lahorde.arenaleague.controller;

import fr.lahorde.arenaleague.AppContext;
import fr.lahorde.arenaleague.model.Match;
import fr.lahorde.arenaleague.model.Tournoi;
import fr.lahorde.arenaleague.model.Utilisateur;
import fr.lahorde.arenaleague.model.exception.ArenaLeagueException;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Locale;

/**
 * Saisie des résultats — écran principal de l'Arbitre.
 *
 * **Aucun test d'état ici.** Le contrôleur adapte ce qu'il *propose* au
 * statut du match, mais il ne décide jamais d'un refus : il appelle
 * `demarrer()` ou `saisirScore()`, et la machine à états accepte ou refuse.
 * C'est ce qui permet de démontrer CE-02 sans code de démonstration : le
 * refus vient de `Termine`, pas d'un `if` écrit dans l'IHM.
 *
 * **Aucun test de rôle non plus.** RG-02 autorise les deux rôles à saisir un
 * score ; l'écran ne propose aucune action d'Organisateur, et le service
 * contrôle de toute façon chaque appel.
 *
 * Deux points d'ergonomie qui portent des exigences de la Definition of Done :
 *
 * - un score négatif ou non numérique **n'atteint jamais le service**. Les
 *   champs n'acceptent que des chiffres, filtrés à la frappe par un
 *   TextFormatter — c'est le cas CE-03, dont le document précise que « la
 *   saisie n'est pas transmise au service » ;
 * - un match clôturé reste **tentable**. C'est contre-intuitif, mais CE-02
 *   est imposé par le sujet et demande de « rouvrir le match terminé et
 *   tenter de saisir 2 – 2 ». La ligne est grisée, un avertissement annonce
 *   le refus, et le service le prononce réellement.
 */
public final class SaisieResultatsController {

    @FXML private Label nomTournoi;
    @FXML private Label formatTournoi;
    @FXML private Label nomUtilisateur;
    @FXML private Label badgeRole;
    @FXML private Label resumeMatchs;

    @FXML private TableView<Match> tableMatchs;
    @FXML private TableColumn<Match, String> colonneTour;
    @FXML private TableColumn<Match, String> colonneEquipeA;
    @FXML private TableColumn<Match, String> colonneScore;
    @FXML private TableColumn<Match, String> colonneEquipeB;
    @FXML private TableColumn<Match, String> colonneEtat;

    @FXML private Label zoneAucuneSelection;
    @FXML private VBox detailMatch;
    @FXML private Label affiche;
    @FXML private Label etatMatch;
    @FXML private Label avertissementCloture;
    @FXML private VBox zoneScore;
    @FXML private Label etiquetteEquipeA;
    @FXML private Label etiquetteEquipeB;
    @FXML private TextField champScoreA;
    @FXML private TextField champScoreB;
    @FXML private Label regleScore;
    @FXML private Label messageErreur;
    @FXML private Label messageSucces;
    @FXML private Button boutonDemarrer;
    @FXML private Button boutonValider;

    private final AppContext contexte;
    private final Vues vues;

    private Long tournoiId;

    public SaisieResultatsController(AppContext contexte, Vues vues) {
        this.contexte = contexte;
        this.vues = vues;
    }

    @FXML
    private void initialize() {
        masquerMessages();

        Utilisateur connecte = contexte.session().exigerConnecte();
        nomUtilisateur.setText(connecte.login());
        badgeRole.setText(connecte.role().name());

        // RG-60 : un score est un entier positif. Le filtre refuse tout le
        // reste à la frappe — le signe moins et les lettres n'entrent même
        // pas dans le champ, donc rien d'invalide n'atteint le service.
        champScoreA.setTextFormatter(filtreEntierPositif());
        champScoreB.setTextFormatter(filtreEntierPositif());
        regleScore.setText("Entiers positifs uniquement (RG-60). "
            + "Les autres caractères sont refusés à la saisie.");

        colonneTour.setCellValueFactory(c -> texte("Tour " + c.getValue().tour()));
        colonneEquipeA.setCellValueFactory(c -> texte(c.getValue().equipeA().nom()));
        colonneEquipeB.setCellValueFactory(c -> texte(c.getValue().equipeB().nom()));
        colonneScore.setCellValueFactory(c -> texte(scoreAffiche(c.getValue())));
        colonneScore.setCellFactory(colonne -> new CelluleTexte("cellule-score"));
        colonneEtat.setCellValueFactory(c -> texte(c.getValue().etat().libelle()));
        colonneEtat.setCellFactory(colonne -> new CelluleStatut());

        // Un match terminé se distingue de toute la ligne, pas seulement de
        // sa pastille : c'est ce que demande la Definition of Done.
        tableMatchs.setRowFactory(table -> new LigneMatch());

        tableMatchs.getSelectionModel().selectedItemProperty().addListener(
            (observable, avant, apres) -> afficherDetail(apres));

        tableMatchs.setPlaceholder(etiquetteVide("Ce tournoi n'a aucun match."));

        tournoiId = vues.parametre(Long.class);
        charger();
    }

    // ------------------------------------------------------------------
    //  Actions
    // ------------------------------------------------------------------

    @FXML
    private void demarrerMatch() {
        Match match = selection();
        if (match == null) {
            return;
        }
        masquerMessages();
        try {
            contexte.matchs().demarrer(tournoiId, match.id());
            recharger(match.id(), "Match démarré.");
        } catch (ArenaLeagueException e) {
            afficher(messageErreur, e.getMessage());
        } catch (RuntimeException e) {
            afficher(messageErreur, "Le démarrage a échoué pour une raison technique.");
            System.err.println("Erreur technique au démarrage du match : " + e);
        }
    }

    @FXML
    private void validerScore() {
        Match match = selection();
        if (match == null) {
            return;
        }
        masquerMessages();

        // CE-03 : le refus est ici, avant tout appel. Le filtre de saisie
        // écarte déjà les caractères invalides ; il reste le champ vide.
        Integer scoreA = lireScore(champScoreA);
        Integer scoreB = lireScore(champScoreB);
        if (scoreA == null || scoreB == null) {
            afficher(messageErreur, "Renseignez les deux scores avant de valider.");
            (scoreA == null ? champScoreA : champScoreB).requestFocus();
            return;
        }

        try {
            contexte.matchs().saisirScore(tournoiId, match.id(), scoreA, scoreB);
            recharger(match.id(), "Score enregistré. Le classement a été recalculé.");

        } catch (ArenaLeagueException e) {
            // Message métier tel quel : c'est lui que la démonstration montre
            // pour CE-02 (match clôturé) et CE-04 (nul en élimination directe).
            afficher(messageErreur, e.getMessage());

        } catch (RuntimeException e) {
            afficher(messageErreur, "La saisie a échoué pour une raison technique. "
                + "Le score n'a pas été enregistré.");
            System.err.println("Erreur technique à la saisie du score : " + e);
        }
    }

    @FXML
    private void retourAccueil() {
        vues.afficher("accueil", contexte.session().exigerConnecte().login());
    }

    // ------------------------------------------------------------------
    //  Chargement et affichage
    // ------------------------------------------------------------------

    private void charger() {
        if (tournoiId == null) {
            afficher(messageErreur, "Aucun tournoi sélectionné. Revenez à l'accueil "
                + "et ouvrez un tournoi depuis la liste.");
            return;
        }
        try {
            Tournoi tournoi = contexte.tournois().parId(tournoiId);
            nomTournoi.setText(tournoi.nom());
            formatTournoi.setText(tournoi.format().libelle());

            List<Match> matchs = tournoi.matchs();
            tableMatchs.getItems().setAll(matchs);

            long termines = matchs.stream().filter(Match::estTermine).count();
            resumeMatchs.setText(matchs.size() + " match(s) · " + termines
                + " terminé(s) · " + (matchs.size() - termines) + " à jouer");

        } catch (ArenaLeagueException e) {
            afficher(messageErreur, e.getMessage());
        } catch (RuntimeException e) {
            afficher(messageErreur, "Le tournoi n'a pas pu être chargé.");
            System.err.println("Erreur technique au chargement du tournoi : " + e);
        }
    }

    /** Recharge depuis la base : l'écran montre l'état réellement persisté. */
    private void recharger(long matchId, String succes) {
        charger();
        for (Match match : tableMatchs.getItems()) {
            if (match.id() != null && match.id() == matchId) {
                tableMatchs.getSelectionModel().select(match);
                break;
            }
        }
        afficher(messageSucces, succes);
    }

    private void afficherDetail(Match match) {
        masquerMessages();

        boolean choisi = match != null;
        montrer(detailMatch, choisi);
        montrer(zoneAucuneSelection, !choisi);
        if (!choisi) {
            return;
        }

        affiche.setText(match.equipeA().nom() + "  contre  " + match.equipeB().nom());
        etatMatch.setText("Tour " + match.tour() + " · " + match.etat().libelle());
        etiquetteEquipeA.setText(match.equipeA().nom().toUpperCase());
        etiquetteEquipeB.setText(match.equipeB().nom().toUpperCase());

        // On demande à l'état ce qu'il autorise, on ne teste pas ce qu'il est.
        boolean demarrable = match.etat().autoriseDemarrage();
        boolean termine    = match.etat().estTerminal();

        montrer(boutonDemarrer, demarrable);
        montrer(boutonValider, !demarrable);
        montrer(zoneScore, !demarrable);
        montrer(avertissementCloture, termine);

        if (termine) {
            avertissementCloture.setText(
                "Match clôturé : TERMINÉ est un état terminal, aucune correction "
                + "n'est possible (RG-53). La saisie reste tentable et le refus "
                + "sera prononcé par le service.");
            boutonValider.setText("Tenter la saisie");
        } else {
            boutonValider.setText("Valider le score");
        }

        champScoreA.setText(match.score() == null ? "" : String.valueOf(match.score().equipeA()));
        champScoreB.setText(match.score() == null ? "" : String.valueOf(match.score().equipeB()));
    }

    // ------------------------------------------------------------------
    //  Outils
    // ------------------------------------------------------------------

    private Match selection() {
        return tableMatchs.getSelectionModel().getSelectedItem();
    }

    private static Integer lireScore(TextField champ) {
        String saisi = champ.getText() == null ? "" : champ.getText().trim();
        return saisi.isEmpty() ? null : Integer.valueOf(saisi);
    }

    /** N'accepte que des chiffres, et au plus trois : ni signe, ni lettre. */
    private static TextFormatter<String> filtreEntierPositif() {
        return new TextFormatter<>(modification ->
            modification.getControlNewText().matches("\\d{0,3}") ? modification : null);
    }

    private static String scoreAffiche(Match match) {
        return match.score() == null
            ? "—"
            : match.score().equipeA() + " – " + match.score().equipeB();
    }

    private static void montrer(Node noeud, boolean visible) {
        noeud.setVisible(visible);
        noeud.setManaged(visible);
    }

    private void afficher(Label encart, String message) {
        encart.setText(message);
        montrer(encart, true);
    }

    private void masquerMessages() {
        montrer(messageErreur, false);
        montrer(messageSucces, false);
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

    private static final class CelluleTexte extends TableCell<Match, String> {

        private CelluleTexte(String classe) {
            getStyleClass().add(classe);
        }

        @Override
        protected void updateItem(String valeur, boolean vide) {
            super.updateItem(valeur, vide);
            setText(vide ? null : valeur);
        }
    }

    /** L'état en pastille, le libellé restant écrit en toutes lettres. */
    private static final class CelluleStatut extends TableCell<Match, String> {

        @Override
        protected void updateItem(String valeur, boolean vide) {
            super.updateItem(valeur, vide);

            if (vide || valeur == null) {
                setGraphic(null);
                return;
            }
            Match match = getTableRow() == null ? null : getTableRow().getItem();

            Label pastille = new Label(valeur);
            pastille.getStyleClass().add("badge-etat");
            if (match != null) {
                // badge-planifie / badge-en-cours / badge-termine : la classe
                // suit le nom du statut, aucun cas à énumérer ici.
                pastille.getStyleClass().add("badge-"
                    + match.statut().name().toLowerCase(Locale.ROOT).replace('_', '-'));
            }
            setGraphic(pastille);
        }
    }

    /** Ligne entière grisée pour un match clôturé — exigence de la DoD. */
    private static final class LigneMatch extends TableRow<Match> {

        @Override
        protected void updateItem(Match match, boolean vide) {
            super.updateItem(match, vide);
            getStyleClass().remove("ligne-match-termine");
            if (!vide && match != null && match.estTermine()) {
                getStyleClass().add("ligne-match-termine");
            }
        }
    }
}
