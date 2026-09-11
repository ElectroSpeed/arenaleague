package fr.lahorde.arenaleague.service;

import fr.lahorde.arenaleague.model.*;
import fr.lahorde.arenaleague.model.exception.TransitionInvalideException;
import fr.lahorde.arenaleague.repository.*;
import fr.lahorde.arenaleague.service.format.FabriqueFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Le forfait — RG-47 à RG-49.
 *
 * Trois règles, trois comportements distincts : une transition qui
 * court-circuite le cycle de vie, un score qui dépend du format, et une
 * qualification qui doit se propager comme après un match joué.
 *
 * Ces tests vérifient surtout que le forfait **n'est pas un cas particulier** :
 * il produit un score ordinaire, que le classement et la génération du tour
 * suivant traitent sans savoir qu'il y a eu forfait. C'est ce qui évite un
 * drapeau `estForfait` à propager dans toute l'application.
 */
class ForfaitTest {

    private static final VerificateurMotDePasse VERIFICATEUR =
        (clair, empreinte) -> empreinte.equals("empreinte-" + new String(clair));

    private SessionContext session;
    private FabriqueFormat formats;
    private Transactions connexions;

    @BeforeEach
    void ouvrirUneSession() {
        formats = new FabriqueFormat();
        connexions = new TransactionsDirectes();
        session = new SessionContext();

        var comptes = new UtilisateurRepositoryEnMemoire()
            .ajouter(new Arbitre(2L, "arbitre", "empreinte-arbitre"));
        new AuthService(comptes, VERIFICATEUR, session, connexions)
            .authentifier("arbitre", "arbitre".toCharArray());
    }

    // ------------------------------------------------------------------

    @Nested
    @DisplayName("RG-48 — en poule, le forfait vaut 0 – 3")
    class EnPoule {

        private Tournoi poule;
        private MatchService matchs;
        private TournoiService tournois;

        @BeforeEach
        void preparer() {
            var depotMatchs = new DepotMatchEnMemoire();
            poule = demarrer(Format.POULE, depotMatchs);
            TournoiRepository depot = depotFixe(poule);
            matchs = new MatchService(depot, depotMatchs, formats, session, connexions);
            tournois = new TournoiService(depot, new EquipeIntrouvable(), depotMatchs,
                                          formats, session, connexions);
        }

        @Test
        @DisplayName("L'équipe A déclarée forfait perd 0 – 3")
        void forfaitEquipeA() {
            Match match = poule.matchs().get(0);

            Match clos = matchs.declarerForfait(1L, match.id(), true);

            assertThat(clos.score()).isEqualTo(new Score(0, 3));
            assertThat(clos.statut()).isEqualTo(StatutMatch.TERMINE);
        }

        @Test
        @DisplayName("L'équipe B déclarée forfait perd 3 – 0 : le score suit le fautif")
        void forfaitEquipeB() {
            Match match = poule.matchs().get(0);

            assertThat(matchs.declarerForfait(1L, match.id(), false).score())
                .isEqualTo(new Score(3, 0));
        }

        @Test
        @DisplayName("Le classement traite le forfait comme un score ordinaire")
        void leClassementNeSaitPasQuIlYAEuForfait() {
            Match match = poule.matchs().get(0);
            Equipe vainqueur = match.equipeB();

            matchs.declarerForfait(1L, match.id(), true);

            LigneClassement ligne = tournois.classement(1L).stream()
                .filter(l -> l.equipe().equals(vainqueur))
                .findFirst()
                .orElseThrow();

            // RG-44 : trois points pour la victoire, et les buts sont comptés.
            assertThat(ligne.victoires()).isEqualTo(1);
            assertThat(ligne.points()).isEqualTo(3);
            assertThat(ligne.marques()).isEqualTo(3);
            assertThat(ligne.encaisses()).isZero();
        }
    }

    // ------------------------------------------------------------------

    @Nested
    @DisplayName("RG-49 — en élimination directe, le forfait vaut 0 – 1 et qualifie")
    class EnEliminationDirecte {

        private Tournoi arbre;
        private MatchService matchs;

        @BeforeEach
        void preparer() {
            var depotMatchs = new DepotMatchEnMemoire();
            arbre = demarrer(Format.ELIMINATION_DIRECTE, depotMatchs);
            matchs = new MatchService(depotFixe(arbre), depotMatchs, formats, session, connexions);
        }

