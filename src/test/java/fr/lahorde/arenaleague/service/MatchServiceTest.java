package fr.lahorde.arenaleague.service;

import fr.lahorde.arenaleague.model.*;
import fr.lahorde.arenaleague.model.exception.TransitionInvalideException;
import fr.lahorde.arenaleague.model.exception.ValidationException;
import fr.lahorde.arenaleague.repository.*;
import fr.lahorde.arenaleague.service.format.FabriqueFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Machine à états et saisie des scores — diagramme 1.6, RG-50 à RG-54, RG-63.
 *
 * Aucune base de données : les repositories sont en mémoire. Ce qu'on teste
 * ici, ce sont les règles métier, pas le SGBD.
 */
class MatchServiceTest {

    private Tournoi tournoi;
    private DepotMatchEnMemoire depotMatchs;
    private MatchService service;
    private final List<Long> notifications = new ArrayList<>();

    @BeforeEach
    void preparer() {
        tournoi = new Tournoi(1L, "Coupe Automne", LocalDate.of(2026, 9, 12), Format.POULE, false);
        for (String nom : List.of("Gen.G", "Hanwha Life Esports", "T1", "KT Rolster")) {
            tournoi.inscrire(equipe(nom));
        }

        FabriqueFormat formats = new FabriqueFormat();
        depotMatchs = new DepotMatchEnMemoire();
        List<Match> generes = formats.pour(Format.POULE).genererMatchs(tournoi);
        depotMatchs.creerTous(1L, generes);
        tournoi.demarrer(generes);

        SessionContext session = new SessionContext();
        session.ouvrir(new Arbitre(2L, "arbitre", "empreinte"));

        service = new MatchService(depotTournoi(), depotMatchs, formats,
                                   session, new TransactionsDirectes());
        service.abonner(notifications::add);
    }

    @Nested
    @DisplayName("Cycle de vie nominal")
    class CycleNominal {

        @Test
        @DisplayName("RG-51, RG-52 : Planifié → En cours → Terminé")
        void parcoursComplet() {
            long id = premierMatch().id();

            assertThat(premierMatch().statut()).isEqualTo(StatutMatch.PLANIFIE);
            service.demarrer(1L, id);
            assertThat(depotMatchs.get(id).statut()).isEqualTo(StatutMatch.EN_COURS);
            service.saisirScore(1L, id, 3, 0);
            assertThat(depotMatchs.get(id).statut()).isEqualTo(StatutMatch.TERMINE);
        }

        @Test
        @DisplayName("L'arbitre qui saisit est enregistré sur le match")
        void arbitreEnregistre() {
            long id = premierMatch().id();
            service.demarrer(1L, id);
            service.saisirScore(1L, id, 2, 1);

            assertThat(depotMatchs.get(id).arbitre().login()).isEqualTo("arbitre");
        }
    }

    @Nested
    @DisplayName("Transitions interdites du diagramme 1.6")
    class TransitionsInterdites {

        @Test
        @DisplayName("RG-53 / CE-02 : saisir un score sur un match terminé est refusé, "
                   + "et le score initial reste intact")
        void secondeSaisieRefusee() {
            long id = premierMatch().id();
            service.demarrer(1L, id);
            service.saisirScore(1L, id, 3, 0);

            assertThatThrownBy(() -> service.saisirScore(1L, id, 2, 2))
                .isInstanceOf(TransitionInvalideException.class)
                .hasMessageContaining("RG-54");

            // Le refus ne doit rien écrire : c'est ce que la démo CE-02 doit montrer.
            assertThat(depotMatchs.get(id).score()).isEqualTo(new Score(3, 0));
        }

        @Test
        @DisplayName("RG-54 : le passage Planifié → Terminé direct est impossible")
        void pasDeRaccourci() {
            long id = premierMatch().id();

            assertThatThrownBy(() -> service.saisirScore(1L, id, 1, 0))
                .isInstanceOf(TransitionInvalideException.class);

            assertThat(depotMatchs.get(id).statut()).isEqualTo(StatutMatch.PLANIFIE);
        }

        @Test
        @DisplayName("RG-54 : redémarrer un match en cours est refusé")
        void doubleDemarrage() {
            long id = premierMatch().id();
            service.demarrer(1L, id);

            assertThatThrownBy(() -> service.demarrer(1L, id))
                .isInstanceOf(TransitionInvalideException.class);
        }

