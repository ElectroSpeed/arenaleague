package fr.lahorde.arenaleague.controller;

/**
 * Contrôleur qui détient une ressource à rendre quand son écran disparaît.
 *
 * JavaFX ne prévient jamais un contrôleur qu'il est remplacé : la scène
 * change, l'ancien objet devient inaccessible, et ce qu'il avait enregistré
 * ailleurs — un abonnement, un fichier, une tâche — y reste. C'est le cas de
 * l'écran de saisie, abonné aux notifications de MatchService : sans retrait,
 * chaque passage laisserait un écouteur de plus rafraîchir une vue morte.
 *
 * Vues appelle liberer() sur le contrôleur sortant avant d'installer le
 * suivant. Un contrôleur qui n'a rien à rendre n'implémente pas l'interface.
 */
public interface Liberable {

    /** Appelé une fois, juste avant que l'écran ne soit remplacé. */
    void liberer();
}
