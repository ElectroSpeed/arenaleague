package fr.lahorde.arenaleague.repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Fournit la connexion JDBC et porte la transaction.
 *
 * Deux décisions, et il faut savoir les défendre :
 *
 * 1. **La transaction est ouverte par la couche service, jamais par un DAO.**
 *    Un DAO qui gère sa propre transaction rend impossible toute opération
 *    composée : la saisie d'un score met à jour le match, génère
 *    éventuellement le tour suivant et invalide le classement (RG-63). Ces
 *    écritures doivent réussir ou échouer ensemble (RG-64).
 *
 * 2. **La connexion voyage dans un ThreadLocal**, pas en paramètre de chaque
 *    méthode. Sans cela, il faudrait faire descendre un objet Connection à
 *    travers toute la pile d'appels — le service se mettrait à parler JDBC,
 *    et l'étanchéité des couches serait perdue.
 *
 * Les DAO exigent une transaction ouverte. C'est volontairement strict :
 * une écriture hors transaction est une erreur de conception, pas un cas à
 * rattraper silencieusement.
 */
public final class ConnectionProvider implements Transactions {

    private final DataSource dataSource;
    private final ThreadLocal<Connection> courante = new ThreadLocal<>();

    public ConnectionProvider(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Exécute l'action dans une transaction : commit si tout passe, rollback
     * à la moindre exception, puis propagation.
     *
     * Les appels imbriqués rejoignent la transaction en cours plutôt que d'en
     * ouvrir une seconde — sinon un service qui en appelle un autre créerait
     * deux transactions concurrentes sur la même opération métier.
     */
    @Override
    public <T> T enTransaction(Action<T> action) {
        if (courante.get() != null) {
            return action.executer();          // déjà dans une transaction
        }
        Connection connexion = null;
        try {
            connexion = dataSource.getConnection();
            connexion.setAutoCommit(false);
            courante.set(connexion);

            T resultat = action.executer();

            connexion.commit();
            return resultat;

        } catch (SQLException e) {
            annuler(connexion);
            throw new PersistenceException("Échec de la transaction", e);
        } catch (RuntimeException e) {
            annuler(connexion);
            throw e;                            // exception métier : on la laisse passer
        } finally {
            courante.remove();
            fermer(connexion);
        }
    }

    /** Connexion de la transaction courante. Réservé aux DAO. */
    Connection connexion() {
        Connection connexion = courante.get();
        if (connexion == null) {
            throw new PersistenceException(
                "Aucune transaction ouverte. Tout accès à la base doit passer par "
                + "ConnectionProvider.enTransaction(...) depuis la couche service.");
        }
        return connexion;
    }

    private void annuler(Connection connexion) {
        if (connexion == null) return;
        try {
            connexion.rollback();
        } catch (SQLException e) {
            // Le rollback a échoué : on ne masque pas l'exception d'origine.
            System.err.println("Rollback impossible : " + e.getMessage());
        }
    }

    private void fermer(Connection connexion) {
        if (connexion == null) return;
        try {
            connexion.close();                  // rend la connexion au pool Hikari
        } catch (SQLException e) {
            System.err.println("Fermeture de connexion impossible : " + e.getMessage());
        }
    }
}
