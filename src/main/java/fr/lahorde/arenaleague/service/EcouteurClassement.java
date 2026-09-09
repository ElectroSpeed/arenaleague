package fr.lahorde.arenaleague.service;

/**
 * Pattern Observer — notification d'un classement devenu obsolète.
 *
 * RG-63, quatrième effet de la saisie d'un score. Le service ne connaît pas
 * ses abonnés : il annonce qu'un classement a changé, sans savoir si quelqu'un
 * écoute ni ce qu'il en fera.
 *
 * C'est ce qui permet à l'IHM de rafraîchir le tableau en direct sans que la
 * couche service dépende de JavaFX — la règle d'étanchéité des couches tient
 * précisément grâce à cette inversion.
 */
@FunctionalInterface
public interface EcouteurClassement {

    void classementModifie(long tournoiId);
}
