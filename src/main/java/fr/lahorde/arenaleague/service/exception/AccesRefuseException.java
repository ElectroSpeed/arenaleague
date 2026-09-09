package fr.lahorde.arenaleague.service.exception;

import fr.lahorde.arenaleague.model.exception.ArenaLeagueException;

/**
 * L'utilisateur connecté n'a pas le droit d'effectuer cette action — RG-01, RG-02.
 *
 * Cas de démonstration CE-01.
 *
 * Cette exception vit dans la couche service, contrairement aux exceptions du
 * domaine : le contrôle de rôle est une décision applicative, pas un invariant
 * métier. Une entité Match n'a pas à savoir qui a le droit de la modifier.
 */
public class AccesRefuseException extends ArenaLeagueException {

    private static final long serialVersionUID = 1L;

    public AccesRefuseException(String message) {
        super(message);
    }
}
