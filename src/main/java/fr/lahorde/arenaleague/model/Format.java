package fr.lahorde.arenaleague.model;

/**
 * Représentation persistée du format — colonne « format » de la table tournoi.
 *
 * Le comportement associé vit dans les implémentations de FormatTournoi
 * (pattern Strategy). Cet enum ne sert qu'à savoir laquelle instancier.
 */
public enum Format {
    ELIMINATION_DIRECTE,
    POULE
}
