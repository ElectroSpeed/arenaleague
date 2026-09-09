package fr.lahorde.arenaleague.service;

import fr.lahorde.arenaleague.model.Utilisateur;
import fr.lahorde.arenaleague.repository.Transactions;
import fr.lahorde.arenaleague.repository.UtilisateurRepository;
import fr.lahorde.arenaleague.service.exception.AccesRefuseException;

import java.util.Arrays;
import java.util.Optional;

/**
 * Authentification et ouverture de session.
 *
 * Le mot de passe circule en tableau de caractères, jamais en String : un
 * tableau s'efface, une String reste en mémoire jusqu'au ramasse-miettes.
 * L'effacement a lieu dans un bloc finally, y compris quand
 * l'authentification échoue.
 */
public final class AuthService {

    private final UtilisateurRepository utilisateurs;
    private final VerificateurMotDePasse verificateur;
    private final SessionContext session;
    private final Transactions connexions;

    public AuthService(UtilisateurRepository utilisateurs,
                       VerificateurMotDePasse verificateur,
                       SessionContext session,
                       Transactions connexions) {
        this.utilisateurs = utilisateurs;
        this.verificateur = verificateur;
        this.session = session;
        this.connexions = connexions;
    }

    /**
     * Vérifie les identifiants et ouvre la session.
     *
     * Le message d'erreur est **identique** que le login soit inconnu ou le
     * mot de passe faux. Distinguer les deux cas révélerait quels comptes
     * existent, ce qui facilite une attaque ciblée.
     *
     * Le tableau reçu est effacé avant de rendre la main, quel que soit le
     * résultat — c'est la raison du bloc finally.
     */
    public Utilisateur authentifier(String login, char[] motDePasse) {
        try {
            if (login == null || login.isBlank() || motDePasse == null || motDePasse.length == 0) {
                throw new AccesRefuseException("Identifiant ou mot de passe incorrect");
            }

            Optional<Utilisateur> trouve =
                connexions.enTransaction(() -> utilisateurs.parLogin(login.trim()));

            // La vérification est faite même si le compte est introuvable, pour
            // que la durée de réponse ne trahisse pas l'existence d'un login.
            String empreinte = trouve.map(Utilisateur::motDePasseHash).orElse(EMPREINTE_FACTICE);
            boolean correspond = verificateur.correspond(motDePasse, empreinte);

            if (trouve.isEmpty() || !correspond) {
                throw new AccesRefuseException("Identifiant ou mot de passe incorrect");
            }

            Utilisateur utilisateur = trouve.get();
            session.ouvrir(utilisateur);
            return utilisateur;

        } finally {
            Arrays.fill(motDePasse == null ? new char[0] : motDePasse, '\0');
        }
    }

    public void deconnecter() {
        session.fermer();
    }

    /**
     * Empreinte valide d'un mot de passe qui n'existe pas.
     *
     * Sert uniquement à faire travailler l'algorithme quand le login est
     * inconnu : sans elle, une réponse immédiate signalerait « ce compte
     * n'existe pas », une réponse lente « ce compte existe ». C'est une
     * attaque par mesure de temps, et elle est simple à mener.
     */
    private static final String EMPREINTE_FACTICE =
        "$2b$10$0000000000000000000000000000000000000000000000000000";
}
