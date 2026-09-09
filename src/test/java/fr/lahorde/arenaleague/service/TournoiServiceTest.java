package fr.lahorde.arenaleague.service;

import fr.lahorde.arenaleague.model.*;
import fr.lahorde.arenaleague.model.exception.ValidationException;
import fr.lahorde.arenaleague.repository.*;
import fr.lahorde.arenaleague.service.format.FabriqueFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Inscriptions et démarrage d'un tournoi — RG-11, RG-20 à RG-23.
 *
 * Ces règles décident de ce qui est possible avant le coup d'envoi. Elles sont
 * moins spectaculaires que la machine à états, mais ce sont elles qui
 * empêchent un tournoi de démarrer dans un état incohérent — situation
 * irrécupérable puisque RG-22 interdit de recommencer.
 */
class TournoiServiceTest {

    private Tournoi coupeAutomne;
    private DepotMatchEnMemoire depotMatchs;
    private DepotEquipes depotEquipes;
    private DepotTournoi depotTournois;
    private TournoiService service;

    @BeforeEach
    void preparer() {
        coupeAutomne = new Tournoi(1L, "Coupe Automne", LocalDate.of(2026, 9, 12),
                                   Format.POULE, false);
        depotMatchs = new DepotMatchEnMemoire();
        depotEquipes = new DepotEquipes();
        depotTournois = new DepotTournoi(coupeAutomne);

        SessionContext session = new SessionContext();
        session.ouvrir(new Organisateur(1L, "orga", "empreinte"));

        service = new TournoiService(depotTournois, depotEquipes, depotMatchs,
                                     new FabriqueFormat(), session, new TransactionsDirectes());
    }

    @Test
    @DisplayName("RG-11 : une équipe d'un seul joueur ne peut pas être inscrite")
    void effectifInsuffisant() {
        depotEquipes.ajouter(equipe(10L, "Solo", 1));

        assertThatThrownBy(() -> service.inscrire(1L, 10L))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("RG-10");

        assertThat(coupeAutomne.equipes()).isEmpty();
    }

    @Test
    @DisplayName("RG-21 : une équipe ne peut pas être inscrite deux fois")
    void doubleInscription() {
        depotEquipes.ajouter(equipe(10L, "Alpha", 3));
        service.inscrire(1L, 10L);

        assertThatThrownBy(() -> service.inscrire(1L, 10L))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("RG-21");

        assertThat(coupeAutomne.equipes()).hasSize(1);
    }

    @Test
    @DisplayName("RG-23 : un tournoi sans assez d'équipes ne démarre pas, "
               + "et le message dit quoi faire")
    void effectifInvalideAuDemarrage() {
        inscrire("Alpha", "Bravo");        // RG-40 en demande 3 minimum

        assertThatThrownBy(() -> service.demarrer(1L))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("3 et 8");

        assertThat(coupeAutomne.estDemarre()).isFalse();
        assertThat(coupeAutomne.matchs()).isEmpty();
    }

    @Test
    @DisplayName("RG-22 : le démarrage génère les matchs et devient irréversible")
    void demarrageNominal() {
        inscrire("Alpha", "Bravo", "Charlie", "Delta");

        service.demarrer(1L);

        assertThat(coupeAutomne.estDemarre()).isTrue();
        assertThat(coupeAutomne.matchs()).hasSize(6);          // RG-41 : 4×3/2
        assertThat(depotTournois.demarrages).isEqualTo(1);

        assertThatThrownBy(() -> service.demarrer(1L))
            .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("RG-20 : plus aucune inscription une fois le tournoi démarré")
    void inscriptionApresDemarrage() {
        inscrire("Alpha", "Bravo", "Charlie", "Delta");
        service.demarrer(1L);

        depotEquipes.ajouter(equipe(99L, "Echo", 3));

        assertThatThrownBy(() -> service.inscrire(1L, 99L))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("RG-20");
    }

    @Test
    @DisplayName("RG-20 : plus aucun retrait une fois le tournoi démarré")
    void retraitApresDemarrage() {
        inscrire("Alpha", "Bravo", "Charlie", "Delta");
        service.demarrer(1L);

        assertThatThrownBy(() -> service.retirer(1L, 10L))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("RG-20");
    }

    // ------------------------------------------------------------------

    private void inscrire(String... noms) {
        long id = 10;
        for (String nom : noms) {
            depotEquipes.ajouter(equipe(id, nom, 3));
            service.inscrire(1L, id);
            id++;
        }
    }

    private static Equipe equipe(long id, String nom, int nombreDeJoueurs) {
        Equipe equipe = new Equipe(id, nom);
        for (int i = 1; i <= nombreDeJoueurs; i++) {
            equipe.ajouterJoueur(new Joueur(nom + "-" + i));
        }
        return equipe;
    }

    private static final class DepotEquipes implements EquipeRepository {
        private final Map<Long, Equipe> equipes = new LinkedHashMap<>();
        void ajouter(Equipe equipe) { equipes.put(equipe.id(), equipe); }
        public Equipe creer(Equipe equipe) { ajouter(equipe); return equipe; }
        public Optional<Equipe> parId(long id) { return Optional.ofNullable(equipes.get(id)); }
        public Optional<Equipe> parNom(String nom) {
            return equipes.values().stream().filter(e -> e.nom().equals(nom)).findFirst(); }
        public List<Equipe> toutes() { return new ArrayList<>(equipes.values()); }
        public List<Equipe> inscritesA(long tournoiId) { return List.of(); }
    }

    private static final class DepotTournoi implements TournoiRepository {
        private final Tournoi unique;
        int demarrages = 0;
        DepotTournoi(Tournoi unique) { this.unique = unique; }
        public Tournoi creer(Tournoi t) { return t; }
        public Optional<Tournoi> parId(long id) { return Optional.of(unique); }
        public List<Tournoi> tous() { return List.of(unique); }
        public void inscrire(long tournoiId, long equipeId) { }
        public void retirer(long tournoiId, long equipeId) { }
        public void marquerDemarre(long tournoiId) { demarrages++; }
    }
}
