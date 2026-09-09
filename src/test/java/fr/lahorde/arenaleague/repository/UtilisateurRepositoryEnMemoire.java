package fr.lahorde.arenaleague.repository;

import fr.lahorde.arenaleague.model.Utilisateur;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Implémentation en mémoire pour les tests.
 *
 * C'est tout l'intérêt du pattern Repository : la couche service se teste
 * sans base de données, sans Flyway et sans PostgreSQL. Les tests s'exécutent
 * en millisecondes et ne dépendent d'aucune installation.
 */
public final class UtilisateurRepositoryEnMemoire implements UtilisateurRepository {

    private final List<Utilisateur> comptes = new ArrayList<>();

    public UtilisateurRepositoryEnMemoire ajouter(Utilisateur utilisateur) {
        comptes.add(utilisateur);
        return this;
    }

    @Override
    public Optional<Utilisateur> parLogin(String login) {
        return comptes.stream()
                      .filter(u -> u.login().equalsIgnoreCase(login))
                      .findFirst();
    }

    @Override
    public Optional<Utilisateur> parId(long id) {
        return comptes.stream()
                      .filter(u -> u.id() != null && u.id() == id)
                      .findFirst();
    }
}
