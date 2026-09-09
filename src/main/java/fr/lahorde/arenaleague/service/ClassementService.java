package fr.lahorde.arenaleague.service;

import fr.lahorde.arenaleague.model.FormatTournoi;
import fr.lahorde.arenaleague.model.LigneClassement;
import fr.lahorde.arenaleague.model.Tournoi;
import fr.lahorde.arenaleague.model.exception.ValidationException;
import fr.lahorde.arenaleague.repository.TournoiRepository;
import fr.lahorde.arenaleague.repository.Transactions;
import fr.lahorde.arenaleague.service.format.FabriqueFormat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Classement d'un tournoi — RG-70 à RG-76.
 *
 * **Le classement n'est jamais stocké en base.** Il est entièrement dérivable
 * des matchs terminés (RG-70). Une table de classement serait une duplication
 * qu'il faudrait maintenir cohérente à chaque saisie, avec le risque classique
 * de la voir diverger de la réalité des scores.
 *
 * Ce service est le point de rendez-vous du pattern Observer :
 *   - il **écoute** MatchService, qui le prévient qu'un score a changé ;
 *   - il **notifie** ses propres abonnés, dont l'IHM en 2.12.
 *
 * Entre les deux, il vide son cache. L'IHM ne sait donc pas d'où vient la
 * mise à jour, et le service ne sait pas qui l'écoute : aucune dépendance
 * vers JavaFX n'est nécessaire.
 *
 * Les critères de départage eux-mêmes ne sont pas ici mais dans les
 * implémentations de FormatTournoi : ils diffèrent selon le format (RG-71 à
 * RG-75 en poule, RG-76 en élimination directe). Les placer ici obligerait à
 * y tester le format, ce que le pattern Strategy est censé éviter.
 */
public final class ClassementService implements EcouteurClassement {

    private final TournoiRepository tournois;
    private final FabriqueFormat formats;
    private final SessionContext session;
    private final Transactions connexions;

    /** Classement calculé, valable jusqu'à la prochaine saisie de score. */
    private final Map<Long, List<LigneClassement>> cache = new ConcurrentHashMap<>();

    private final List<EcouteurClassement> abonnes = new ArrayList<>();

    public ClassementService(TournoiRepository tournois,
                             FabriqueFormat formats,
                             SessionContext session,
                             Transactions connexions) {
        this.tournois = tournois;
        this.formats = formats;
        this.session = session;
        this.connexions = connexions;
    }

    /** L'IHM s'abonne ici pour rafraîchir son tableau (tâche 2.12). */
    public void abonner(EcouteurClassement abonne) {
        abonnes.add(abonne);
    }

    /**
     * RG-70 : classement calculé à la demande.
     *
     * Le cache évite de recalculer à chaque rafraîchissement d'écran, pas de
     * stocker un résultat durable. Il est vidé dès qu'un score change, donc
     * il ne peut pas afficher une valeur périmée.
     */
    public List<LigneClassement> pourTournoi(long tournoiId) {
        session.exigerConnecte();
        return cache.computeIfAbsent(tournoiId, this::calculer);
    }

    /** Force le recalcul, en ignorant le cache. */
    public List<LigneClassement> recalculer(long tournoiId) {
        session.exigerConnecte();
        List<LigneClassement> classement = calculer(tournoiId);
        cache.put(tournoiId, classement);
        return classement;
    }

    /**
     * RG-63, quatrième effet : appelé par MatchService après une saisie.
     *
     * L'ordre compte. On vide d'abord, on notifie ensuite : un abonné qui
     * redemande immédiatement le classement doit obtenir la version à jour,
     * pas celle qu'on s'apprêtait à supprimer.
     */
    @Override
    public void classementModifie(long tournoiId) {
        cache.remove(tournoiId);
        for (EcouteurClassement abonne : abonnes) {
            try {
                abonne.classementModifie(tournoiId);
            } catch (RuntimeException e) {
                System.err.println("Un abonné au classement a échoué : " + e.getMessage());
            }
        }
    }

    /** Vide tout le cache — utile après un rechargement complet des données. */
    public void invaliderTout() {
        cache.clear();
    }

    private List<LigneClassement> calculer(long tournoiId) {
        return connexions.enTransaction(() -> {
            Tournoi tournoi = tournois.parId(tournoiId)
                .orElseThrow(() -> new ValidationException("Tournoi introuvable : " + tournoiId));
            FormatTournoi strategie = formats.pour(tournoi.format());
            return strategie.calculerClassement(tournoi);
        });
    }
}
