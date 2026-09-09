package fr.lahorde.arenaleague.model;

import java.util.Objects;

/**
 * Utilisateur authentifié — classe abstraite dont héritent Organisateur
 * et Arbitre.
 *
 * Les droits sont portés par des méthodes abstraites redéfinies, pas par un
 * test sur le rôle. Le service appelle peutCreerTournoi() sans savoir à
 * quelle sous-classe il parle : c'est du polymorphisme réel (RG-01, RG-02).
 *
 * Côté base, l'héritage est aplati : une seule table utilisateur avec une
 * colonne role. Aucun attribut propre à une sous-classe ne justifierait une
 * table par classe. Le repository fait la discrimination à la lecture.
 */
public abstract class Utilisateur {

    private Long id;
    private final String login;
    private final String motDePasseHash;

    protected Utilisateur(Long id, String login, String motDePasseHash) {
        this.id = id;
        this.login = Objects.requireNonNull(login, "login").trim();
        this.motDePasseHash = Objects.requireNonNull(motDePasseHash, "motDePasseHash");
        if (this.login.isEmpty()) {
            throw new IllegalArgumentException("Le login ne peut pas être vide");
        }
    }

    public Long id()               { return id; }
    public String login()          { return login; }
    public String motDePasseHash() { return motDePasseHash; }

    public void attribuerId(Long id) {
        if (this.id != null) {
            throw new IllegalStateException("L'identifiant est déjà attribué");
        }
        this.id = id;
    }

    /** Valeur persistée dans la colonne role. */
    public abstract Role role();

    /** RG-01 : seul l'Organisateur crée un tournoi, inscrit des équipes, génère les matchs. */
    public abstract boolean peutCreerTournoi();

    /** RG-02 : les deux rôles peuvent démarrer un match et saisir un score. */
    public abstract boolean peutSaisirScore();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Utilisateur autre)) return false;
        return login.equalsIgnoreCase(autre.login);
    }

    @Override
    public int hashCode() {
        return login.toLowerCase().hashCode();
    }

    @Override
    public String toString() {
        return login + " (" + role() + ")";
    }
}
