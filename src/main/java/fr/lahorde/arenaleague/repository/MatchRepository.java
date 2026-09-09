package fr.lahorde.arenaleague.repository;

import fr.lahorde.arenaleague.model.Match;

import java.util.List;
import java.util.Optional;

public interface MatchRepository {

    Optional<Match> parId(long id);

    List<Match> parTournoi(long tournoiId);

    /** RG-70 : seule source du classement. */
    List<Match> terminesParTournoi(long tournoiId);

    /** Insertion en lot — génération d'un tour complet (RG-32, RG-41). */
    List<Match> creerTous(long tournoiId, List<Match> matchs);

    /** Met à jour score, état et arbitre. */
    void mettreAJour(Match match);
}