        @Test
        @DisplayName("RG-54 : démarrer un match terminé est refusé")
        void demarrerUnMatchTermine() {
            long id = premierMatch().id();
            service.demarrer(1L, id);
            service.saisirScore(1L, id, 1, 0);

            assertThatThrownBy(() -> service.demarrer(1L, id))
                .isInstanceOf(TransitionInvalideException.class);
        }
    }

    @Nested
    @DisplayName("Observer — RG-63, septième étape")
    class Notification {

        @Test
        @DisplayName("Une saisie réussie notifie les abonnés")
        void saisieNotifie() {
            long id = premierMatch().id();
            service.demarrer(1L, id);
            service.saisirScore(1L, id, 2, 0);

            assertThat(notifications).containsExactly(1L);
        }

        @Test
        @DisplayName("Un refus ne notifie personne : le classement n'a pas changé")
        void refusNeNotifiePas() {
            long id = premierMatch().id();

            assertThatThrownBy(() -> service.saisirScore(1L, id, 2, 0))
                .isInstanceOf(TransitionInvalideException.class);

            assertThat(notifications).isEmpty();
        }
    }

    @Nested
    @DisplayName("Propagation du tour suivant — RG-35")
    class Propagation {

        @Test
        @DisplayName("La finale n'apparaît qu'une fois les deux demi-finales terminées")
        void finaleGenereeAuBonMoment() {
            Tournoi arbre = new Tournoi(2L, "Open Hiver", LocalDate.of(2026, 9, 19),
                                        Format.ELIMINATION_DIRECTE, false);
            for (String nom : List.of("Gen.G", "Hanwha Life Esports", "T1", "KT Rolster")) {
                arbre.inscrire(equipe(nom));
            }

            FabriqueFormat formats = new FabriqueFormat();
            DepotMatchEnMemoire depot = new DepotMatchEnMemoire();
            List<Match> demies = formats.pour(Format.ELIMINATION_DIRECTE).genererMatchs(arbre);
            depot.creerTous(2L, demies);
            arbre.demarrer(demies);

            SessionContext session = new SessionContext();
            session.ouvrir(new Arbitre(2L, "arbitre", "empreinte"));
            MatchService matchs = new MatchService(depotFixe(arbre), depot, formats,
                                                   session, new TransactionsDirectes());

            matchs.demarrer(2L, demies.get(0).id());
            matchs.saisirScore(2L, demies.get(0).id(), 2, 1);
            assertThat(arbre.matchs()).hasSize(2);          // toujours pas de finale

            matchs.demarrer(2L, demies.get(1).id());
            matchs.saisirScore(2L, demies.get(1).id(), 3, 0);
            assertThat(arbre.matchs()).hasSize(3);          // RG-35
            assertThat(arbre.dernierTour()).isEqualTo(2);

            // RG-33 : le nul reste refusé, y compris en finale.
            Match finale = arbre.matchsDuTour(2).get(0);
            matchs.demarrer(2L, finale.id());
            assertThatThrownBy(() -> matchs.saisirScore(2L, finale.id(), 1, 1))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("RG-33");

            // RG-36 : rien après la finale.
            matchs.saisirScore(2L, finale.id(), 2, 1);
            assertThat(arbre.matchs()).hasSize(3);
        }
    }

    // ------------------------------------------------------------------

    private Match premierMatch() {
        return tournoi.matchs().get(0);
    }

    private static Equipe equipe(String nom) {
        Equipe equipe = new Equipe(nom);
        equipe.ajouterJoueur(new Joueur(nom + "-1"));
        equipe.ajouterJoueur(new Joueur(nom + "-2"));
        return equipe;
    }

    private TournoiRepository depotTournoi() {
        return depotFixe(tournoi);
    }

    private static TournoiRepository depotFixe(Tournoi unique) {
        return new TournoiRepository() {
            public Tournoi creer(Tournoi t) { return t; }
            public java.util.Optional<Tournoi> parId(long id) { return java.util.Optional.of(unique); }
            public List<Tournoi> tous() { return List.of(unique); }
            public void inscrire(long tournoiId, long equipeId) { }
            public void retirer(long tournoiId, long equipeId) { }
            public void marquerDemarre(long tournoiId) { }
        };
    }
}
