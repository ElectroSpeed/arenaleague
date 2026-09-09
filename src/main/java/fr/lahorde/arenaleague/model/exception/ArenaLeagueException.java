package fr.lahorde.arenaleague.model.exception;

/**
 * Racine des exceptions métier.
 *
 * Le contrôleur ne connaît que cette classe : il attrape ArenaLeagueException
 * et affiche son message. Il n'a pas à distinguer les sous-types.
 *
 * Non cochée volontairement : ces exceptions signalent une règle violée, pas
 * une panne récupérable. L'appelant ne doit pas être forcé de les attraper à
 * chaque étage — elles remontent jusqu'au contrôleur, qui annule la
 * transaction au passage (RG-64).
 */
public abstract class ArenaLeagueException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    protected ArenaLeagueException(String message) {
        super(message);
    }
}
