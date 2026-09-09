package fr.lahorde.arenaleague.repository;

import fr.lahorde.arenaleague.model.exception.ArenaLeagueException;

/**
 * Traduction d'une SQLException en exception métier.
 *
 * Aucune SQLException ne franchit la frontière de la couche repository. Le
 * service et le contrôleur n'ont pas à connaître JDBC — c'est la condition
 * pour pouvoir remplacer l'implémentation JDBC par une implémentation en
 * mémoire dans les tests, sans rien changer au-dessus.
 *
 * La cause d'origine est conservée : on ne perd pas la trace technique, on
 * la met simplement hors du chemin des couches hautes.
 */
public class PersistenceException extends ArenaLeagueException {

    private static final long serialVersionUID = 1L;

    public PersistenceException(String message, Throwable cause) {
        super(message, cause);
    }

    public PersistenceException(String message) {
        super(message);
    }
}
