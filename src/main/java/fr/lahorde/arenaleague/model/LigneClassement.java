package fr.lahorde.arenaleague.model;

/**
 * Une ligne du classement — objet valeur immuable.
 *
 * Jamais persistée : le classement est recalculé à la demande à partir des
 * matchs terminés (RG-70). Une table de classement en base serait une
 * duplication qu'il faudrait maintenir cohérente.
 *
 * @param rang        position, 1 pour le premier
 * @param equipe      équipe classée
 * @param points      barème RG-44 en poule, non significatif en élimination directe
 * @param joues       nombre de matchs terminés joués
 * @param victoires   nombre de victoires
 * @param nuls        nombre de matchs nuls
 * @param defaites    nombre de défaites
 * @param marques     points marqués, toutes rencontres confondues
 * @param encaisses   points encaissés
 * @param qualifiee   RG-46 en poule, RG-34 en élimination directe
 */
public record LigneClassement(
        int rang,
        Equipe equipe,
        int points,
        int joues,
        int victoires,
        int nuls,
        int defaites,
        int marques,
        int encaisses,
        boolean qualifiee) {

    /** RG-72 : différence de points, deuxième critère de départage. */
    public int difference() {
        return marques - encaisses;
    }

    /** Recopie avec un nouveau rang — utilisé après le tri. */
    public LigneClassement avecRang(int nouveauRang) {
        return new LigneClassement(nouveauRang, equipe, points, joues, victoires,
                                   nuls, defaites, marques, encaisses, qualifiee);
    }

    /** Recopie avec le marqueur de qualification — RG-46. */
    public LigneClassement avecQualification(boolean qualifiee) {
        return new LigneClassement(rang, equipe, points, joues, victoires,
                                   nuls, defaites, marques, encaisses, qualifiee);
    }

    @Override
    public String toString() {
        return String.format("%d. %-10s %2d pts  %d/%d/%d  %+d",
                rang, equipe.nom(), points, victoires, nuls, defaites, difference());
    }
}
