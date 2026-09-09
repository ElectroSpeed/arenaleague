package fr.lahorde.arenaleague;

/**
 * Point d'entrée du jar exécutable.
 *
 * Une classe qui étend javafx.application.Application ne peut pas servir de
 * classe principale dans un jar « fat » : le lanceur JavaFX refuse de démarrer
 * si les modules du runtime sont sur le classpath plutôt que le module-path.
 * Ce niveau d'indirection contourne le problème.
 */
public final class Launcher {
    public static void main(String[] args) {
        Main.main(args);
    }
}
