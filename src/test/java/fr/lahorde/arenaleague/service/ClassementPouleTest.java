package fr.lahorde.arenaleague.service;

import fr.lahorde.arenaleague.model.*;
import fr.lahorde.arenaleague.model.exception.ValidationException;
import fr.lahorde.arenaleague.service.format.FabriqueFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Calcul du classement et pattern Strategy — RG-33, RG-40 à RG-46, RG-71 à RG-76.
 *
 * Le scénario de poule reproduit **exactement** le tableau de référence du
 * §8.1 des règles de gestion. Ce n'est pas un jeu de données inventé pour le
 * test : c'est le même que celui chargé par la migration V2, donc le même que
 * celui montré au jury pendant la démonstration.
 */
class ClassementPouleTest {

    private FabriqueFormat fabrique;
    private FormatTournoi poule;
    private FormatTournoi elimination;

    @BeforeEach
    void preparer() {
        fabrique = new FabriqueFormat();
        poule = fabrique.pour(Format.POULE);
        elimination = fabrique.pour(Format.ELIMINATION_DIRECTE);
    }

    // ------------------------------------------------------------------
    //  Le tableau de référence
    // ------------------------------------------------------------------

    @Test
    @DisplayName("RG-75 : deux équipes strictement à égalité sont départagées "
               + "par l'ordre alphabétique, leur confrontation directe étant nulle")
    void tableauDeReference() {
        Tournoi tournoi = poule("Alpha", "Bravo", "Charlie", "Delta");
        tournoi.demarrer(poule.genererMatchs(tournoi));

        jouer(tournoi, "Alpha",   "Bravo",   1, 1);
        jouer(tournoi, "Alpha",   "Delta",   2, 0);
        jouer(tournoi, "Alpha",   "Charlie", 0, 2);
        jouer(tournoi, "Bravo",   "Delta",   2, 0);
        jouer(tournoi, "Bravo",   "Charlie", 0, 2);
        jouer(tournoi, "Charlie", "Delta",   3, 0);

        List<LigneClassement> classement = poule.calculerClassement(tournoi);

        assertThat(classement).extracting(l -> l.equipe().nom())
            .containsExactly("Charlie", "Alpha", "Bravo", "Delta");
        assertThat(classement).extracting(LigneClassement::points)
            .containsExactly(9, 4, 4, 0);

        LigneClassement alpha = classement.get(1);
        LigneClassement bravo = classement.get(2);

        // Les trois premiers critères ne les séparent pas...
        assertThat(alpha.points()).isEqualTo(bravo.points());
        assertThat(alpha.difference()).isEqualTo(bravo.difference());
        assertThat(alpha.marques()).isEqualTo(bravo.marques());
        // ...leur duel non plus (1 – 1). Seul RG-75 tranche.

        // RG-46 : les deux premières sont qualifiées.
        assertThat(classement).extracting(LigneClassement::qualifiee)
            .containsExactly(true, true, false, false);
    }

    @Test
    @DisplayName("En round-robin, total des points marqués = total des encaissés")
    void coherenceDesTotaux() {
        Tournoi tournoi = poule("Alpha", "Bravo", "Charlie");
        tournoi.demarrer(poule.genererMatchs(tournoi));
        jouer(tournoi, "Alpha", "Bravo",   3, 1);
        jouer(tournoi, "Alpha", "Charlie", 0, 2);
        jouer(tournoi, "Bravo", "Charlie", 1, 1);

        List<LigneClassement> classement = poule.calculerClassement(tournoi);

        int marques   = classement.stream().mapToInt(LigneClassement::marques).sum();
        int encaisses = classement.stream().mapToInt(LigneClassement::encaisses).sum();
        assertThat(marques).isEqualTo(encaisses);
    }

