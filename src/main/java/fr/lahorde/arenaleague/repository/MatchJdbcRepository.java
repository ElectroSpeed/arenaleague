package fr.lahorde.arenaleague.repository;

import fr.lahorde.arenaleague.model.Equipe;
import fr.lahorde.arenaleague.model.Match;
import fr.lahorde.arenaleague.model.Score;
import fr.lahorde.arenaleague.model.StatutMatch;
import fr.lahorde.arenaleague.model.etat.EtatMatchFabrique;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * La table s'appelle « rencontre » : MATCH est un mot réservé SQL:2003.
 *
 * Le repository ne connaît pas les classes d'état concrètes. Il lit une
 * chaîne, la convertit en StatutMatch, et demande l'objet correspondant à
 * une fabrique injectée. Il continue donc de ne dépendre que d'abstractions.
 */
public final class MatchJdbcRepository implements MatchRepository {

    private static final String SELECT_BASE =
        "SELECT r.id, r.tour, r.equipe_a_id, r.equipe_b_id, r.score_a, r.score_b, r.etat, "
      + "       a.nom AS nom_a, b.nom AS nom_b "
      + "FROM rencontre r "
      + "JOIN equipe a ON a.id = r.equipe_a_id "
      + "JOIN equipe b ON b.id = r.equipe_b_id ";

    private static final String SELECT_PAR_ID     = SELECT_BASE + "WHERE r.id = ?";
    private static final String SELECT_PAR_TOURNOI= SELECT_BASE + "WHERE r.tournoi_id = ? ORDER BY r.tour, r.id";
    private static final String SELECT_TERMINES   = SELECT_BASE + "WHERE r.tournoi_id = ? AND r.etat = ? ORDER BY r.tour, r.id";

    private static final String INSERT =
        "INSERT INTO rencontre (tournoi_id, tour, equipe_a_id, equipe_b_id, etat) VALUES (?, ?, ?, ?, ?)";

    private static final String UPDATE =
        "UPDATE rencontre SET score_a = ?, score_b = ?, etat = ?, arbitre_id = ? WHERE id = ?";

    private final ConnectionProvider connexions;
    private final EtatMatchFabrique etats;

    public MatchJdbcRepository(ConnectionProvider connexions, EtatMatchFabrique etats) {
        this.connexions = connexions;
        this.etats = etats;
    }

    @Override
    public Optional<Match> parId(long id) {
        try (PreparedStatement requete = connexions.connexion().prepareStatement(SELECT_PAR_ID)) {
            requete.setLong(1, id);
            try (ResultSet lignes = requete.executeQuery()) {
                return lignes.next() ? Optional.of(convertir(lignes, new HashMap<>())) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new PersistenceException("Lecture du match " + id + " impossible", e);
        }
    }

    @Override
    public List<Match> parTournoi(long tournoiId) {
        return liste(SELECT_PAR_TOURNOI, tournoiId, null);
    }

    @Override
    public List<Match> terminesParTournoi(long tournoiId) {
        return liste(SELECT_TERMINES, tournoiId, StatutMatch.TERMINE.name());
    }

    private List<Match> liste(String sql, long tournoiId, String statut) {
        List<Match> matchs = new ArrayList<>();
        // Une même équipe revient dans plusieurs matchs : on la réutilise pour
        // que equals par identité fonctionne côté calcul de classement.
        Map<Long, Equipe> cache = new HashMap<>();
        try (PreparedStatement requete = connexions.connexion().prepareStatement(sql)) {
            requete.setLong(1, tournoiId);
            if (statut != null) {
                requete.setString(2, statut);
            }
            try (ResultSet lignes = requete.executeQuery()) {
                while (lignes.next()) {
                    matchs.add(convertir(lignes, cache));
                }
            }
        } catch (SQLException e) {
            throw new PersistenceException("Lecture des matchs du tournoi " + tournoiId + " impossible", e);
        }
        return matchs;
    }

    @Override
    public List<Match> creerTous(long tournoiId, List<Match> matchs) {
        try (PreparedStatement requete = connexions.connexion()
                .prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {

            for (Match match : matchs) {
                requete.setLong(1, tournoiId);
                requete.setInt(2, match.tour());
                requete.setLong(3, match.equipeA().id());
                requete.setLong(4, match.equipeB().id());
                requete.setString(5, match.statut().name());
                requete.addBatch();
            }
            requete.executeBatch();

            try (ResultSet cles = requete.getGeneratedKeys()) {
                int index = 0;
                while (cles.next() && index < matchs.size()) {
                    matchs.get(index++).attribuerId(cles.getLong(1));
                }
            }
        } catch (SQLException e) {
            throw new PersistenceException("Création des matchs du tournoi " + tournoiId + " impossible", e);
        }
        return matchs;
    }

    @Override
    public void mettreAJour(Match match) {
        try (PreparedStatement requete = connexions.connexion().prepareStatement(UPDATE)) {
            Score score = match.score();
            // RG-83 : le score reste NULL tant que le match n'est pas TERMINE.
            if (score == null) {
                requete.setNull(1, Types.INTEGER);
                requete.setNull(2, Types.INTEGER);
            } else {
                requete.setInt(1, score.equipeA());
                requete.setInt(2, score.equipeB());
            }
            requete.setString(3, match.statut().name());
            if (match.arbitre() == null || match.arbitre().id() == null) {
                requete.setNull(4, Types.BIGINT);
            } else {
                requete.setLong(4, match.arbitre().id());
            }
            requete.setLong(5, match.id());

            if (requete.executeUpdate() == 0) {
                throw new PersistenceException("Aucun match mis à jour pour l'identifiant " + match.id());
            }
        } catch (SQLException e) {
            throw new PersistenceException("Mise à jour du match " + match.id() + " impossible", e);
        }
    }

    private Match convertir(ResultSet ligne, Map<Long, Equipe> cache) throws SQLException {
        Equipe equipeA = equipe(cache, ligne.getLong("equipe_a_id"), ligne.getString("nom_a"));
        Equipe equipeB = equipe(cache, ligne.getLong("equipe_b_id"), ligne.getString("nom_b"));

        StatutMatch statut = StatutMatch.valueOf(ligne.getString("etat"));
        Match match = new Match(ligne.getLong("id"), ligne.getInt("tour"),
                                equipeA, equipeB, etats.depuis(statut));

        int scoreA = ligne.getInt("score_a");
        boolean scoreAbsent = ligne.wasNull();
        int scoreB = ligne.getInt("score_b");
        if (!scoreAbsent && !ligne.wasNull()) {
            match.restaurerScore(new Score(scoreA, scoreB));
        }
        return match;
    }

    private Equipe equipe(Map<Long, Equipe> cache, long id, String nom) {
        return cache.computeIfAbsent(id, cle -> new Equipe(cle, nom));
    }
}
