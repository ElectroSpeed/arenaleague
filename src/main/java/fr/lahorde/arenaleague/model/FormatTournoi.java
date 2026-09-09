package fr.lahorde.arenaleague.model;

import java.util.List;

/**
 * Pattern Strategy — mode de déroulement d'un tournoi.
 *
 * Deux implémentations, EliminationDirecte et Poule (tâche 2.5), qui
 * diffèrent sur trois comportements à la fois : la génération des matchs,
 * la validation d'un score et le calcul du classement. Un
 * « if (format == POULE) » répété à trois endroits est exactement ce que
 * ce pattern élimine.
 *
 * L'interface vit dans la couche model et reste **purement métier** : elle
 * ne persiste rien, ne connaît ni repository ni transaction. Le service qui
 * l'appelle se charge d'enregistrer ce qu'elle retourne. C'est ce qui permet
 * de la tester sans base de données.
 */
public interface FormatTournoi {

    /** Valeur persistée correspondante. */
    Format format();

    /** RG-30 (puissance de 2) ou RG-40 (entre 3 et 8) selon le format. */
    boolean nbEquipesValide(int nbEquipes);

    /** Message d'explication affiché quand nbEquipesValide est faux. */
    String contrainteEffectif();

    /**
     * Matchs du premier tour — RG-31, RG-32, RG-41, RG-42.
     * Retourne les objets sans les persister.
     */
    List<Match> genererMatchs(Tournoi tournoi);

    /**
     * RG-33 : le match nul est interdit en élimination directe, autorisé en
     * poule. Lève ValidationException si le score est incompatible.
     *
     * C'est la meilleure démonstration du pattern : le même score est
     * accepté dans un format et refusé dans l'autre, sans aucun test
     * conditionnel dans le service.
     */
    void validerScore(Score score);

    /** RG-44 et RG-71 à RG-76 selon le format. Liste triée, rang renseigné. */
    List<LigneClassement> calculerClassement(Tournoi tournoi);

    /**
     * RG-35 : matchs du tour suivant, à générer après la clôture d'un match.
     *
     * Comportement par défaut : aucun match. C'est le cas de la poule, où
     * tout est généré au démarrage — et c'est ce qui évite un
     * « if (format == ELIMINATION_DIRECTE) » dans le service. Celui-ci appelle
     * la méthode systématiquement et persiste ce qu'il reçoit : le plus
     * souvent rien.
     */
    default List<Match> genererTourSuivant(Tournoi tournoi) {
        return List.of();
    }
}
