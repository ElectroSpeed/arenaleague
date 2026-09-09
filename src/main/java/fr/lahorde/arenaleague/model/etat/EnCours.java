package fr.lahorde.arenaleague.model.etat;

import fr.lahorde.arenaleague.model.Match;
import fr.lahorde.arenaleague.model.Score;
import fr.lahorde.arenaleague.model.StatutMatch;

/**
 * Rencontre en train de se jouer — RG-51.
 *
 * Seule transition autorisée : saisirScore(). Redémarrer un match déjà en
 * cours est refusé par la méthode par défaut.
 */
public final class EnCours implements EtatMatch {

    @Override
    public StatutMatch statut() {
        return StatutMatch.EN_COURS;
    }

    @Override
    public String libelle() {
        return "En cours";
    }

    /**
     * RG-52 : EN COURS → TERMINÉ. La saisie du score **est** la clôture.
     *
     * La validation du score au regard du format (RG-33 : nul interdit en
     * élimination directe) n'est pas faite ici : l'état ne connaît pas le
     * tournoi. Elle a lieu dans le service, avant l'appel — voir le
     * diagramme de séquence.
     */
    @Override
    public EtatMatch saisirScore(Match match, Score score) {
        return new Termine();
    }
}
