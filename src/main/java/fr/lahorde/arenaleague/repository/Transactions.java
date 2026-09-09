package fr.lahorde.arenaleague.repository;

/**
 * Frontière transactionnelle vue par la couche service.
 *
 * Le service déclare « ceci doit être atomique » sans savoir comment. Il ne
 * dépend donc ni de JDBC, ni d'HikariCP, ni de l'existence d'une base : en
 * test, une implémentation qui exécute directement l'action suffit.
 *
 * C'est la même logique que pour les repositories — dépendre d'une
 * abstraction, pas d'une implémentation.
 */
public interface Transactions {

    @FunctionalInterface
    interface Action<T> {
        T executer();
    }

    @FunctionalInterface
    interface ActionSansResultat {
        void executer();
    }

    /** Commit si l'action réussit, rollback et propagation sinon (RG-64). */
    <T> T enTransaction(Action<T> action);

    default void enTransaction(ActionSansResultat action) {
        enTransaction(() -> { action.executer(); return null; });
    }
}
