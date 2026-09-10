package fr.lahorde.arenaleague.model;

/**
 * Représentation persistée du format — colonne « format » de la table tournoi.
 *
 * Le comportement associé vit dans les implémentations de FormatTournoi
 * (pattern Strategy). Cet enum ne sert qu'à savoir laquelle instancier.
 *
 * Chaque constante porte son libellé d'affichage. Ce n'est pas un test sur
 * le format — il n'y a ni switch ni condition — mais une donnée attachée à
 * la constante, au même titre que son nom. L'IHM le lit sans jamais avoir à
 * connaître les formats existants, et un format ajouté demain s'affiche
 * correctement sans qu'aucun écran ne change.
 *
 * L'alternative, dériver le libellé du nom de la constante, produisait
 * « Elimination directe » sans accent : le nom d'une constante Java ne peut
 * pas porter la typographie française.
 */
public enum Format {

    ELIMINATION_DIRECTE("Élimination directe"),
    POULE("Poule");

    private final String libelle;

    Format(String libelle) {
        this.libelle = libelle;
    }

    /** Libellé lisible, destiné à l'affichage. */
    public String libelle() {
        return libelle;
    }
}
