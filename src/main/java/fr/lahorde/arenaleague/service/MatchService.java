package fr.lahorde.arenaleague.service;

import fr.lahorde.arenaleague.model.*;
import fr.lahorde.arenaleague.model.exception.ValidationException;
import fr.lahorde.arenaleague.repository.MatchRepository;
import fr.lahorde.arenaleague.repository.TournoiRepository;
import fr.lahorde.arenaleague.repository.Transactions;
import fr.lahorde.arenaleague.service.format.FabriqueFormat;

import java.util.ArrayList;
import java.util.List;

/**
 * Démarrage des matchs et saisie des scores.
 *
 * **Ce service ne teste jamais l'état d'un match.** On n'y trouvera aucun
 * `if (statut == TERMINE)` : il appelle `match.demarrer()` ou
 * `match.saisirScore()`, et c'est l'état courant qui accepte ou refuse. Un
 * test sur l'état ici dupliquerait la machine à états et finirait par diverger.
 *
 * La méthode saisirScore suit exactement l'ordre de RG-63 et du diagramme de
 * séquence 1.7 :
 *
 *   1. contrôle de droits          (RG-02)
 *   2. chargement de l'agrégat
 *   3. validation selon le format  (RG-33, RG-60, RG-62) — Strategy
 *   4. transition d'état           (RG-52) — State
 *   5. persistance
 *   6. propagation du tour suivant (RG-34, RG-35)
 *   7. invalidation du classement  (RG-63) — Observer
 *
 * L'ensemble est dans une transaction (RG-64) : un échec en étape 6 annule la
 * saisie de l'étape 5. Le match ne peut pas rester clôturé sans que le tour
 * suivant existe.
 */
public final class MatchService {

    private final TournoiRepository tournois;
    private final MatchRepository matchs;
    private final FabriqueFormat formats;
    private final SessionContext session;
    private final Transactions connexions;
    private final List<EcouteurClassement> ecouteurs = new ArrayList<>();

    public MatchService(TournoiRepository tournois,
                        MatchRepository matchs,
                        FabriqueFormat formats,
                        SessionContext session,
                        Transactions connexions) {
        this.tournois = tournois;
        this.matchs = matchs;
        this.formats = formats;
        this.session = session;
        this.connexions = connexions;
    }

    /** L'IHM s'abonne ici pour rafraîchir le classement en direct. */
    public void abonner(EcouteurClassement ecouteur) {
        ecouteurs.add(ecouteur);
    }

    /**
     * RG-51 : PLANIFIÉ → EN COURS.
     *
     * Aucun test sur l'état : si le match n'est pas planifié, c'est l'objet
     * d'état qui lève TransitionInvalideException.
     */
    public Match demarrer(long tournoiId, long matchId) {
        Utilisateur operateur = session.exigerDroitSaisirScore();
        return connexions.enTransaction(() -> {
            Tournoi tournoi = charger(tournoiId);
            Match match = trouver(tournoi, matchId);

            match.demarrer();                       // RG-51, refus porté par l'état
            match.affecterArbitre(operateur);
            matchs.mettreAJour(match);
            return match;
        });
    }

    /**
     * RG-52, RG-63, RG-64 : saisie du score, clôture et propagation.
     *
     * La saisie du score **est** la clôture : il n'y a pas d'étape séparée.
     */
    public Match saisirScore(long tournoiId, long matchId, int scoreEquipeA, int scoreEquipeB) {
        Utilisateur operateur = session.exigerDroitSaisirScore();        // 1

        Match resultat = connexions.enTransaction(() -> {
            Tournoi tournoi = charger(tournoiId);                        // 2
            Match match = trouver(tournoi, matchId);
            FormatTournoi strategie = formats.pour(tournoi.format());

            Score score = new Score(scoreEquipeA, scoreEquipeB);         // RG-60
            strategie.validerScore(score);                               // 3 — RG-33, RG-62

            match.saisirScore(score);                                    // 4 — RG-52
            match.affecterArbitre(operateur);
            matchs.mettreAJour(match);                                   // 5

            List<Match> suivants = strategie.genererTourSuivant(tournoi); // 6 — RG-34, RG-35
            if (!suivants.isEmpty()) {
                tournoi.ajouterMatchs(suivants);
                matchs.creerTous(tournoiId, suivants);
            }
            return match;
        });

        notifier(tournoiId);                                             // 7 — RG-63
        return resultat;
    }

    /** RG-03 : consultation, sans distinction de rôle. */
    public List<Match> matchsDuTournoi(long tournoiId) {
        session.exigerConnecte();
        return connexions.enTransaction(() -> matchs.parTournoi(tournoiId));
    }

    /**
     * La notification a lieu **après** le commit, jamais pendant.
     *
     * Prévenir l'IHM à l'intérieur de la transaction lui ferait afficher un
     * classement qu'un rollback pourrait encore annuler. Et un abonné qui
     * échoue ne doit pas faire échouer une saisie déjà validée.
     */
    private void notifier(long tournoiId) {
        for (EcouteurClassement ecouteur : ecouteurs) {
            try {
                ecouteur.classementModifie(tournoiId);
            } catch (RuntimeException e) {
                System.err.println("Un abonné au classement a échoué : " + e.getMessage());
            }
        }
    }

    private Tournoi charger(long tournoiId) {
        return tournois.parId(tournoiId)
            .orElseThrow(() -> new ValidationException("Tournoi introuvable : " + tournoiId));
    }

    /**
     * Le match est cherché **dans l'agrégat**, pas directement en base.
     *
     * Deux raisons : cela garantit qu'il appartient bien à ce tournoi, et
     * l'objet retourné partage ses instances d'Equipe avec le reste du
     * tournoi — sans quoi le calcul de classement travaillerait sur des
     * doublons.
     */
    private Match trouver(Tournoi tournoi, long matchId) {
        return tournoi.matchs().stream()
            .filter(m -> m.id() != null && m.id() == matchId)
            .findFirst()
            .orElseThrow(() -> new ValidationException(
                "Match " + matchId + " introuvable dans le tournoi " + tournoi.nom()));
    }
}
