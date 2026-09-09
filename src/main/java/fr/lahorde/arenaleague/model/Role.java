package fr.lahorde.arenaleague.model;

/**
 * Représentation persistée du rôle — colonne « role » de la table utilisateur.
 *
 * Attention à ne pas se méprendre sur son usage : cet enum sert au mapping
 * base de données, **pas** à décider des droits. Les droits sont portés par
 * des méthodes abstraites redéfinies dans Organisateur et Arbitre (RG-01,
 * RG-02). On ne trouvera nulle part un « if (role == ORGANISATEUR) ».
 */
public enum Role {
    ORGANISATEUR,
    ARBITRE
}
