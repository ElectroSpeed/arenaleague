package fr.lahorde.arenaleague.model;

import fr.lahorde.arenaleague.model.etat.EtatMatch;
import fr.lahorde.arenaleague.model.exception.ValidationException;

import java.util.Objects;

/**
 * Rencontre entre deux équipes d'un même tournoi.
 *
 * Le nom de la table est « rencontre » et non « match » : MATCH est un mot
 * réservé SQL:2003 (clause MERGE). Le nom de la classe reste Match, plus
 * naturel côté domaine.
 *
 * L'état n'est pas un champ enum testé par des conditions : c'est un objet
 * EtatMatch qui décide lui-même des transitions autorisées (pattern State).
 */
public final class Match {

    private Long id;
    private final int tour;
    private final Equipe equipeA;
    private final Equipe equipeB;

    private Score score;        // null tant que le match n'est pas terminé (RG-83)
    private EtatMatch etat;
    private Utilisateur arbitre;

    public Match(Long id, int tour, Equipe equipeA, Equipe equipeB, EtatMatch etatInitial) {
        this.id = id;
        this.tour = tour;
        this.equipeA = Objects.requireNonNull(equipeA, "equipeA");
        this.equipeB = Objects.requireNonNull(equipeB, "equipeB");
        this.etat = Objects.requireNonNull(etatInitial, "etatInitial");

        // RG-80 : une équipe ne s'affronte pas elle-même
        if (this.equipeA.equals(this.equipeB)) {
            throw new ValidationException(
                "Une équipe ne peut pas s'affronter elle-même : " + equipeA.nom() + " (RG-80)");
        }
        // RG-32 / RG-42 : le tour commence à 1
        if (tour < 1) {
            throw new ValidationException("Le tour commence à 1, reçu : " + tour);
        }
    }

    public Match(int tour, Equipe equipeA, Equipe equipeB, EtatMatch etatInitial) {
        this(null, tour, equipeA, equipeB, etatInitial);
    }

    public Long id()            { return id; }
    public int tour()           { return tour; }
    public Equipe equipeA()     { return equipeA; }
    public Equipe equipeB()     { return equipeB; }
    public Score score()        { return score; }
    public EtatMatch etat()     { return etat; }
    public StatutMatch statut() { return etat.statut(); }
    public Utilisateur arbitre(){ return arbitre; }

    public void attribuerId(Long id) {
        if (this.id != null) {
            throw new IllegalStateException("L'identifiant est déjà attribué");
        }
        this.id = id;
    }

    public void affecterArbitre(Utilisateur arbitre) {
        this.arbitre = arbitre;
    }

    // ------------------------------------------------------------------
    //  Transitions — déléguées à l'état courant
    // ------------------------------------------------------------------

    /** RG-51 : PLANIFIÉ -> EN COURS. */
    public void demarrer() {
        this.etat = etat.demarrer(this);
    }

    /**
     * RG-52 : EN COURS -> TERMINÉ. La saisie du score est la clôture.
     *
     * L'ordre compte : l'état valide la transition **avant** que le score
     * soit posé. Si la transition est refusée, le score précédent est
     * intact — c'est ce que doit montrer la démonstration CE-02.
     */
    public void saisirScore(Score score) {
        Objects.requireNonNull(score, "score");
        EtatMatch suivant = etat.saisirScore(this, score);
        this.score = score;
        this.etat = suivant;
    }

    /** Réservé au repository : reconstruit un match déjà terminé depuis la base. */
    public void restaurerScore(Score score) {
        this.score = score;
    }

    public boolean estTermine() {
        return etat.estTerminal();
    }

    /**
     * RG-34 : le vainqueur avance au tour suivant.
     * null si le match est nul — cas possible en poule uniquement (RG-43).
     */
    public Equipe vainqueur() {
        if (!estTermine()) {
            throw new IllegalStateException("Le match n'est pas terminé, il n'a pas de vainqueur");
        }
        if (score.estNul()) {
            return null;
        }
        return score.equipeAGagne() ? equipeA : equipeB;
    }

    public Equipe perdant() {
        Equipe gagnant = vainqueur();
        if (gagnant == null) {
            return null;
        }
        return gagnant.equals(equipeA) ? equipeB : equipeA;
    }

    /** true si l'équipe participe à ce match. */
    public boolean concerne(Equipe equipe) {
        return equipeA.equals(equipe) || equipeB.equals(equipe);
    }

    /** Points marqués par l'équipe dans ce match. 0 si elle n'y participe pas. */
    public int marquesPar(Equipe equipe) {
        if (score == null) return 0;
        if (equipeA.equals(equipe)) return score.equipeA();
        if (equipeB.equals(equipe)) return score.equipeB();
        return 0;
    }

    /** Points encaissés par l'équipe dans ce match. 0 si elle n'y participe pas. */
    public int encaissesPar(Equipe equipe) {
        if (score == null) return 0;
        if (equipeA.equals(equipe)) return score.equipeB();
        if (equipeB.equals(equipe)) return score.equipeA();
        return 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Match autre)) return false;
        if (id != null && autre.id != null) return id.equals(autre.id);
        return tour == autre.tour
            && equipeA.equals(autre.equipeA)
            && equipeB.equals(autre.equipeB);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : Objects.hash(tour, equipeA, equipeB);
    }

    @Override
    public String toString() {
        return equipeA.nom() + " – " + equipeB.nom()
             + (score != null ? " (" + score + ")" : "")
             + " [" + etat.libelle() + "]";
    }
}
