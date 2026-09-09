package fr.lahorde.arenaleague.repository;

import fr.lahorde.arenaleague.model.Equipe;

import java.util.List;
import java.util.Optional;

public interface EquipeRepository {

    /** Insère l'équipe et ses joueurs, puis renseigne les identifiants générés. */
    Equipe creer(Equipe equipe);

    Optional<Equipe> parId(long id);

    Optional<Equipe> parNom(String nom);

    List<Equipe> toutes();

    /** Équipes inscrites à un tournoi, joueurs chargés. */
    List<Equipe> inscritesA(long tournoiId);
}
