package fr.lahorde.arenaleague.model;

/**
 * Représentation persistée de l'état d'un match — colonne « etat » de la
 * table rencontre, contrainte par ck_rencontre_etat.
 *
 * Le pendant comportemental est l'interface EtatMatch et ses trois
 * implémentations (pattern State). La règle de partage est simple :
 *   - cet enum dit **dans quel état on est** — c'est ce qu'on écrit en base ;
 *   - l'objet EtatMatch dit **ce qu'on a le droit de faire** — c'est ce qui
 *     refuse les transitions interdites.
 *
 * Cycle de vie : PLANIFIE -> EN_COURS -> TERMINE (RG-50 à RG-53).
 * TERMINE est terminal.
 */
public enum StatutMatch {
    PLANIFIE,
    EN_COURS,
    TERMINE
}
