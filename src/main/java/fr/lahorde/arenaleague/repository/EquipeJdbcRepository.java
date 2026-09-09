package fr.lahorde.arenaleague.repository;

import fr.lahorde.arenaleague.model.Equipe;
import fr.lahorde.arenaleague.model.Joueur;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class EquipeJdbcRepository implements EquipeRepository {

    private static final String INSERT_EQUIPE =
        "INSERT INTO equipe (nom) VALUES (?)";
    private static final String INSERT_JOUEUR =
        "INSERT INTO joueur (pseudo, equipe_id) VALUES (?, ?)";
    private static final String SELECT_PAR_ID =
        "SELECT id, nom FROM equipe WHERE id = ?";
    private static final String SELECT_PAR_NOM =
        "SELECT id, nom FROM equipe WHERE nom = ?";
    private static final String SELECT_TOUTES =
        "SELECT id, nom FROM equipe ORDER BY nom";
    private static final String SELECT_INSCRITES =
        "SELECT e.id, e.nom FROM equipe e "
      + "JOIN inscription i ON i.equipe_id = e.id "
      + "WHERE i.tournoi_id = ? ORDER BY e.nom";
    private static final String SELECT_JOUEURS =
        "SELECT id, pseudo FROM joueur WHERE equipe_id = ? ORDER BY pseudo";

    private final ConnectionProvider connexions;

    public EquipeJdbcRepository(ConnectionProvider connexions) {
        this.connexions = connexions;
    }

    /**
     * Insère l'équipe puis ses joueurs.
     *
     * Deux tables, donc une opération qui doit être atomique : sans
     * transaction, un échec sur le second joueur laisserait une équipe
     * incomplète en base, ce que RG-10 interdit. La transaction est déjà
     * ouverte par le service — d'où l'exigence de ConnectionProvider.
     */
    @Override
    public Equipe creer(Equipe equipe) {
        try (PreparedStatement requete = connexions.connexion()
                .prepareStatement(INSERT_EQUIPE, Statement.RETURN_GENERATED_KEYS)) {

            requete.setString(1, equipe.nom());
            requete.executeUpdate();

            try (ResultSet cles = requete.getGeneratedKeys()) {
                if (!cles.next()) {
                    throw new PersistenceException("Aucun identifiant généré pour l'équipe " + equipe.nom());
                }
                equipe.attribuerId(cles.getLong(1));
            }
        } catch (SQLException e) {
            throw new PersistenceException("Création de l'équipe " + equipe.nom() + " impossible", e);
        }

        for (Joueur joueur : equipe.joueurs()) {
            insererJoueur(joueur, equipe.id());
        }
        return equipe;
    }

    private void insererJoueur(Joueur joueur, long equipeId) {
        try (PreparedStatement requete = connexions.connexion()
                .prepareStatement(INSERT_JOUEUR, Statement.RETURN_GENERATED_KEYS)) {

            requete.setString(1, joueur.pseudo());
            requete.setLong(2, equipeId);
            requete.executeUpdate();

            try (ResultSet cles = requete.getGeneratedKeys()) {
                if (cles.next()) {
                    joueur.attribuerId(cles.getLong(1));
                }
            }
        } catch (SQLException e) {
            throw new PersistenceException("Création du joueur " + joueur.pseudo() + " impossible", e);
        }
    }

    @Override
    public Optional<Equipe> parId(long id) {
        return uneSeule(SELECT_PAR_ID, id);
    }

    @Override
    public Optional<Equipe> parNom(String nom) {
        return uneSeule(SELECT_PAR_NOM, nom);
    }

    private Optional<Equipe> uneSeule(String sql, Object parametre) {
        try (PreparedStatement requete = connexions.connexion().prepareStatement(sql)) {
            requete.setObject(1, parametre);
            try (ResultSet lignes = requete.executeQuery()) {
                if (!lignes.next()) return Optional.empty();
                Equipe equipe = new Equipe(lignes.getLong("id"), lignes.getString("nom"));
                chargerJoueurs(equipe);
                return Optional.of(equipe);
            }
        } catch (SQLException e) {
            throw new PersistenceException("Lecture d'une équipe impossible", e);
        }
    }

    @Override
    public List<Equipe> toutes() {
        return liste(SELECT_TOUTES, null);
    }

    @Override
    public List<Equipe> inscritesA(long tournoiId) {
        return liste(SELECT_INSCRITES, tournoiId);
    }

    private List<Equipe> liste(String sql, Long parametre) {
        List<Equipe> equipes = new ArrayList<>();
        try (PreparedStatement requete = connexions.connexion().prepareStatement(sql)) {
            if (parametre != null) {
                requete.setLong(1, parametre);
            }
            try (ResultSet lignes = requete.executeQuery()) {
                while (lignes.next()) {
                    equipes.add(new Equipe(lignes.getLong("id"), lignes.getString("nom")));
                }
            }
        } catch (SQLException e) {
            throw new PersistenceException("Lecture des équipes impossible", e);
        }
        for (Equipe equipe : equipes) {
            chargerJoueurs(equipe);
        }
        return equipes;
    }

    private void chargerJoueurs(Equipe equipe) {
        try (PreparedStatement requete = connexions.connexion().prepareStatement(SELECT_JOUEURS)) {
            requete.setLong(1, equipe.id());
            try (ResultSet lignes = requete.executeQuery()) {
                while (lignes.next()) {
                    equipe.ajouterJoueur(new Joueur(lignes.getLong("id"), lignes.getString("pseudo")));
                }
            }
        } catch (SQLException e) {
            throw new PersistenceException("Lecture des joueurs de " + equipe.nom() + " impossible", e);
        }
    }
}
