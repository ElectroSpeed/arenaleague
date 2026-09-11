package fr.lahorde.arenaleague.controller;

import fr.lahorde.arenaleague.AppContext;
import fr.lahorde.arenaleague.model.LigneClassement;
import fr.lahorde.arenaleague.model.Match;
import fr.lahorde.arenaleague.model.Tournoi;
import fr.lahorde.arenaleague.model.Utilisateur;
import fr.lahorde.arenaleague.service.EcouteurClassement;
import javafx.application.Platform;
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
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

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
public final class SaisieResultatsController implements Liberable {

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

    @FXML private Label noteClassement;
    @FXML private TableView<LigneClassement> tableClassement;
    @FXML private TableColumn<LigneClassement, String> colonneRang;
    @FXML private TableColumn<LigneClassement, String> colonneEquipe;
    @FXML private TableColumn<LigneClassement, String> colonnePoints;
    @FXML private TableColumn<LigneClassement, String> colonneJoues;
    @FXML private TableColumn<LigneClassement, String> colonneVictoires;
    @FXML private TableColumn<LigneClassement, String> colonneNuls;
    @FXML private TableColumn<LigneClassement, String> colonneDefaites;
    @FXML private TableColumn<LigneClassement, String> colonneMarques;
    @FXML private TableColumn<LigneClassement, String> colonneEncaisses;
    @FXML private TableColumn<LigneClassement, String> colonneDifference;

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
    @FXML private VBox zoneForfait;
    @FXML private Button boutonForfaitA;
    @FXML private Button boutonForfaitB;
    @FXML private Label regleForfait;

    private final AppContext contexte;
    private final Vues vues;

    private Long tournoiId;

    private GestionnaireErreurs erreurs;

    /**
     * Liste observable alimentant la table du classement. La TableView y est
     * liée une fois pour toutes : rafraîchir revient à en remplacer le
     * contenu, sans jamais retoucher la vue.
     */
    private final ObservableList<LigneClassement> classement = FXCollections.observableArrayList();

    /**
     * Abonnement conservé pour pouvoir s'en retirer. C'est ce même objet que
     * liberer() transmet à desabonner() — une lambda recréée à la volée ne
     * serait pas égale à celle qui a été enregistrée.
     */
    private final EcouteurClassement ecouteur = this::classementModifie;

    public SaisieResultatsController(AppContext contexte, Vues vues) {
        this.contexte = contexte;
        this.vues = vues;
    }

    @FXML
    private void initialize() {
        erreurs = new GestionnaireErreurs(messageErreur, contexte.config().profil());
        masquerMessages();

        Utilisateur connecte = contexte.session().exigerConnecte();
        nomUtilisateur.setText(connecte.login());
        badgeRole.setText(connecte.role().name());

        // RG-60 : un score est un entier positif. Le filtre refuse tout le
        // reste à la frappe — le signe moins et les lettres n'entrent même
        // pas dans le champ, donc rien d'invalide n'atteint le service.
        champScoreA.setTextFormatter(filtreEntierPositif());
        champScoreB.setTextFormatter(filtreEntierPositif());
        regleScore.setText("Entiers positifs uniquement.");

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

        tableClassement.setItems(classement);
        colonneRang.setCellValueFactory(c -> texte(String.valueOf(c.getValue().rang())));
        colonneEquipe.setCellValueFactory(c -> texte(c.getValue().equipe().nom()));
        colonneEquipe.setCellFactory(colonne -> new CelluleClassement("cellule-principale"));
        colonnePoints.setCellValueFactory(c -> texte(String.valueOf(c.getValue().points())));
        colonnePoints.setCellFactory(colonne -> new CelluleClassement("cellule-score"));
        colonneJoues.setCellValueFactory(c -> texte(String.valueOf(c.getValue().joues())));
        colonneVictoires.setCellValueFactory(c -> texte(String.valueOf(c.getValue().victoires())));
        colonneNuls.setCellValueFactory(c -> texte(String.valueOf(c.getValue().nuls())));
        colonneDefaites.setCellValueFactory(c -> texte(String.valueOf(c.getValue().defaites())));
        colonneMarques.setCellValueFactory(c -> texte(String.valueOf(c.getValue().marques())));
        colonneEncaisses.setCellValueFactory(c -> texte(String.valueOf(c.getValue().encaisses())));
        colonneDifference.setCellValueFactory(c -> texte(differenceSignee(c.getValue())));

        // RG-46 : une équipe qualifiée ressort en gras.
        tableClassement.setRowFactory(table -> new LigneClassementQualifiee());
        tableClassement.setPlaceholder(etiquetteVide("Aucun match terminé : le classement est vide."));

        noteClassement.setText("Recalculé à chaque score enregistré.");

        // Pattern Observer : le service prévient, l'écran se recalcule.
        contexte.matchs().abonner(ecouteur);

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
        if (erreurs.executer("démarrer le match",
                () -> contexte.matchs().demarrer(tournoiId, match.id()))) {
            recharger(match.id(), "Match démarré.");
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

        // Le message métier est affiché tel quel par le gestionnaire : c'est
        // lui que la démonstration montre pour CE-02 (match clôturé) et CE-04
        // (nul refusé en élimination directe).
        if (erreurs.executer("enregistrer le score",
                () -> contexte.matchs().saisirScore(tournoiId, match.id(), scoreA, scoreB))) {
            recharger(match.id(), "Score enregistré. Le classement a été recalculé.");
        }
    }

    /**
     * RG-47 à RG-49 : déclaration de forfait.
     *
     * L'écran ne connaît **ni le score ni le format** : il désigne l'équipe
     * absente, et c'est la stratégie qui décide — 0 – 3 en poule, 0 – 1 en
     * élimination directe. Ajouter un troisième format ne toucherait pas
     * cette classe, une fois de plus.
     */
    private void declarerForfait(boolean forfaitEquipeA) {
        Match match = selection();
        if (match == null) {
            return;
        }
        masquerMessages();

        String absente = (forfaitEquipeA ? match.equipeA() : match.equipeB()).nom();
        if (erreurs.executer("déclarer le forfait",
                () -> contexte.matchs().declarerForfait(tournoiId, match.id(), forfaitEquipeA))) {
            recharger(match.id(), "Forfait de " + absente
                + " enregistré. Le score a été imposé par le format du tournoi.");
        }
    }

    @FXML
    private void forfaitEquipeA() {
        declarerForfait(true);
    }

    @FXML
    private void forfaitEquipeB() {
        declarerForfait(false);
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
            erreurs.afficher("Aucun tournoi sélectionné. Revenez à l'accueil "
                + "et ouvrez un tournoi depuis la liste.");
            return;
        }
        erreurs.calculer("charger le tournoi", () -> contexte.tournois().parId(tournoiId))
            .ifPresent(tournoi -> {
                nomTournoi.setText(tournoi.nom());
                formatTournoi.setText(tournoi.format().libelle());

                List<Match> matchs = tournoi.matchs();
                tableMatchs.getItems().setAll(matchs);

                long termines = matchs.stream().filter(Match::estTermine).count();
                resumeMatchs.setText(matchs.size() + " match(s) · " + termines
                    + " terminé(s) · " + (matchs.size() - termines) + " à jouer");

                rafraichirClassement();
            });
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

        // RG-47 : proposé tant que le match n'est pas clos, qu'il ait été
        // démarré ou non. On demande à l'état, on ne teste pas le statut.
        boolean forfaitPossible = match.etat().autoriseForfait();
        montrer(zoneForfait, forfaitPossible);
        if (forfaitPossible) {
            boutonForfaitA.setText(match.equipeA().nom() + " déclare forfait");
            boutonForfaitB.setText(match.equipeB().nom() + " déclare forfait");
            regleForfait.setText("Le score est imposé par le format.");
        }

        if (termine) {
            avertissementCloture.setText("Match clôturé : le score ne peut plus être modifié.");
            boutonValider.setText("Tenter la saisie");
        } else {
            boutonValider.setText("Valider le score");
        }

        champScoreA.setText(match.score() == null ? "" : String.valueOf(match.score().equipeA()));
        champScoreB.setText(match.score() == null ? "" : String.valueOf(match.score().equipeB()));
    }

