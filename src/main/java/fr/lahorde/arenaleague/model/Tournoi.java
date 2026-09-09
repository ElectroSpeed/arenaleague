package fr.lahorde.arenaleague.model;

import fr.lahorde.arenaleague.model.exception.ValidationException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Un tournoi, ses équipes inscrites et ses matchs.
 *
 * Deux relations de nature différente, et il faut savoir les justifier :
 *   - Tournoi ◆— Match : composition. Un match n'a aucun sens hors de son
 *     tournoi, le supprimer supprime ses matchs (ON DELETE CASCADE).
 *   - Tournoi ◇— Equipe : agrégation. Une équipe survit à la fin du tournoi
 *     et peut s'inscrire ailleurs (ON DELETE RESTRICT).
 *
 * Le tournoi porte un Format (enum persisté). Le comportement associé vit
 * dans FormatTournoi, résolu par le service — le model ne dépend d'aucune
 * implémentation.
 */
public final class Tournoi {

    private Long id;
    private final String nom;
    private final LocalDate dateDebut;
    private final Format format;
    private boolean demarre;

    private final List<Equipe> equipes = new ArrayList<>();
    private final List<Match> matchs = new ArrayList<>();

    public Tournoi(Long id, String nom, LocalDate dateDebut, Format format, boolean demarre) {
        this.id = id;
        this.nom = Objects.requireNonNull(nom, "nom").trim();
        this.dateDebut = Objects.requireNonNull(dateDebut, "dateDebut");
        this.format = Objects.requireNonNull(format, "format");
        this.demarre = demarre;
        if (this.nom.isEmpty()) {
            throw new IllegalArgumentException("Le nom du tournoi ne peut pas être vide");
        }
    }

    public Tournoi(String nom, LocalDate dateDebut, Format format) {
        this(null, nom, dateDebut, format, false);
    }

    public Long id()             { return id; }
    public String nom()          { return nom; }
    public LocalDate dateDebut() { return dateDebut; }
    public Format format()       { return format; }
    public boolean estDemarre()  { return demarre; }

    public void attribuerId(Long id) {
        if (this.id != null) {
            throw new IllegalStateException("L'identifiant est déjà attribué");
        }
        this.id = id;
    }

    public List<Equipe> equipes() { return Collections.unmodifiableList(equipes); }
    public List<Match> matchs()   { return Collections.unmodifiableList(matchs); }

    // ------------------------------------------------------------------
    //  Inscriptions
    // ------------------------------------------------------------------

    /** RG-20, RG-21 : inscription possible tant que le tournoi n'est pas démarré. */
    public void inscrire(Equipe equipe) {
        Objects.requireNonNull(equipe, "equipe");
        exigerNonDemarre("inscrire une équipe");
        if (equipes.contains(equipe)) {
            throw new ValidationException(
                "L'équipe " + equipe.nom() + " est déjà inscrite à " + nom + " (RG-21)");
        }
        // RG-11 : l'effectif est vérifié au moment de l'inscription
        if (!equipe.effectifValide()) {
            throw new ValidationException(
                "L'équipe " + equipe.nom() + " compte " + equipe.effectif()
                + " joueur(s) ; il en faut entre " + Equipe.EFFECTIF_MIN
                + " et " + Equipe.EFFECTIF_MAX + " (RG-10)");
        }
        equipes.add(equipe);
    }

    /** RG-20 : retrait possible tant que le tournoi n'est pas démarré. */
    public void retirer(Equipe equipe) {
        exigerNonDemarre("retirer une équipe");
        equipes.remove(equipe);
    }

    // ------------------------------------------------------------------
    //  Démarrage
    // ------------------------------------------------------------------

    /**
     * RG-22 : le démarrage est irréversible et n'a lieu qu'une fois.
     *
     * Les matchs sont générés par le service, qui appelle
     * FormatTournoi.genererMatchs puis les ajoute ici. Le tournoi ne sait pas
     * comment on génère un arbre ou un round-robin — c'est le rôle de la
     * Strategy.
     */
    public void demarrer(List<Match> matchsInitiaux) {
        exigerNonDemarre("démarrer le tournoi");
        if (matchsInitiaux == null || matchsInitiaux.isEmpty()) {
            throw new ValidationException("Aucun match à générer : le tournoi ne peut pas démarrer");
        }
        this.matchs.addAll(matchsInitiaux);
        this.demarre = true;
    }

    /** RG-35 : ajout des matchs d'un tour suivant, après démarrage. */
    public void ajouterMatchs(List<Match> nouveaux) {
        if (!demarre) {
            throw new ValidationException("Le tournoi n'est pas démarré");
        }
        this.matchs.addAll(nouveaux);
    }

    /** Réservé au repository : reconstruit un tournoi depuis la base. */
    public void restaurer(List<Equipe> equipesInscrites, List<Match> matchsExistants) {
        this.equipes.clear();
        this.equipes.addAll(equipesInscrites);
        this.matchs.clear();
        this.matchs.addAll(matchsExistants);
    }

    private void exigerNonDemarre(String action) {
        if (demarre) {
            throw new ValidationException(
                "Impossible de " + action + " : le tournoi " + nom + " est déjà démarré (RG-20)");
        }
    }

    // ------------------------------------------------------------------
    //  Consultation
    // ------------------------------------------------------------------

    /** RG-36, RG-45 : terminé quand tous les matchs le sont. */
    public boolean estTermine() {
        return demarre && !matchs.isEmpty() && matchs.stream().allMatch(Match::estTermine);
    }

    public List<Match> matchsTermines() {
        return matchs.stream().filter(Match::estTermine).toList();
    }

    public List<Match> matchsDuTour(int tour) {
        return matchs.stream().filter(m -> m.tour() == tour).toList();
    }

    /** RG-35 : le tour n+1 ne se génère que si tous les matchs du tour n sont terminés. */
    public boolean tourTermine(int tour) {
        List<Match> duTour = matchsDuTour(tour);
        return !duTour.isEmpty() && duTour.stream().allMatch(Match::estTermine);
    }

    public int dernierTour() {
        return matchs.stream().mapToInt(Match::tour).max().orElse(0);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Tournoi autre)) return false;
        return nom.equalsIgnoreCase(autre.nom);
    }

    @Override
    public int hashCode() {
        return nom.toLowerCase().hashCode();
    }

    @Override
    public String toString() {
        return nom + " (" + format + (demarre ? ", démarré" : ", inscriptions ouvertes") + ")";
    }
}
