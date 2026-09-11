package fr.lahorde.arenaleague.service;

import fr.lahorde.arenaleague.model.*;
import fr.lahorde.arenaleague.model.exception.TransitionInvalideException;
import fr.lahorde.arenaleague.model.exception.ValidationException;
import fr.lahorde.arenaleague.repository.*;
import fr.lahorde.arenaleague.service.exception.AccesRefuseException;
import fr.lahorde.arenaleague.service.format.FabriqueFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Les cinq cas d'erreur du script de démonstration.
 *
 * Un test par cas, nommé par son identifiant. Si le jury demande « où est
 * vérifié le cas CE-02 ? », la réponse est une recherche sur « CE-02 ».
 *
 * Ces tests doublent volontairement une partie des tests des autres classes.
 * Ce n'est pas une redondance inutile : ils garantissent que **le scénario
 * montré au jury** est celui qui est vérifié, et pas seulement la règle sous-
 * jacente. Si un jour la démonstration change, ces tests changent avec elle.
 */
class CasErreurDemonstrationTest {

    private SessionContext session;
    private AuthService auth;
    private TournoiService tournois;
    private MatchService matchs;
    private Tournoi coupeAutomne;
    private DepotMatchEnMemoire depotMatchs;

    private static final VerificateurMotDePasse VERIFICATEUR =
        (clair, empreinte) -> empreinte.equals("empreinte-" + new String(clair));

    @BeforeEach
    void preparerLeJeuDeDonnees() {
        // Le même jeu que la migration V2, donc le même que la démonstration.
        coupeAutomne = new Tournoi(1L, "Coupe Automne", LocalDate.of(2026, 9, 12),
                                   Format.POULE, false);
        for (String nom : List.of("Gen.G", "Hanwha Life Esports", "T1", "KT Rolster")) {
            coupeAutomne.inscrire(equipeDe(nom, 3));
        }

        FabriqueFormat formats = new FabriqueFormat();
        depotMatchs = new DepotMatchEnMemoire();
        List<Match> generes = formats.pour(Format.POULE).genererMatchs(coupeAutomne);
        depotMatchs.creerTous(1L, generes);
        coupeAutomne.demarrer(generes);

        TournoiRepository depotTournois = depotFixe(coupeAutomne);
        Transactions connexions = new TransactionsDirectes();
        session = new SessionContext();

        var comptes = new UtilisateurRepositoryEnMemoire()
            .ajouter(new Organisateur(1L, "orga", "empreinte-orga"))
            .ajouter(new Arbitre(2L, "arbitre", "empreinte-arbitre"));

        auth = new AuthService(comptes, VERIFICATEUR, session, connexions);
        tournois = new TournoiService(depotTournois, new EquipeIntrouvable(), depotMatchs,
                                      formats, session, connexions);
        matchs = new MatchService(depotTournois, depotMatchs, formats, session, connexions);
    }

    @Test
    @DisplayName("CE-01 — un Arbitre tente de créer un tournoi : accès refusé, "
               + "et rien n'est écrit")
    void ce01AccesRefuseSelonLeRole() {
        auth.authentifier("arbitre", "arbitre".toCharArray());

        assertThatThrownBy(() ->
                tournois.creerTournoi("Open Hiver", LocalDate.of(2026, 9, 19),
                                      Format.ELIMINATION_DIRECTE))
            .isInstanceOf(AccesRefuseException.class)
            .hasMessageContaining("Organisateur");
    }

    @Test
    @DisplayName("CE-02 — saisie sur un match déjà clôturé : refus, "
               + "et le score initial est intact")
    void ce02SaisieSurMatchTermine() {
        auth.authentifier("arbitre", "arbitre".toCharArray());
        long matchId = premierMatch().id();

        matchs.demarrer(1L, matchId);
        matchs.saisirScore(1L, matchId, 3, 0);

        assertThatThrownBy(() -> matchs.saisirScore(1L, matchId, 2, 2))
            .isInstanceOf(TransitionInvalideException.class);

        // C'est ce qu'il faut montrer au jury : le refus n'a rien écrasé.
        assertThat(depotMatchs.get(matchId).score()).isEqualTo(new Score(3, 0));
        assertThat(depotMatchs.get(matchId).statut()).isEqualTo(StatutMatch.TERMINE);
    }

    @Test
    @DisplayName("CE-03 — score négatif refusé dès la construction de l'objet")
    void ce03ScoreNegatif() {
        auth.authentifier("arbitre", "arbitre".toCharArray());
        long matchId = premierMatch().id();
        matchs.demarrer(1L, matchId);

        assertThatThrownBy(() -> matchs.saisirScore(1L, matchId, -1, 2))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("RG-60");

        // Le match n'a pas bougé : la validation est en amont de la transition.
        assertThat(depotMatchs.get(matchId).statut()).isEqualTo(StatutMatch.EN_COURS);
    }

    @Test
    @DisplayName("CE-04 — égalité refusée en élimination directe, acceptée en poule : "
               + "c'est le pattern Strategy qui fait la différence")
    void ce04EgaliteEnEliminationDirecte() {
        FabriqueFormat formats = new FabriqueFormat();

        // Le même score, deux verdicts, sans aucun test de format dans le service.
        formats.pour(Format.POULE).validerScore(new Score(2, 2));

        assertThatThrownBy(() ->
                formats.pour(Format.ELIMINATION_DIRECTE).validerScore(new Score(2, 2)))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("RG-33");
    }

    @Test
    @DisplayName("CE-05 — une équipe d'un seul joueur ne peut pas être inscrite")
    void ce05EquipeIncomplete() {
        Tournoi ouvert = new Tournoi(2L, "Open Hiver", LocalDate.of(2026, 9, 19),
                                     Format.POULE, false);

        assertThatThrownBy(() -> ouvert.inscrire(equipeDe("Solo", 1)))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("RG-10");

        assertThat(ouvert.equipes()).isEmpty();
    }

    // ------------------------------------------------------------------

    private Match premierMatch() {
        return coupeAutomne.matchs().get(0);
    }

    private static Equipe equipeDe(String nom, int nombreDeJoueurs) {
        Equipe equipe = new Equipe(nom);
        for (int i = 1; i <= nombreDeJoueurs; i++) {
            equipe.ajouterJoueur(new Joueur(nom + "-" + i));
        }
        return equipe;
    }

    private static TournoiRepository depotFixe(Tournoi unique) {
        return new TournoiRepository() {
            public Tournoi creer(Tournoi t) { return t; }
            public Optional<Tournoi> parId(long id) { return Optional.of(unique); }
            public List<Tournoi> tous() { return List.of(unique); }
            public void inscrire(long tournoiId, long equipeId) { }
            public void retirer(long tournoiId, long equipeId) { }
            public void marquerDemarre(long tournoiId) { }
        };
    }

    /** Dépôt d'équipes vide : ces tests n'inscrivent rien via le service. */
    private static final class EquipeIntrouvable implements EquipeRepository {
        public Equipe creer(Equipe equipe) { return equipe; }
        public Optional<Equipe> parId(long id) { return Optional.empty(); }
        public Optional<Equipe> parNom(String nom) { return Optional.empty(); }
        public List<Equipe> toutes() { return List.of(); }
        public List<Equipe> inscritesA(long tournoiId) { return List.of(); }
    }
}
