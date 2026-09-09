package fr.lahorde.arenaleague;

import fr.lahorde.arenaleague.config.AppConfig;
import fr.lahorde.arenaleague.config.Database;
import fr.lahorde.arenaleague.model.etat.EtatsMatch;
import fr.lahorde.arenaleague.repository.*;
import fr.lahorde.arenaleague.service.*;
import fr.lahorde.arenaleague.service.format.FabriqueFormat;

/**
 * Composition root : le seul endroit où les dépendances sont assemblées.
 *
 * Chaque objet reçoit ce dont il a besoin par constructeur et ne va jamais le
 * chercher lui-même. Aucun framework d'injection : sur un projet noté sur la
 * compréhension de l'architecture, écrire soi-même le câblage prouve qu'on
 * l'a comprise. Le coût est cette classe ; le bénéfice est qu'il n'y a aucune
 * magie à expliquer au jury.
 *
 * L'ordre de construction lit l'architecture de bas en haut :
 * infrastructure, puis repositories, puis services. Aucune ligne ne remonte.
 */
public final class AppContext {

    private final AppConfig config;
    private final Database database;

    private final ConnectionProvider connexions;
    private final UtilisateurRepository utilisateurs;
    private final EquipeRepository equipes;
    private final MatchRepository matchs;
    private final TournoiRepository tournois;

    private final SessionContext session;
    private final AuthService auth;
    private final TournoiService tournoiService;
    private final MatchService matchService;
    private final ClassementService classementService;

    public AppContext(AppConfig config, Database database) {
        this.config = config;
        this.database = database;

        // --- infrastructure ---
        this.connexions = new ConnectionProvider(database.dataSource());
        FabriqueFormat formats = new FabriqueFormat();

        // --- repositories ---
        this.utilisateurs = new UtilisateurJdbcRepository(connexions);
        this.equipes = new EquipeJdbcRepository(connexions);
        this.matchs = new MatchJdbcRepository(connexions, new EtatsMatch());
        this.tournois = new TournoiJdbcRepository(connexions, equipes, matchs);

        // --- services ---
        this.session = new SessionContext();
        this.auth = new AuthService(utilisateurs, new BCryptVerificateur(), session, connexions);
        this.tournoiService = new TournoiService(tournois, equipes, matchs, formats, session, connexions);
        this.matchService = new MatchService(tournois, matchs, formats, session, connexions);
        this.classementService = new ClassementService(tournois, formats, session, connexions);

        // Pattern Observer : une saisie de score invalide le classement, qui
        // prévient à son tour l'IHM. Ce câblage est la seule ligne qui relie
        // les deux services — ils s'ignorent l'un l'autre.
        this.matchService.abonner(classementService);
    }

    public AppConfig config()                     { return config; }
    public Database database()                    { return database; }
    public SessionContext session()               { return session; }
    public AuthService auth()                     { return auth; }
    public TournoiService tournois()              { return tournoiService; }
    public MatchService matchs()                  { return matchService; }
    public ClassementService classements()        { return classementService; }

    public void fermer() {
        database.close();
    }
}
