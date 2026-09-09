package fr.lahorde.arenaleague.model.exception;

/**
 * Transition interdite dans le cycle de vie d'un match — RG-54.
 *
 * En particulier : saisir un score sur un match PLANIFIÉ (il faut le démarrer
 * d'abord) ou sur un match TERMINÉ (cas de démonstration CE-02, RG-53).
 *
 * Levée par l'état lui-même, pas par un test extérieur : c'est ce qui
 * distingue le pattern State d'un switch déguisé.
 */
public class TransitionInvalideException extends ArenaLeagueException {

    private static final long serialVersionUID = 1L;

    public TransitionInvalideException(String message) {
        super(message);
    }
}
