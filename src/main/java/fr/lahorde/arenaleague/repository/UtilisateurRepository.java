package fr.lahorde.arenaleague.repository;

import fr.lahorde.arenaleague.model.Utilisateur;

import java.util.Optional;

/**
 * Accès aux comptes.
 *
 * Le service ne connaît que cette interface. En test, on lui passe une
 * implémentation en mémoire et aucune base n'est nécessaire.
 */
public interface UtilisateurRepository {

    Optional<Utilisateur> parLogin(String login);

    Optional<Utilisateur> parId(long id);
}
