package fr.lahorde.arenaleague.model;

import java.util.Objects;

/**
 * Un joueur, membre d'une seule équipe (RG-12).
 *
 * Le pseudo est unique dans toute l'application (RG-13) — l'unicité est
 * garantie côté base par uq_joueur_pseudo.
 */
public final class Joueur {

    private Long id;
    private final String pseudo;

    public Joueur(Long id, String pseudo) {
        this.id = id;
        this.pseudo = Objects.requireNonNull(pseudo, "pseudo").trim();
        if (this.pseudo.isEmpty()) {
            throw new IllegalArgumentException("Le pseudo ne peut pas être vide");
        }
    }

    public Joueur(String pseudo) {
        this(null, pseudo);
    }

    public Long id()        { return id; }
    public String pseudo()  { return pseudo; }

    /** Appelé par le repository après insertion. */
    public void attribuerId(Long id) {
        if (this.id != null) {
            throw new IllegalStateException("L'identifiant est déjà attribué");
        }
        this.id = id;
    }

    /**
     * Égalité par pseudo, pas par identifiant : un joueur qui n'a pas encore
     * été inséré n'a pas d'id, et deux objets décrivant le même joueur
     * doivent malgré tout être égaux.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Joueur autre)) return false;
        return pseudo.equalsIgnoreCase(autre.pseudo);
    }

    @Override
    public int hashCode() {
        return pseudo.toLowerCase().hashCode();
    }

    @Override
    public String toString() {
        return pseudo;
    }
}
