package fr.lahorde.arenaleague.service.format;

import fr.lahorde.arenaleague.model.LigneClassement;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Critères de départage — RG-71 à RG-76.
 *
 * Le tri est fait **en Java, jamais par une clause ORDER BY**. Ce n'est pas
 * un détail : une collation française et une collation C ne classent pas les
 * mêmes chaînes dans le même ordre. Laisser le SGBD trier rendrait le
 * classement dépendant de l'installation, et deux postes pourraient afficher
 * deux classements différents pour les mêmes résultats.
 */
final class Comparateurs {

    private Comparateurs() {}

    /**
     * RG-75 : ordre alphabétique, dernier critère et seul totalement
     * déterministe.
     *
     * Collator en Locale.FRENCH plutôt que compareTo : « École » et « Ecole »
     * doivent se classer côte à côte, ce que la comparaison brute des points
     * de code ne fait pas.
     */
    private static final Collator ALPHABETIQUE = Collator.getInstance(Locale.FRENCH);
    static {
        ALPHABETIQUE.setStrength(Collator.SECONDARY);
    }

    static final Comparator<LigneClassement> PAR_NOM =
        (a, b) -> ALPHABETIQUE.compare(a.equipe().nom(), b.equipe().nom());

    /** RG-71 → RG-73 → RG-75. La confrontation directe (RG-74) est appliquée à part. */
    static final Comparator<LigneClassement> PAR_POINTS =
        Comparator.comparingInt(LigneClassement::points).reversed()
                  .thenComparing(Comparator.comparingInt(LigneClassement::difference).reversed())
                  .thenComparing(Comparator.comparingInt(LigneClassement::marques).reversed())
                  .thenComparing(PAR_NOM);

    /** RG-76 : tour atteint décroissant, puis RG-75. */
    static final Comparator<LigneClassement> PAR_TOUR_ATTEINT =
        Comparator.comparingInt(LigneClassement::points).reversed()
                  .thenComparing(PAR_NOM);

    /** Attribue les rangs de 1 à n et pose le marqueur de qualification. */
    static List<LigneClassement> numeroter(List<LigneClassement> triees,
                                           Predicate<LigneClassement> qualifiee) {
        List<LigneClassement> resultat = new ArrayList<>(triees.size());
        for (int i = 0; i < triees.size(); i++) {
            LigneClassement ligne = triees.get(i).avecRang(i + 1);
            resultat.add(ligne.avecQualification(qualifiee.test(ligne)));
        }
        return resultat;
    }
}
