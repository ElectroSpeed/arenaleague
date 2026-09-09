package fr.lahorde.arenaleague.service;

import org.mindrot.jbcrypt.BCrypt;

/**
 * Implémentation BCrypt.
 *
 * BCrypt est volontairement lent — chaque vérification coûte une centaine de
 * millisecondes. Ce n'est pas un défaut : c'est ce qui rend une attaque par
 * force brute impraticable. Le sel est intégré à l'empreinte, il n'y a donc
 * pas de colonne séparée à gérer.
 *
 * Limite connue, à annoncer plutôt qu'à cacher : l'API de la bibliothèque
 * prend une String. On ne peut donc pas effacer cette copie temporaire de la
 * mémoire — les String sont immuables et restent jusqu'au passage du ramasse-
 * miettes. Ce qui est sous notre contrôle, le tableau de caractères venant de
 * l'IHM, est effacé (voir AuthService).
 */
public final class BCryptVerificateur implements VerificateurMotDePasse {

    @Override
    public boolean correspond(char[] clair, String empreinte) {
        if (clair == null || empreinte == null || empreinte.isBlank()) {
            return false;
        }
        return BCrypt.checkpw(new String(clair), empreinte);
    }

    /** Utilisé pour créer un compte. Coût 10 : compromis usuel sécurité / temps. */
    public static String hacher(char[] clair) {
        return BCrypt.hashpw(new String(clair), BCrypt.gensalt(10));
    }
}
