package fr.lahorde.arenaleague.model.etat;

import fr.lahorde.arenaleague.model.StatutMatch;

/**
 * Fabrique des états — reconstruction depuis la valeur persistée.
 *
 * Le switch sur l'enum est ici, et **uniquement ici**. C'est inévitable : il
 * faut bien un point où la chaîne lue en base redevient un objet. Ce qui
 * compte, c'est qu'il n'y en ait pas un second ailleurs dans le code métier.
 */
public final class EtatsMatch implements EtatMatchFabrique {

    public static final EtatMatch PLANIFIE = new Planifie();

    @Override
    public EtatMatch depuis(StatutMatch statut) {
        return switch (statut) {
            case PLANIFIE -> new Planifie();
            case EN_COURS -> new EnCours();
            case TERMINE  -> new Termine();
        };
    }

    /** État initial d'un match qui vient d'être généré — RG-50. */
    public static EtatMatch initial() {
        return new Planifie();
    }
}