        @Test
        @DisplayName("Le forfait est enregistré 0 – 1")
        void scoreDeForfait() {
            Match demiFinale = arbre.matchs().get(0);

            assertThat(matchs.declarerForfait(1L, demiFinale.id(), true).score())
                .isEqualTo(new Score(0, 1));
        }

        @Test
        @DisplayName("RG-34 : l'adversaire est qualifié comme après un match joué — "
                   + "la finale se génère seule")
        void laQualificationSePropage() {
            List<Match> demiFinales = List.copyOf(arbre.matchs());
            assertThat(demiFinales).hasSize(2);

            // Une demi-finale gagnée sur le terrain — elle doit être démarrée
            // avant la saisie (RG-52) — l'autre gagnée par forfait, qui se
            // passe justement de ce démarrage (RG-47).
            matchs.demarrer(1L, demiFinales.get(0).id());
            matchs.saisirScore(1L, demiFinales.get(0).id(), 2, 1);
            matchs.declarerForfait(1L, demiFinales.get(1).id(), true);

            List<Match> finale = arbre.matchsDuTour(2);
            assertThat(finale)
                .describedAs("La finale doit apparaître, que la qualification "
                           + "vienne d'un score ou d'un forfait")
                .hasSize(1);

            assertThat(finale.get(0).equipeA()).isEqualTo(demiFinales.get(0).equipeA());
            assertThat(finale.get(0).equipeB()).isEqualTo(demiFinales.get(1).equipeB());
        }
    }

    // ------------------------------------------------------------------

    @Nested
    @DisplayName("RG-47 — quels états acceptent le forfait")
    class TransitionsAutorisees {

        private Tournoi poule;
        private MatchService matchs;

        @BeforeEach
        void preparer() {
            var depotMatchs = new DepotMatchEnMemoire();
            poule = demarrer(Format.POULE, depotMatchs);
            matchs = new MatchService(depotFixe(poule), depotMatchs, formats, session, connexions);
        }

        @Test
        @DisplayName("Un match PLANIFIÉ peut être déclaré forfait sans être démarré")
        void depuisPlanifie() {
            Match match = poule.matchs().get(0);
            assertThat(match.statut()).isEqualTo(StatutMatch.PLANIFIE);

            assertThat(matchs.declarerForfait(1L, match.id(), true).statut())
                .isEqualTo(StatutMatch.TERMINE);
        }

        @Test
        @DisplayName("Un match EN COURS peut être déclaré forfait")
        void depuisEnCours() {
            Match match = poule.matchs().get(0);
            matchs.demarrer(1L, match.id());

            assertThat(matchs.declarerForfait(1L, match.id(), false).statut())
                .isEqualTo(StatutMatch.TERMINE);
        }

        @Test
        @DisplayName("RG-53 : un match TERMINÉ refuse le forfait, comme il refuse une saisie")
        void depuisTermineRefuse() {
            Match match = poule.matchs().get(1);
            matchs.declarerForfait(1L, match.id(), true);

            assertThatThrownBy(() -> matchs.declarerForfait(1L, match.id(), true))
                .isInstanceOf(TransitionInvalideException.class)
                .hasMessageContaining("terminé");

            // Le refus ne doit rien avoir changé.
            assertThat(match.score()).isEqualTo(new Score(0, 3));
        }
    }

    // ------------------------------------------------------------------
    //  Fabrique de contexte
    // ------------------------------------------------------------------

    /**
     * Les identifiants sont attribués par le dépôt, pas ici : Match.attribuerId
     * refuse une seconde attribution, et c'est bien ce qu'on veut d'un
     * identifiant.
     */
    private Tournoi demarrer(Format format, DepotMatchEnMemoire depot) {
        Tournoi tournoi = new Tournoi(1L, "Tournoi", LocalDate.of(2026, 10, 9), format, false);
        for (String nom : List.of("Alpha", "Bravo", "Charlie", "Delta")) {
            tournoi.inscrire(equipeDe(nom));
        }
        List<Match> generes = formats.pour(format).genererMatchs(tournoi);
        depot.creerTous(1L, generes);
        tournoi.demarrer(generes);
        return tournoi;
    }

    private static Equipe equipeDe(String nom) {
        Equipe equipe = new Equipe((long) nom.hashCode(), nom);
        for (int i = 1; i <= 3; i++) {
            equipe.ajouterJoueur(new Joueur(null, nom + "-" + i));
        }
        return equipe;
    }

    /** Dépôt rendant toujours le même tournoi : ces tests n'en manipulent qu'un. */
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
