package fr.lahorde.arenaleague.repository;

import fr.lahorde.arenaleague.model.Tournoi;

import java.util.List;
import java.util.Optional;

public interface TournoiRepository {

    Tournoi creer(Tournoi tournoi);

    /** Charge le tournoi avec ses équipes inscrites et ses matchs. */
    Optional<Tournoi> parId(long id);

    List<Tournoi> tous();

    void inscrire(long tournoiId, long equipeId);

    void retirer(long tournoiId, long equipeId);

    /** RG-22 : bascule irréversible du drapeau demarre. */
    void marquerDemarre(long tournoiId);
}
