package fr.lahorde.arenaleague.model.etat;

import fr.lahorde.arenaleague.model.Match;
import fr.lahorde.arenaleague.model.Score;
import fr.lahorde.arenaleague.model.StatutMatch;

/**
 * Match créé, pas encore commencé — RG-50.
 *
 * Seule transition autorisée : démarrer(). Toute autre est refusée par les
 * méthodes par défaut de EtatMatch, sans qu'il y ait ici la moindre ligne
 * pour l'interdire. C'est la différence avec un enum : on ne teste pas l'état,
 * on lui demande.
 */
public final class Planifie implements EtatMatch {

    @Override
    public StatutMatch statut() {
        return StatutMatch.PLANIFIE;
    }

    @Override
    public String libelle() {
        return "Planifié";
    }

    /** RG-51 : PLANIFIÉ → EN COURS. */
    @Override
    public boolean autoriseForfait() {
        return true;
    }

    /** RG-47 : forfait accepté depuis cet état, le match est clos aussitôt. */
    @Override
    public EtatMatch declarerForfait(Match match, Score score) {
        return new Termine();
    }

    @Override
    public boolean autoriseDemarrage() {
        return true;
    }

    @Override
    public EtatMatch demarrer(Match match) {
        return new EnCours();
    }
}
