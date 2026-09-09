package fr.lahorde.arenaleague.service;

import fr.lahorde.arenaleague.model.Utilisateur;
import fr.lahorde.arenaleague.service.exception.AccesRefuseException;

/**
 * Utilisateur connecté et contrôles de droits.
 *
 * **C'est ici que vivent les droits, pas dans l'IHM.** L'interface masque des
 * boutons pour le confort de l'utilisateur ; masquer un bouton n'est pas un
 * contrôle d'accès. Un raccourci clavier, un appel direct au service ou une
 * IHM modifiée contourneraient l'affichage — pas ces méthodes.
 *
 * C'est ce que doit prouver la démonstration CE-01 : l'accès reste refusé
 * même quand on court-circuite l'interface.
 *
 * Les méthodes exigerXxx() ne retournent rien : elles passent ou elles
 * lèvent. Une méthode qui retournerait un booléen pourrait voir son résultat
 * ignoré par un appelant distrait.
 */
public final class SessionContext {

    private Utilisateur utilisateur;

    public void ouvrir(Utilisateur utilisateur) {
        this.utilisateur = utilisateur;
    }

    public void fermer() {
        this.utilisateur = null;
    }

    public boolean estConnecte() {
        return utilisateur != null;
    }

    /** null si personne n'est connecté. */
    public Utilisateur utilisateur() {
        return utilisateur;
    }

    /** RG-03 : aucun accès anonyme. */
    public Utilisateur exigerConnecte() {
        if (utilisateur == null) {
            throw new AccesRefuseException("Aucun utilisateur connecté (RG-03)");
        }
        return utilisateur;
    }

    /**
     * RG-01 : créer un tournoi, inscrire des équipes, générer les matchs.
     *
     * Le droit n'est pas déduit d'un test sur le rôle mais demandé à
     * l'utilisateur lui-même, qui répond selon sa classe. Aucun
     * « if (role == ORGANISATEUR) » n'existe dans le code métier.
     */
    public Utilisateur exigerDroitCreerTournoi() {
        Utilisateur connecte = exigerConnecte();
        if (!connecte.peutCreerTournoi()) {
            throw new AccesRefuseException(
                "Action réservée à l'Organisateur. " + connecte.login()
                + " est connecté en tant qu'Arbitre (RG-01)");
        }
        return connecte;
    }

    /** RG-02 : démarrer un match et saisir un score — les deux rôles. */
    public Utilisateur exigerDroitSaisirScore() {
        Utilisateur connecte = exigerConnecte();
        if (!connecte.peutSaisirScore()) {
            throw new AccesRefuseException(
                "Action réservée aux Arbitres et Organisateurs (RG-02)");
        }
        return connecte;
    }
}
