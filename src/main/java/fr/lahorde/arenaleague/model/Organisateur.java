package fr.lahorde.arenaleague.model;

/** Organisateur : tous les droits du périmètre. Peut aussi arbitrer. */
public final class Organisateur extends Utilisateur {

    public Organisateur(Long id, String login, String motDePasseHash) {
        super(id, login, motDePasseHash);
    }

    public Organisateur(String login, String motDePasseHash) {
        this(null, login, motDePasseHash);
    }

    @Override
    public Role role() {
        return Role.ORGANISATEUR;
    }

    @Override
    public boolean peutCreerTournoi() {
        return true;
    }

    @Override
    public boolean peutSaisirScore() {
        return true;
    }
}
