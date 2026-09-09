package fr.lahorde.arenaleague.model.etat;

import fr.lahorde.arenaleague.model.StatutMatch;

/**
 * Match clôturé — RG-53, état terminal.
 *
 * **Cette classe ne redéfinit rien.** C'est précisément ce qui la rend
 * intéressante : elle hérite des refus par défaut de EtatMatch, donc toute
 * transition sortante lève TransitionInvalideException.
 *
 * C'est le cas de démonstration CE-02. Quand le jury demande où une seconde
 * saisie est refusée, la réponse tient en une phrase : nulle part, et c'est
 * voulu — l'état n'autorise que ce qu'il déclare autoriser.
 */
public final class Termine implements EtatMatch {

    @Override
    public StatutMatch statut() {
        return StatutMatch.TERMINE;
    }

    @Override
    public String libelle() {
        return "Terminé";
    }

    @Override
    public boolean estTerminal() {
        return true;
    }
}
