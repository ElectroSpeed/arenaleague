package fr.lahorde.arenaleague.repository;

import fr.lahorde.arenaleague.model.Match;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Dépôt de matchs en mémoire, pour tester la couche service sans base. */
public final class DepotMatchEnMemoire implements MatchRepository {

    private final Map<Long, Match> matchs = new LinkedHashMap<>();
    private long sequence = 100;

    public Match get(long id) {
        return matchs.get(id);
    }

    @Override
    public Optional<Match> parId(long id) {
        return Optional.ofNullable(matchs.get(id));
    }

    @Override
    public List<Match> parTournoi(long tournoiId) {
        return new ArrayList<>(matchs.values());
    }

    @Override
    public List<Match> terminesParTournoi(long tournoiId) {
        return matchs.values().stream().filter(Match::estTermine).toList();
    }

    @Override
    public List<Match> creerTous(long tournoiId, List<Match> nouveaux) {
        for (Match match : nouveaux) {
            match.attribuerId(++sequence);
            matchs.put(match.id(), match);
        }
        return nouveaux;
    }

    @Override
    public void mettreAJour(Match match) {
        matchs.put(match.id(), match);
    }
}