    /**
     * Recalcul délégué : le tri, y compris le Collator de RG-75, est fait en
     * Java par la stratégie du format. L'écran n'ordonne rien lui-même — une
     * TableView triable par l'utilisateur donnerait un ordre qui ne serait
     * plus celui des règles de gestion.
     */
    private void rafraichirClassement() {
        erreurs.calculer("recalculer le classement", () -> contexte.tournois().classement(tournoiId))
            .ifPresent(classement::setAll);
    }

    /**
     * Pattern Observer, étape 7 de RG-63 : MatchService prévient ses abonnés
     * qu'un score a changé, l'écran se recalcule. Aucun bouton de
     * rafraîchissement, aucune scrutation.
     *
     * Le passage par Platform.runLater n'est pas de la prudence gratuite : le
     * service ignore qu'il parle à une interface graphique, et rien ne
     * garantit qu'il notifiera toujours depuis le fil JavaFX. C'est à
     * l'abonné de revenir sur le bon fil.
     */
    private void classementModifie(long tournoiIdModifie) {
        if (tournoiId != null && tournoiId == tournoiIdModifie) {
            Platform.runLater(this::rafraichirClassement);
        }
    }

    /**
     * L'écran rend son abonnement quand il quitte la scène — appelé par Vues.
     * Sans cela, chaque passage laisserait un écouteur de plus rafraîchir une
     * vue qui n'est plus affichée.
     */
    @Override
    public void liberer() {
        contexte.matchs().desabonner(ecouteur);
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
        erreurs.masquer();
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

    /** Le signe explicite se lit plus vite qu'un nombre nu dans une colonne. */
    private static String differenceSignee(LigneClassement ligne) {
        int difference = ligne.difference();
        return difference > 0 ? "+" + difference : String.valueOf(difference);
    }

    private static final class CelluleClassement extends TableCell<LigneClassement, String> {

        private CelluleClassement(String classe) {
            getStyleClass().add(classe);
        }

        @Override
        protected void updateItem(String valeur, boolean vide) {
            super.updateItem(valeur, vide);
            setText(vide ? null : valeur);
        }
    }

    /** RG-46 : l'équipe qualifiée ressort, sans que la couleur seule le dise. */
    private static final class LigneClassementQualifiee extends TableRow<LigneClassement> {

        @Override
        protected void updateItem(LigneClassement ligne, boolean vide) {
            super.updateItem(ligne, vide);
            getStyleClass().remove("ligne-qualifiee");
            if (!vide && ligne != null && ligne.qualifiee()) {
                getStyleClass().add("ligne-qualifiee");
            }
        }
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