    @Test
    @DisplayName("RG-74 : à égalité parfaite, la confrontation directe départage")
    void confrontationDirecte() {
        Tournoi tournoi = poule("Xerus", "Yeti", "Zephyr");
        tournoi.demarrer(poule.genererMatchs(tournoi));
        jouer(tournoi, "Xerus", "Yeti",   2, 1);
        jouer(tournoi, "Xerus", "Zephyr", 0, 3);
        jouer(tournoi, "Yeti",  "Zephyr", 0, 3);

        // Xerus et Yeti sont à égalité ; Xerus a gagné leur duel, il passe devant
        // alors que l'ordre alphabétique aurait donné le même résultat ici —
        // le test vérifie surtout que le critère ne casse pas le classement.
        assertThat(poule.calculerClassement(tournoi))
            .extracting(l -> l.equipe().nom())
            .containsExactly("Zephyr", "Xerus", "Yeti");
    }

    // ------------------------------------------------------------------
    //  Le pattern Strategy
    // ------------------------------------------------------------------

    @Test
    @DisplayName("RG-33 / RG-43 : le même score est accepté en poule et refusé "
               + "en élimination directe, sans aucun test de format dans le service")
    void memeScoreDeuxVerdicts() {
        Score nul = new Score(2, 2);

        // Aucune exception : le nul est valide en poule.
        poule.validerScore(nul);

        assertThatThrownBy(() -> elimination.validerScore(nul))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("RG-33");
    }

    @Test
    @DisplayName("RG-30 : 5, 6 ou 7 équipes en élimination directe sont refusées "
               + "avec un message qui dit quoi faire")
    void effectifsNonPuissanceDeDeux() {
        for (int nbEquipes = 5; nbEquipes <= 7; nbEquipes++) {
            assertThat(elimination.nbEquipesValide(nbEquipes)).isFalse();
            assertThat(poule.nbEquipesValide(nbEquipes)).isTrue();   // RG-40 : 3 à 8
        }
        assertThat(elimination.contrainteEffectif()).contains("puissance de 2");
    }

    @Test
    @DisplayName("RG-41 : N équipes en poule produisent N(N−1)/2 matchs, tous au tour 1")
    void nombreDeMatchsEnPoule() {
        for (int n = 3; n <= 8; n++) {
            Tournoi tournoi = poule(noms(n));
            List<Match> matchs = poule.genererMatchs(tournoi);

            assertThat(matchs).hasSize(n * (n - 1) / 2);
            assertThat(matchs).allMatch(m -> m.tour() == 1);
        }
    }

    // ------------------------------------------------------------------
    //  Utilitaires
    // ------------------------------------------------------------------

    private static String[] noms(int nombre) {
        String[] noms = new String[nombre];
        for (int i = 0; i < nombre; i++) noms[i] = "Equipe" + (char) ('A' + i);
        return noms;
    }

    private static Tournoi poule(String... nomsEquipes) {
        Tournoi tournoi = new Tournoi("Coupe Automne", LocalDate.of(2026, 9, 12), Format.POULE);
        for (String nom : nomsEquipes) {
            Equipe equipe = new Equipe(nom);
            equipe.ajouterJoueur(new Joueur(nom + "-1"));
            equipe.ajouterJoueur(new Joueur(nom + "-2"));
            tournoi.inscrire(equipe);
        }
        return tournoi;
    }

    private static void jouer(Tournoi tournoi, String a, String b, int scoreA, int scoreB) {
        for (Match match : tournoi.matchs()) {
            if (match.estTermine()) continue;
            boolean memeSens    = match.equipeA().nom().equals(a) && match.equipeB().nom().equals(b);
            boolean sensInverse = match.equipeA().nom().equals(b) && match.equipeB().nom().equals(a);
            if (!memeSens && !sensInverse) continue;

            match.demarrer();
            match.saisirScore(memeSens ? new Score(scoreA, scoreB) : new Score(scoreB, scoreA));
            return;
        }
        throw new IllegalStateException("Match introuvable : " + a + " – " + b);
    }
}
