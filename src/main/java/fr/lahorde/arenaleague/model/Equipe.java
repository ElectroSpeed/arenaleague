package fr.lahorde.arenaleague.model;

import fr.lahorde.arenaleague.model.exception.ValidationException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Une équipe et ses joueurs.
 *
 * Composition : un joueur n'existe pas hors de son équipe dans notre
 * périmètre, et supprimer une équipe supprime ses joueurs. C'est traduit
 * en base par ON DELETE CASCADE sur fk_joueur_equipe.
 */
public final class Equipe {

    /** RG-10 : une équipe compte entre 2 et 5 joueurs. */
    public static final int EFFECTIF_MIN = 2;
    public static final int EFFECTIF_MAX = 5;

    private Long id;
    private final String nom;
    private final List<Joueur> joueurs = new ArrayList<>();

    public Equipe(Long id, String nom) {
        this.id = id;
        this.nom = Objects.requireNonNull(nom, "nom").trim();
        if (this.nom.isEmpty()) {
            throw new IllegalArgumentException("Le nom d'équipe ne peut pas être vide");
        }
    }

    public Equipe(String nom) {
        this(null, nom);
    }

    public Long id()    { return id; }
    public String nom() { return nom; }

    public void attribuerId(Long id) {
        if (this.id != null) {
            throw new IllegalStateException("L'identifiant est déjà attribué");
        }
        this.id = id;
    }

    /** Vue non modifiable : la liste ne se modifie que par ajouterJoueur. */
    public List<Joueur> joueurs() {
        return Collections.unmodifiableList(joueurs);
    }

    public int effectif() {
        return joueurs.size();
    }

    /** RG-10 : plafond à 5 joueurs, vérifié à l'ajout. */
    public void ajouterJoueur(Joueur joueur) {
        Objects.requireNonNull(joueur, "joueur");
        if (joueurs.contains(joueur)) {
            throw new ValidationException(
                "Le joueur " + joueur.pseudo() + " est déjà dans l'équipe " + nom);
        }
        if (joueurs.size() >= EFFECTIF_MAX) {
            throw new ValidationException(
                "Une équipe compte au maximum " + EFFECTIF_MAX + " joueurs (RG-10)");
        }
        joueurs.add(joueur);
    }

    public void retirerJoueur(Joueur joueur) {
        joueurs.remove(joueur);
    }

    /**
     * RG-10 : l'effectif est valide entre 2 et 5.
     *
     * Cette borne est une contrainte d'agrégat : elle ne s'exprime pas en
     * CHECK de colonne, elle est donc portée ici et vérifiée par le service
     * au moment de l'inscription (RG-11). Limitation assumée, à annoncer.
     */
    public boolean effectifValide() {
        return effectif() >= EFFECTIF_MIN && effectif() <= EFFECTIF_MAX;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Equipe autre)) return false;
        return nom.equalsIgnoreCase(autre.nom);
    }

    @Override
    public int hashCode() {
        return nom.toLowerCase().hashCode();
    }

    @Override
    public String toString() {
        return nom;
    }
}
