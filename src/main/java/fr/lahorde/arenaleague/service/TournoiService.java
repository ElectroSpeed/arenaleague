package fr.lahorde.arenaleague.service;

import fr.lahorde.arenaleague.model.Format;
import fr.lahorde.arenaleague.model.Tournoi;
import fr.lahorde.arenaleague.repository.Transactions;
import fr.lahorde.arenaleague.repository.TournoiRepository;

import java.time.LocalDate;
import java.util.List;

/**
 * Cycle de vie d'un tournoi.
 *
 * Version partielle : seule la création est implémentée ici, parce que le
 * contrôle de droits qui l'accompagne fait partie de la tâche 2.4.
 * L'inscription des équipes, le démarrage et la génération des matchs
 * arrivent avec la tâche 2.5 et la Strategy de format.
 */
public final class TournoiService {

    private final TournoiRepository tournois;
    private final SessionContext session;
    private final Transactions connexions;

    public TournoiService(TournoiRepository tournois,
                          SessionContext session,
                          Transactions connexions) {
        this.tournois = tournois;
        this.session = session;
        this.connexions = connexions;
    }

    /**
     * RG-01 : réservé à l'Organisateur.
     *
     * Le contrôle est la **première instruction**, avant toute lecture ou
     * écriture. Un refus ne doit laisser aucune trace : ni ligne créée, ni
     * séquence consommée. C'est ce que la démonstration CE-01 doit montrer.
     */
    public Tournoi creerTournoi(String nom, LocalDate dateDebut, Format format) {
        session.exigerDroitCreerTournoi();
        return connexions.enTransaction(() -> tournois.creer(new Tournoi(nom, dateDebut, format)));
    }

    /** RG-03 : la consultation exige d'être connecté, sans distinction de rôle. */
    public List<Tournoi> lister() {
        session.exigerConnecte();
        return connexions.enTransaction(tournois::tous);
    }

    public Tournoi parId(long id) {
        session.exigerConnecte();
        return connexions.enTransaction(() -> tournois.parId(id))
            .orElseThrow(() -> new IllegalArgumentException("Tournoi introuvable : " + id));
    }
}
