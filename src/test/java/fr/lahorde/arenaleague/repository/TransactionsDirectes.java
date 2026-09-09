package fr.lahorde.arenaleague.repository;

/**
 * Exécute l'action sans transaction ni base de données.
 *
 * Suffisant pour tester la couche service : ce qu'on y vérifie, ce sont les
 * règles métier et les droits, pas le comportement du SGBD. La logique de
 * commit et de rollback est éprouvée séparément sur ConnectionProvider.
 */
public final class TransactionsDirectes implements Transactions {

    @Override
    public <T> T enTransaction(Action<T> action) {
        return action.executer();
    }
}
