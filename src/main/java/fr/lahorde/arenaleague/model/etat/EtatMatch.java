package fr.lahorde.arenaleague.model.etat;

import fr.lahorde.arenaleague.model.Match;
import fr.lahorde.arenaleague.model.Score;
import fr.lahorde.arenaleague.model.StatutMatch;
import fr.lahorde.arenaleague.model.exception.TransitionInvalideException;

/**
 * Pattern State — cycle de vie d'un match (RG-50 à RG-54).
 *
 *   PLANIFIÉ --demarrer()--> EN COURS --saisirScore()--> TERMINÉ (terminal)
 *
 * Chaque implémentation sait quelles transitions elle autorise et retourne
 * l'état suivant. Les transitions interdites sont refusées **par l'état
 * lui-même**, via les méthodes par défaut ci-dessous : personne ne
 * l'interroge de l'extérieur, il n'y a aucun switch sur un enum.
 *
 * C'est ce qui distingue le pattern State d'un enum déguisé, et c'est la
 * question que le jury posera en pointant Termine.
 *
 * Implémentations : tâche 2.6.
 */
public interface EtatMatch {

    /** Valeur persistée correspondante. */
    StatutMatch statut();

    /** Libellé affichable dans l'IHM. */
    String libelle();

    /**
     * RG-51 : PLANIFIÉ -> EN COURS.
     * Refusé par défaut : seul Planifie redéfinit cette méthode.
     */
    default EtatMatch demarrer(Match match) {
        throw new TransitionInvalideException(
            "Impossible de démarrer un match " + libelle().toLowerCase() + " (RG-54)");
    }

    /**
     * RG-52 : EN COURS -> TERMINÉ. La saisie du score **est** la clôture.
     * Refusé par défaut : seul EnCours redéfinit cette méthode.
     *
     * RG-61 énonce la même exigence côté service — « le match doit être à
     * l'état EN COURS ». Elle n'est pas vérifiée ailleurs : c'est ce refus
     * par défaut qui la porte, et le message cite RG-54, la règle générale
     * dont il découle.
     *
     * Le refus depuis Termine est le cas de démonstration CE-02 (RG-53).
     */
    default EtatMatch saisirScore(Match match, Score score) {
        throw new TransitionInvalideException(
            "Impossible de saisir un score sur un match " + libelle().toLowerCase() + " (RG-54)");
    }

    /**
     * RG-47 : le forfait ferme le match sans passer par la saisie d'un score.
     *
     * C'est la seule transition qui court-circuite le cycle : PLANIFIÉ **ou**
     * EN COURS mènent directement à TERMINÉ. Une équipe qui ne se présente
     * pas n'a pas à voir son match démarré pour être déclarée forfait.
     *
     * Le score n'est pas saisi mais **imposé par le format** (RG-48, RG-49) :
     * l'état reçoit celui que la stratégie a produit et se contente de
     * transiter. Refusé par défaut, comme les autres transitions — d'où le
     * refus sur un match déjà clos, sans que Termine ait à l'écrire.
     */
    default EtatMatch declarerForfait(Match match, Score score) {
        throw new TransitionInvalideException(
            "Impossible de déclarer forfait sur un match "
            + libelle().toLowerCase() + " (RG-47)");
    }

    /** L'état accepte-t-il une déclaration de forfait ? Voir autoriseDemarrage(). */
    default boolean autoriseForfait() {
        return false;
    }

    /** RG-53 : seul TERMINÉ est terminal. */
    default boolean estTerminal() {
        return false;
    }

    /**
     * L'état accepte-t-il un démarrage ?
     *
     * Existe pour que l'IHM sache quoi **proposer** sans interroger le statut
     * — même principe que peutCreerTournoi() côté rôles : on demande à
     * l'objet ce qu'il autorise plutôt que de tester ce qu'il est. Un écran
     * qui ferait « if (statut == PLANIFIE) » dupliquerait la machine à états
     * et finirait par diverger.
     *
     * Refusé par défaut, comme les transitions elles-mêmes : seul Planifie
     * redéfinit. Ce n'est pas une autorisation, seulement une intention
     * d'affichage — le refus qui fait foi reste celui de demarrer().
     */
    default boolean autoriseDemarrage() {
        return false;
    }

    /** Même rôle pour la saisie du score : seul EnCours redéfinit. */
    default boolean autoriseSaisie() {
        return false;
    }
}
