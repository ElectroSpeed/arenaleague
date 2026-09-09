package fr.lahorde.arenaleague.service;

/**
 * Vérification d'un mot de passe contre son empreinte.
 *
 * Abstraite derrière une interface pour deux raisons :
 *   - la couche service ne dépend pas d'une bibliothèque de hachage précise ;
 *   - les tests s'exécutent sans BCrypt, dont chaque vérification coûte
 *     volontairement une centaine de millisecondes.
 */
public interface VerificateurMotDePasse {

    /**
     * @param clair     mot de passe saisi
     * @param empreinte valeur stockée en base
     */
    boolean correspond(char[] clair, String empreinte);
}
