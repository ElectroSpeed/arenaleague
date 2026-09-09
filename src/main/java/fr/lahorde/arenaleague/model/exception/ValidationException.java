package fr.lahorde.arenaleague.model.exception;

/**
 * Une donnée fournie ne respecte pas une règle de gestion.
 *
 * Couvre les cas de démonstration CE-03 (score négatif), CE-04 (égalité
 * interdite en élimination directe) et CE-05 (équipe incomplète).
 */
public class ValidationException extends ArenaLeagueException {

    private static final long serialVersionUID = 1L;

    public ValidationException(String message) {
        super(message);
    }
}
