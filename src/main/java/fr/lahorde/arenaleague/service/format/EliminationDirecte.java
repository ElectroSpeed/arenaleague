package fr.lahorde.arenaleague.service.format;

import fr.lahorde.arenaleague.model.*;
import fr.lahorde.arenaleague.model.etat.EtatsMatch;
import fr.lahorde.arenaleague.model.exception.ValidationException;

import java.util.*;

/**
 * Format à élimination directe — RG-30 à RG-37.
 *
 * Arbre binaire : N équipes, N−1 matchs, log₂(N) tours. Le vainqueur avance,
 * le perdant sort.
 */
public final class EliminationDirecte implements FormatTournoi {

    private final Random tirage;

    public EliminationDirecte() {
        this(new Random());
    }

    /** Graine fixe pour les tests : le tirage doit être reproductible. */
    public EliminationDirecte(Random tirage) {
        this.tirage = tirage;
    }

    @Override
    public Format format() {
        return Format.ELIMINATION_DIRECTE;
    }

    /**
     * RG-30 : puissance de 2 uniquement.
     *
     * Décision assumée, pas un oubli. Accepter 5, 6 ou 7 équipes imposerait
     * des exemptions de premier tour — les « byes » — qui doublent la
     * complexité de la génération d'arbre pour un gain fonctionnel nul sur ce
     * périmètre. Le refus est explicite et porte un message qui dit quoi faire.
     */
    @Override
    public boolean nbEquipesValide(int nbEquipes) {
        return nbEquipes >= 4 && (nbEquipes & (nbEquipes - 1)) == 0;
    }

    @Override
    public String contrainteEffectif() {
        return "L'élimination directe demande un nombre d'équipes en puissance de 2 "
             + "(4, 8, 16, 32). Retirez ou ajoutez des équipes, ou choisissez le format Poule.";
    }

    /**
     * RG-31, RG-32 : appariement aléatoire du premier tour.
     *
     * Seul le tour 1 est généré. Les suivants naissent au fur et à mesure,
     * quand tous les matchs du tour précédent sont terminés (RG-35) — on ne
     * peut pas connaître les affiches d'une demi-finale avant les quarts.
     */
    @Override
    public List<Match> genererMatchs(Tournoi tournoi) {
        List<Equipe> equipes = new ArrayList<>(tournoi.equipes());
        exigerEffectifValide(equipes.size());

        Collections.shuffle(equipes, tirage);

        List<Match> matchs = new ArrayList<>();
        for (int i = 0; i < equipes.size(); i += 2) {
            matchs.add(new Match(1, equipes.get(i), equipes.get(i + 1), EtatsMatch.initial()));
        }
        return matchs;
    }

    /**
     * RG-34, RG-35 : matchs du tour suivant, à partir des vainqueurs.
     *
     * Retourne une liste vide si le tour courant n'est pas terminé ou si le
     * tournoi l'est. Le service ne teste donc rien : il persiste ce qu'il
     * reçoit, et ne reçoit rien quand il n'y a rien à faire.
     */
    @Override
    public List<Match> genererTourSuivant(Tournoi tournoi) {
        int tourCourant = tournoi.dernierTour();
        if (tourCourant == 0 || !tournoi.tourTermine(tourCourant)) {
            return List.of();
        }

        List<Equipe> vainqueurs = tournoi.matchsDuTour(tourCourant).stream()
                                         .map(Match::vainqueur)
                                         .toList();
        if (vainqueurs.size() < 2) {
            return List.of();          // RG-36 : la finale est jouée, le tournoi est terminé
        }

        List<Match> suivants = new ArrayList<>();
        for (int i = 0; i < vainqueurs.size(); i += 2) {
            suivants.add(new Match(tourCourant + 1, vainqueurs.get(i), vainqueurs.get(i + 1),
                                   EtatsMatch.initial()));
        }
        return suivants;
    }

    /** RG-33 : le match nul est impossible, il faut départager. */
    @Override
    public void validerScore(Score score) {
        if (score.estNul()) {
            throw new ValidationException(
                "Le match nul est impossible en élimination directe : "
                + "une équipe doit être éliminée (RG-33)");
        }
    }

    /**
     * RG-76 : classement par tour atteint, décroissant.
     *
     * Le vainqueur du dernier tour est premier, son adversaire deuxième, puis
     * les équipes éliminées de plus en plus tôt. À tour égal, RG-75 départage
     * par ordre alphabétique — sans quoi l'ordre dépendrait du parcours en
     * mémoire et deux exécutions donneraient deux résultats.
     */
    @Override
    public List<LigneClassement> calculerClassement(Tournoi tournoi) {
        Map<Equipe, Integer> dernierTourAtteint = new HashMap<>();
        Map<Equipe, int[]> bilan = new HashMap<>();   // [victoires, defaites, marques, encaisses]

        for (Equipe equipe : tournoi.equipes()) {
            dernierTourAtteint.put(equipe, 0);
            bilan.put(equipe, new int[4]);
        }

        for (Match match : tournoi.matchsTermines()) {
            for (Equipe equipe : List.of(match.equipeA(), match.equipeB())) {
                dernierTourAtteint.merge(equipe, match.tour(), Math::max);
                int[] b = bilan.computeIfAbsent(equipe, e -> new int[4]);
                if (equipe.equals(match.vainqueur())) b[0]++; else b[1]++;
                b[2] += match.marquesPar(equipe);
                b[3] += match.encaissesPar(equipe);
            }
            Equipe gagnant = match.vainqueur();
            if (gagnant != null) {
                dernierTourAtteint.merge(gagnant, match.tour() + 1, Math::max);
            }
        }

        List<LigneClassement> lignes = new ArrayList<>();
        for (Equipe equipe : dernierTourAtteint.keySet()) {
            int[] b = bilan.get(equipe);
            lignes.add(new LigneClassement(0, equipe, dernierTourAtteint.get(equipe),
                                           b[0] + b[1], b[0], 0, b[1], b[2], b[3], false));
        }

        lignes.sort(Comparateurs.PAR_TOUR_ATTEINT);
        return Comparateurs.numeroter(lignes, ligne -> ligne.rang() == 1);
    }

    private void exigerEffectifValide(int nbEquipes) {
        if (!nbEquipesValide(nbEquipes)) {
            throw new ValidationException(
                nbEquipes + " équipe(s) inscrite(s). " + contrainteEffectif());
        }
    }
}
