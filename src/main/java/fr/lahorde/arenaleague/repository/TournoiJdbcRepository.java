package fr.lahorde.arenaleague.repository;

import fr.lahorde.arenaleague.model.Equipe;
import fr.lahorde.arenaleague.model.Format;
import fr.lahorde.arenaleague.model.Match;
import fr.lahorde.arenaleague.model.Tournoi;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class TournoiJdbcRepository implements TournoiRepository {

    private static final String INSERT =
        "INSERT INTO tournoi (nom, date_debut, format, demarre) VALUES (?, ?, ?, ?)";
    private static final String SELECT_PAR_ID =
        "SELECT id, nom, date_debut, format, demarre FROM tournoi WHERE id = ?";
    private static final String SELECT_TOUS =
        "SELECT id, nom, date_debut, format, demarre FROM tournoi ORDER BY date_debut DESC, nom";
    private static final String INSERT_INSCRIPTION =
        "INSERT INTO inscription (tournoi_id, equipe_id) VALUES (?, ?)";
    private static final String DELETE_INSCRIPTION =
        "DELETE FROM inscription WHERE tournoi_id = ? AND equipe_id = ?";
    private static final String UPDATE_DEMARRE =
        "UPDATE tournoi SET demarre = TRUE WHERE id = ? AND demarre = FALSE";

    private final ConnectionProvider connexions;
    private final EquipeRepository equipes;
    private final MatchRepository matchs;

    public TournoiJdbcRepository(ConnectionProvider connexions,
                                 EquipeRepository equipes,
                                 MatchRepository matchs) {
        this.connexions = connexions;
        this.equipes = equipes;
        this.matchs = matchs;
    }

    @Override
    public Tournoi creer(Tournoi tournoi) {
        try (PreparedStatement requete = connexions.connexion()
                .prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {

            requete.setString(1, tournoi.nom());
            requete.setDate(2, Date.valueOf(tournoi.dateDebut()));
            requete.setString(3, tournoi.format().name());
            requete.setBoolean(4, tournoi.estDemarre());
            requete.executeUpdate();

            try (ResultSet cles = requete.getGeneratedKeys()) {
                if (!cles.next()) {
                    throw new PersistenceException("Aucun identifiant généré pour le tournoi " + tournoi.nom());
                }
                tournoi.attribuerId(cles.getLong(1));
            }
            return tournoi;

        } catch (SQLException e) {
            throw new PersistenceException("Création du tournoi " + tournoi.nom() + " impossible", e);
        }
    }

    @Override
    public Optional<Tournoi> parId(long id) {
        Tournoi tournoi;
        try (PreparedStatement requete = connexions.connexion().prepareStatement(SELECT_PAR_ID)) {
            requete.setLong(1, id);
            try (ResultSet lignes = requete.executeQuery()) {
                if (!lignes.next()) return Optional.empty();
                tournoi = convertir(lignes);
            }
        } catch (SQLException e) {
            throw new PersistenceException("Lecture du tournoi " + id + " impossible", e);
        }

        // Agrégat complet : équipes inscrites et matchs.
        List<Equipe> inscrites = equipes.inscritesA(id);
        List<Match> rencontres = matchs.parTournoi(id);
        tournoi.restaurer(inscrites, rencontres);
        return Optional.of(tournoi);
    }

    @Override
    public List<Tournoi> tous() {
        List<Tournoi> resultat = new ArrayList<>();
        try (PreparedStatement requete = connexions.connexion().prepareStatement(SELECT_TOUS);
             ResultSet lignes = requete.executeQuery()) {
            while (lignes.next()) {
                resultat.add(convertir(lignes));
            }
        } catch (SQLException e) {
            throw new PersistenceException("Lecture des tournois impossible", e);
        }
        return resultat;
    }

    @Override
    public void inscrire(long tournoiId, long equipeId) {
        executer(INSERT_INSCRIPTION, tournoiId, equipeId,
                 "Inscription de l'équipe " + equipeId + " au tournoi " + tournoiId + " impossible");
    }

    @Override
    public void retirer(long tournoiId, long equipeId) {
        executer(DELETE_INSCRIPTION, tournoiId, equipeId,
                 "Retrait de l'équipe " + equipeId + " du tournoi " + tournoiId + " impossible");
    }

    private void executer(String sql, long premier, long second, String messageErreur) {
        try (PreparedStatement requete = connexions.connexion().prepareStatement(sql)) {
            requete.setLong(1, premier);
            requete.setLong(2, second);
            requete.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException(messageErreur, e);
        }
    }

    /**
     * RG-22 : la clause « AND demarre = FALSE » rend le démarrage idempotent
     * au niveau du SGBD. Même si deux appels arrivaient simultanément, le
     * second ne modifierait aucune ligne — la garantie ne repose pas
     * uniquement sur le contrôle applicatif.
     */
    @Override
    public void marquerDemarre(long tournoiId) {
        try (PreparedStatement requete = connexions.connexion().prepareStatement(UPDATE_DEMARRE)) {
            requete.setLong(1, tournoiId);
            if (requete.executeUpdate() == 0) {
                throw new PersistenceException(
                    "Le tournoi " + tournoiId + " est introuvable ou déjà démarré (RG-22)");
            }
        } catch (SQLException e) {
            throw new PersistenceException("Démarrage du tournoi " + tournoiId + " impossible", e);
        }
    }

    private Tournoi convertir(ResultSet ligne) throws SQLException {
        return new Tournoi(
            ligne.getLong("id"),
            ligne.getString("nom"),
            ligne.getDate("date_debut").toLocalDate(),
            Format.valueOf(ligne.getString("format")),
            ligne.getBoolean("demarre"));
    }
}
