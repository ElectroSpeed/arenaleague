package fr.lahorde.arenaleague.service;

import fr.lahorde.arenaleague.model.*;
import fr.lahorde.arenaleague.model.exception.ValidationException;
import fr.lahorde.arenaleague.repository.*;
import fr.lahorde.arenaleague.service.format.FabriqueFormat;

import java.time.LocalDate;
import java.util.List;

/**
 * Cycle de vie d'un tournoi : création, inscriptions, démarrage.
 *
 * **Ce service ne contient aucun test sur le format.** Il demande une
 * stratégie à la fabrique et lui délègue tout ce qui dépend du format :
 * validation de l'effectif, génération des matchs, calcul du classement.
 * Ajouter un troisième format ne toucherait pas une ligne de cette classe —
 * c'est le critère de réussite du pattern inscrit dans la Definition of Done.
 */
public final class TournoiService {

    private final TournoiRepository tournois;
    private final EquipeRepository equipes;
    private final MatchRepository matchs;
    private final FabriqueFormat formats;
    private final SessionContext session;
    private final Transactions connexions;

    public TournoiService(TournoiRepository tournois,
                          EquipeRepository equipes,
                          MatchRepository matchs,
                          FabriqueFormat formats,
                          SessionContext session,
                          Transactions connexions) {
        this.tournois = tournois;
        this.equipes = equipes;
        this.matchs = matchs;
        this.formats = formats;
        this.session = session;
        this.connexions = connexions;
    }

    /**
     * RG-01 : réservé à l'Organisateur.
     *
     * Le contrôle est la première instruction, avant toute lecture ou
     * écriture : un refus ne consomme ni ligne ni séquence (démonstration CE-01).
     */
    public Tournoi creerTournoi(String nom, LocalDate dateDebut, Format format) {
        session.exigerDroitCreerTournoi();
        return connexions.enTransaction(() -> tournois.creer(new Tournoi(nom, dateDebut, format)));
    }

    /** RG-01, RG-11, RG-20, RG-21 : inscription tant que le tournoi n'est pas démarré. */
    public void inscrire(long tournoiId, long equipeId) {
        session.exigerDroitCreerTournoi();
        connexions.enTransaction(() -> {
            Tournoi tournoi = charger(tournoiId);
            Equipe equipe = equipes.parId(equipeId)
                .orElseThrow(() -> new ValidationException("Équipe introuvable : " + equipeId));

            tournoi.inscrire(equipe);              // porte RG-11, RG-20, RG-21
            tournois.inscrire(tournoiId, equipeId);
        });
    }

    /** RG-20 : retrait tant que le tournoi n'est pas démarré. */
    public void retirer(long tournoiId, long equipeId) {
        session.exigerDroitCreerTournoi();
        connexions.enTransaction(() -> {
            Tournoi tournoi = charger(tournoiId);
            if (tournoi.estDemarre()) {
                throw new ValidationException(
                    "Impossible de retirer une équipe : le tournoi " + tournoi.nom()
                    + " est déjà démarré (RG-20)");
            }
            tournois.retirer(tournoiId, equipeId);
        });
    }

    /**
     * RG-22 : démarrage irréversible, une seule fois.
     *
     * Trois écritures : les matchs, le drapeau demarre, et rien d'autre. Elles
     * doivent réussir ou échouer ensemble — un tournoi marqué démarré sans
     * matchs serait irrécupérable, puisque RG-22 interdit de recommencer.
     */
    public Tournoi demarrer(long tournoiId) {
        session.exigerDroitCreerTournoi();
        return connexions.enTransaction(() -> {
            Tournoi tournoi = charger(tournoiId);
            FormatTournoi strategie = formats.pour(tournoi.format());

            // RG-23 : l'effectif est validé par la stratégie, pas par ce service.
            int nbEquipes = tournoi.equipes().size();
            if (!strategie.nbEquipesValide(nbEquipes)) {
                throw new ValidationException(
                    nbEquipes + " équipe(s) inscrite(s). " + strategie.contrainteEffectif());
            }

            List<Match> generes = strategie.genererMatchs(tournoi);
            tournoi.demarrer(generes);                       // porte RG-22
            matchs.creerTous(tournoiId, generes);
            tournois.marquerDemarre(tournoiId);
            return tournoi;
        });
    }

    /** RG-03 : la consultation exige une session, sans distinction de rôle. */
    public List<Tournoi> lister() {
        session.exigerConnecte();
        return connexions.enTransaction(tournois::tous);
    }

    public Tournoi parId(long tournoiId) {
        session.exigerConnecte();
        return connexions.enTransaction(() -> charger(tournoiId));
    }

    /** RG-03 : le vivier d'equipes, pour l'ecran d'inscription. */
    public List<Equipe> listerEquipes() {
        session.exigerConnecte();
        return connexions.enTransaction(equipes::toutes);
    }

    /**
     * Contrainte d'effectif du format, telle que la strategie l'enonce.
     *
     * Existe pour que l'IHM puisse annoncer la regle avant que l'utilisateur
     * ne la viole, sans jamais tester le format elle-meme : le texte vient de
     * la strategie, pas d'un switch dans le controleur.
     */
    public String contrainteEffectif(Format format) {
        return formats.pour(format).contrainteEffectif();
    }

    /** Meme delegation, pour refuser un effectif avant meme de creer le tournoi. */
    public boolean nbEquipesValide(Format format, int nbEquipes) {
        return formats.pour(format).nbEquipesValide(nbEquipes);
    }

    /** RG-70 : classement recalculé à la demande, délégué à la stratégie. */
    public List<LigneClassement> classement(long tournoiId) {
        session.exigerConnecte();
        return connexions.enTransaction(() -> {
            Tournoi tournoi = charger(tournoiId);
            return formats.pour(tournoi.format()).calculerClassement(tournoi);
        });
    }

    private Tournoi charger(long tournoiId) {
        return tournois.parId(tournoiId)
            .orElseThrow(() -> new ValidationException("Tournoi introuvable : " + tournoiId));
    }
}
