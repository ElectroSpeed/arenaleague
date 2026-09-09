package fr.lahorde.arenaleague.service.format;

import fr.lahorde.arenaleague.model.Format;
import fr.lahorde.arenaleague.model.FormatTournoi;

/**
 * Résout la stratégie à partir de la valeur persistée.
 *
 * Le switch est ici et seulement ici. Ajouter un troisième format
 * demanderait : une valeur dans l'enum, une classe qui implémente
 * FormatTournoi, une ligne ici. **Aucune modification de TournoiService,
 * MatchService ou ClassementService** — c'est exactement le test du pattern
 * Strategy demandé par la Definition of Done.
 */
public final class FabriqueFormat {

    public FormatTournoi pour(Format format) {
        return switch (format) {
            case ELIMINATION_DIRECTE -> new EliminationDirecte();
            case POULE               -> new Poule();
        };
    }
}
