package fr.lahorde.arenaleague.service.format;

import fr.lahorde.arenaleague.model.*;
import fr.lahorde.arenaleague.model.etat.EtatsMatch;
import fr.lahorde.arenaleague.model.exception.ValidationException;

import java.util.*;

/**
 * Format en poule unique, round-robin aller simple — RG-40 à RG-46.
 *
 * Chaque équipe rencontre chaque autre une fois. Pour N équipes :
 * N(N−1)/2 matchs, tous au tour 1.
 */
public final class Poule implements FormatTournoi {

    /** RG-44 : barème. */
    public static final int PTS_VICTOIRE = 3;
    public static final int PTS_NUL      = 1;
    public static final int PTS_DEFAITE  = 0;

    /** RG-46 : les deux premières équipes sont qualifiées. */
    public static final int NB_QUALIFIEES = 2;

    @Override
    public Format format() {
        return Format.POULE;
    }

    /** RG-40 : entre 3 et 8 équipes. Aucune contrainte de parité. */
    @Override
    public boolean nbEquipesValide(int nbEquipes) {
        return nbEquipes >= 3 && nbEquipes <= 8;
    }

    @Override
    public String contrainteEffectif() {
        return "Une poule accueille entre 3 et 8 équipes.";
    }

    /** RG-41, RG-42 : toutes les rencontres générées d'un coup, au tour 1. */
    @Override
    public List<Match> genererMatchs(Tournoi tournoi) {
        List<Equipe> equipes = new ArrayList<>(tournoi.equipes());
        if (!nbEquipesValide(equipes.size())) {
            throw new ValidationException(
                equipes.size() + " équipe(s) inscrite(s). " + contrainteEffectif());
        }

        List<Match> matchs = new ArrayList<>();
        for (int i = 0; i < equipes.size(); i++) {
            for (int j = i + 1; j < equipes.size(); j++) {
                matchs.add(new Match(1, equipes.get(i), equipes.get(j), EtatsMatch.initial()));
            }
        }
        return matchs;
    }

    /** RG-43 : le match nul est autorisé. Rien à refuser au-delà de RG-60. */
    @Override
    public void validerScore(Score score) {
        // Aucune contrainte propre au format. La méthode existe quand même :
        // c'est le contrat de la Strategy, et c'est ce qui permet au service
        // d'appeler validerScore() sans jamais savoir à quel format il parle.
    }

    /**
     * RG-44, RG-71 à RG-75.
     *
     * L'ordre des critères est strict : points, différence, points marqués,
     * confrontation directe, puis ordre alphabétique.
     */
    /**
     * RG-48 : forfait enregistré 3 – 0 pour l'adversaire.
     *
     * L'adversaire encaisse une victoire pleine : trois points marqués, le
     * barème de RG-44 s'applique ensuite sans traitement particulier.
     */
    @Override
    public Score scoreForfait(boolean forfaitEquipeA) {
        return forfaitEquipeA ? new Score(0, 3) : new Score(3, 0);
    }

    @Override
    public List<LigneClassement> calculerClassement(Tournoi tournoi) {
        Map<Equipe, int[]> bilans = new LinkedHashMap<>();
        // [0] points, [1] joues, [2] victoires, [3] nuls, [4] defaites, [5] marques, [6] encaisses
        for (Equipe equipe : tournoi.equipes()) {
            bilans.put(equipe, new int[7]);
        }

        for (Match match : tournoi.matchsTermines()) {
            Score score = match.score();
            comptabiliser(bilans, match.equipeA(), score.equipeA(), score.equipeB());
            comptabiliser(bilans, match.equipeB(), score.equipeB(), score.equipeA());
        }

        List<LigneClassement> lignes = new ArrayList<>();
        bilans.forEach((equipe, b) ->
            lignes.add(new LigneClassement(0, equipe, b[0], b[1], b[2], b[3], b[4], b[5], b[6], false)));

        lignes.sort(Comparateurs.PAR_POINTS);
        appliquerConfrontationDirecte(lignes, tournoi);

        return Comparateurs.numeroter(lignes, ligne -> ligne.rang() <= NB_QUALIFIEES);
    }

    private void comptabiliser(Map<Equipe, int[]> bilans, Equipe equipe, int pour, int contre) {
        int[] b = bilans.computeIfAbsent(equipe, e -> new int[7]);
        b[1]++;
        b[5] += pour;
        b[6] += contre;
        if (pour > contre)      { b[0] += PTS_VICTOIRE; b[2]++; }
        else if (pour == contre){ b[0] += PTS_NUL;      b[3]++; }
        else                    { b[0] += PTS_DEFAITE;  b[4]++; }
    }

    /**
     * RG-74 : confrontation directe, appliquée après le tri principal.
     *
     * Elle ne départage que des paires d'équipes strictement à égalité sur
     * les trois premiers critères. Pour trois équipes ou plus, on tombe
     * directement sur RG-75 — limitation assumée et documentée en 1.2.
     */
    private void appliquerConfrontationDirecte(List<LigneClassement> lignes, Tournoi tournoi) {
        for (int i = 0; i < lignes.size() - 1; i++) {
            final int rang = i;
            LigneClassement premiere = lignes.get(rang);
            LigneClassement seconde  = lignes.get(rang + 1);
            if (!strictementAEgalite(premiere, seconde)) continue;
            if (rang + 2 < lignes.size() && strictementAEgalite(seconde, lignes.get(rang + 2))) continue;

            Optional<Match> duel = tournoi.matchsTermines().stream()
                .filter(m -> m.concerne(premiere.equipe()) && m.concerne(seconde.equipe()))
                .findFirst();

            if (duel.isEmpty()) continue;
            Equipe gagnant = duel.get().vainqueur();
            // Un nul ne départage pas : on laisse RG-75 trancher.
            if (gagnant != null && gagnant.equals(seconde.equipe())) {
                lignes.set(rang, seconde);
                lignes.set(rang + 1, premiere);
            }
        }
    }

    private boolean strictementAEgalite(LigneClassement a, LigneClassement b) {
        return a.points() == b.points()
            && a.difference() == b.difference()
            && a.marques() == b.marques();
    }
}
