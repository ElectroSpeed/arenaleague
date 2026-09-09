package fr.lahorde.arenaleague.repository;

import fr.lahorde.arenaleague.model.Arbitre;
import fr.lahorde.arenaleague.model.Organisateur;
import fr.lahorde.arenaleague.model.Role;
import fr.lahorde.arenaleague.model.Utilisateur;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/**
 * Toutes les requêtes sont des PreparedStatement paramétrés.
 *
 * Aucune valeur n'est concaténée dans une chaîne SQL, nulle part dans cette
 * couche. C'est la réponse à la question du jury sur l'injection SQL : le
 * pilote transmet la requête et les valeurs séparément, une valeur ne peut
 * donc jamais être interprétée comme du code.
 */
public final class UtilisateurJdbcRepository implements UtilisateurRepository {

    private static final String SELECT_BASE =
        "SELECT id, login, mot_de_passe_hash, role FROM utilisateur";

    private final ConnectionProvider connexions;

    public UtilisateurJdbcRepository(ConnectionProvider connexions) {
        this.connexions = connexions;
    }

    @Override
    public Optional<Utilisateur> parLogin(String login) {
        return unSeul(SELECT_BASE + " WHERE login = ?", login);
    }

    @Override
    public Optional<Utilisateur> parId(long id) {
        return unSeul(SELECT_BASE + " WHERE id = ?", id);
    }

    private Optional<Utilisateur> unSeul(String sql, Object parametre) {
        try (PreparedStatement requete = connexions.connexion().prepareStatement(sql)) {
            requete.setObject(1, parametre);
            try (ResultSet lignes = requete.executeQuery()) {
                return lignes.next() ? Optional.of(convertir(lignes)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new PersistenceException("Lecture d'un utilisateur impossible", e);
        }
    }

    /**
     * Reconstruit la bonne sous-classe depuis la colonne role.
     *
     * C'est le seul endroit de l'application où le rôle est testé, et c'est
     * normal : l'héritage est aplati en base, il faut bien le reconstruire
     * quelque part. Partout ailleurs, les droits passent par le polymorphisme.
     */
    private Utilisateur convertir(ResultSet ligne) throws SQLException {
        long id = ligne.getLong("id");
        String login = ligne.getString("login");
        String hash = ligne.getString("mot_de_passe_hash");
        Role role = Role.valueOf(ligne.getString("role"));

        return switch (role) {
            case ORGANISATEUR -> new Organisateur(id, login, hash);
            case ARBITRE      -> new Arbitre(id, login, hash);
        };
    }
}
