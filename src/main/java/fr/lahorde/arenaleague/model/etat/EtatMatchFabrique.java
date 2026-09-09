package fr.lahorde.arenaleague.model.etat;

import fr.lahorde.arenaleague.model.StatutMatch;

/**
 * Reconstruit l'objet d'état à partir de sa valeur persistée.
 *
 * Le repository lit une colonne `etat` et doit rendre un objet EtatMatch.
 * Passer par cette fabrique évite qu'il connaisse les classes concrètes —
 * il continue de ne dépendre que d'abstractions.
 *
 * Implémentation : tâche 2.6.
 */
public interface EtatMatchFabrique {
    EtatMatch depuis(StatutMatch statut);
}
