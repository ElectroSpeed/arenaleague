package fr.lahorde.arenaleague.model;

import fr.lahorde.arenaleague.model.exception.ValidationException;

/**
 * Résultat chiffré d'un match — objet valeur immuable.
 *
 * La validation vit dans le constructeur compact : un Score qui existe est
 * forcément valide au regard de RG-60. Il n'y a pas d'état intermédiaire
 * incohérent à gérer ailleurs.
 *
 * L'interdiction du match nul (RG-33) n'est **pas** ici : elle dépend du
 * format du tournoi, et le Score ne connaît pas son tournoi. C'est
 * FormatTournoi.validerScore() qui la porte.
 */
public record Score(int equipeA, int equipeB) {

    public Score {
        // RG-60 : les deux scores sont des entiers supérieurs ou égaux à 0
        if (equipeA < 0 || equipeB < 0) {
            throw new ValidationException(
                "Un score ne peut pas être négatif (RG-60) : " + equipeA + " – " + equipeB);
        }
    }

    public boolean estNul() {
        return equipeA == equipeB;
    }

    /** true si l'équipe A l'emporte, false si c'est B. À n'appeler que si le match n'est pas nul. */
    public boolean equipeAGagne() {
        if (estNul()) {
            throw new IllegalStateException("Match nul : aucune équipe ne l'emporte");
        }
        return equipeA > equipeB;
    }

    public int total() {
        return equipeA + equipeB;
    }

    @Override
    public String toString() {
        return equipeA + " – " + equipeB;
    }
}
