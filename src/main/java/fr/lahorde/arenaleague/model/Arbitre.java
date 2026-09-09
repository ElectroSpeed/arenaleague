package fr.lahorde.arenaleague.model;

/**
 * Arbitre : démarre les matchs et saisit les scores, rien d'autre.
 *
 * Le refus de peutCreerTournoi() est le cas de démonstration CE-01.
 */
public final class Arbitre extends Utilisateur {

    public Arbitre(Long id, String login, String motDePasseHash) {
        super(id, login, motDePasseHash);
    }

    public Arbitre(String login, String motDePasseHash) {
        this(null, login, motDePasseHash);
    }

    @Override
    public Role role() {
        return Role.ARBITRE;
    }

    @Override
    public boolean peutCreerTournoi() {
        return false;
    }

    @Override
    public boolean peutSaisirScore() {
        return true;
    }
}
