package fr.lahorde.arenaleague.service;

import fr.lahorde.arenaleague.model.Arbitre;
import fr.lahorde.arenaleague.model.Format;
import fr.lahorde.arenaleague.model.Organisateur;
import fr.lahorde.arenaleague.model.Utilisateur;
import fr.lahorde.arenaleague.repository.Transactions;
import fr.lahorde.arenaleague.repository.TransactionsDirectes;
import fr.lahorde.arenaleague.repository.TournoiRepository;
import fr.lahorde.arenaleague.repository.UtilisateurRepositoryEnMemoire;
import fr.lahorde.arenaleague.service.exception.AccesRefuseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Tests du contrôle de droits — RG-01, RG-02, RG-03.
 *
 * Aucune base de données : le repository est une implémentation en mémoire et
 * la vérification du mot de passe est remplacée par une comparaison simple.
 * BCrypt est volontairement lent, l'utiliser ici ralentirait la suite sans
 * rien prouver de plus.
 */
class AuthServiceTest {

    private static final String EMPREINTE_ORGA    = "empreinte-orga";
    private static final String EMPREINTE_ARBITRE = "empreinte-arbitre";

    private SessionContext session;
    private AuthService auth;
    private TournoiRepository tournois;
    private TournoiService tournoiService;

    /** Vérificateur de test : l'empreinte attendue est « empreinte-<login> ». */
    private static final VerificateurMotDePasse VERIFICATEUR =
        (clair, empreinte) -> empreinte.equals("empreinte-" + new String(clair));

    @BeforeEach
    void preparer() {
        Utilisateur orga    = new Organisateur(1L, "orga", EMPREINTE_ORGA);
        Utilisateur arbitre = new Arbitre(2L, "arbitre", EMPREINTE_ARBITRE);

        var comptes = new UtilisateurRepositoryEnMemoire().ajouter(orga).ajouter(arbitre);

        // Pas de base de données : l'action est exécutée directement.
        Transactions connexions = new TransactionsDirectes();

        session = new SessionContext();
        auth = new AuthService(comptes, VERIFICATEUR, session, connexions);
        tournois = mock(TournoiRepository.class);
        tournoiService = new TournoiService(tournois, session, connexions);
    }

    // ------------------------------------------------------------------
    //  Authentification
    // ------------------------------------------------------------------

    @Test
    @DisplayName("RG-03 : des identifiants valides ouvrent la session")
    void authentificationReussie() {
        Utilisateur connecte = auth.authentifier("orga", "orga".toCharArray());

        assertThat(connecte.login()).isEqualTo("orga");
        assertThat(session.estConnecte()).isTrue();
    }

    @Test
    @DisplayName("Le mot de passe est effacé du tableau après usage")
    void motDePasseEfface() {
        char[] saisie = "orga".toCharArray();
        auth.authentifier("orga", saisie);

        assertThat(saisie).containsOnly('\0');
    }

    @Test
    @DisplayName("Le tableau est effacé même quand l'authentification échoue")
    void motDePasseEffaceMemeEnEchec() {
        char[] saisie = "mauvais".toCharArray();

        assertThatThrownBy(() -> auth.authentifier("orga", saisie))
            .isInstanceOf(AccesRefuseException.class);

        assertThat(saisie).containsOnly('\0');
    }

    @Test
    @DisplayName("Login inconnu et mot de passe faux donnent le même message")
    void messageIndifferencie() {
        String surLoginInconnu = attraperMessage(() -> auth.authentifier("inconnu", "x".toCharArray()));
        String surMauvaisMdp   = attraperMessage(() -> auth.authentifier("orga", "faux".toCharArray()));

        // Distinguer les deux cas révélerait quels comptes existent.
        assertThat(surLoginInconnu).isEqualTo(surMauvaisMdp);
    }

    // ------------------------------------------------------------------
    //  Droits — le point non négociable de la tâche
    // ------------------------------------------------------------------

    @Test
    @DisplayName("RG-01 / CE-01 : un Arbitre ne peut pas créer un tournoi, "
               + "même en appelant le service directement")
    void arbitreNePeutPasCreerTournoi() {
        auth.authentifier("arbitre", "arbitre".toCharArray());

        assertThatThrownBy(() ->
                tournoiService.creerTournoi("Coupe Automne", LocalDate.now(), Format.POULE))
            .isInstanceOf(AccesRefuseException.class)
            .hasMessageContaining("Organisateur");

        // Le refus ne doit laisser aucune trace : le repository n'est pas touché.
        verifyNoInteractions(tournois);
    }

    @Test
    @DisplayName("RG-01 : un Organisateur crée un tournoi sans obstacle")
    void organisateurPeutCreerTournoi() {
        auth.authentifier("orga", "orga".toCharArray());

        assertThat(session.utilisateur().peutCreerTournoi()).isTrue();
    }

    @Test
    @DisplayName("RG-03 : aucun accès sans session ouverte")
    void accesAnonymeRefuse() {
        assertThatThrownBy(() ->
                tournoiService.creerTournoi("Open Hiver", LocalDate.now(), Format.ELIMINATION_DIRECTE))
            .isInstanceOf(AccesRefuseException.class);
    }

    @Test
    @DisplayName("La déconnexion referme la session")
    void deconnexion() {
        auth.authentifier("orga", "orga".toCharArray());
        auth.deconnecter();

        assertThat(session.estConnecte()).isFalse();
    }

    private String attraperMessage(Runnable action) {
        try {
            action.run();
            return null;
        } catch (RuntimeException e) {
            return e.getMessage();
        }
    }
}
