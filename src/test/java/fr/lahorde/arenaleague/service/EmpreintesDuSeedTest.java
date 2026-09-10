package fr.lahorde.arenaleague.service;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Non-régression : les comptes du jeu de données sont réellement utilisables.
 *
 * **Ce test existe à cause d'un incident.** Le 10 septembre 2026, les
 * empreintes de `V2__seed.sql` étaient en révision `$2b$`, que jbcrypt 0.4 ne
 * sait pas lire : toute connexion levait « Invalid salt revision » et aucun
 * compte ne fonctionnait. Les 38 tests étaient au vert.
 *
 * Ils l'étaient parce que `AuthServiceTest` et `CasErreurDemonstrationTest`
 * injectent un `VerificateurMotDePasse` factice. C'est le bon choix pour
 * tester la logique d'authentification — un BCrypt réel coûterait une
 * centaine de millisecondes par test — mais cela laissait **personne** pour
 * confronter les empreintes du seed à la vraie bibliothèque.
 *
 * Cette classe est donc la seule à instancier `BCryptVerificateur`. Elle ne
 * teste pas un service : elle vérifie qu'une **donnée de production** reste
 * compatible avec le code qui la consomme. C'est lent, une fraction de
 * seconde par empreinte, et c'est le prix à payer une fois.
 */
class EmpreintesDuSeedTest {

    private static final String MIGRATION = "/db/migration/V2__seed.sql";

    /**
     * Mots de passe annoncés par le commentaire de la migration, par le
     * README et par CLAUDE.md. Si l'un d'eux change, ce tableau change aussi
     * — et c'est bien le but : la documentation et le seed ne peuvent plus
     * diverger en silence.
     */
    private static final Map<String, String> MOTS_DE_PASSE_ATTENDUS = Map.of(
        "orga", "orga",
        "arbitre", "arbitre");

    /**
     * Révisions que jbcrypt 0.4 sait lire. `$2b$`, produit par la plupart des
     * générateurs en ligne et par les bibliothèques récentes, en est
     * **absent** : c'est précisément ce qui avait cassé la connexion.
     */
    private static final Pattern REVISION_SUPPORTEE = Pattern.compile("^\\$2[ay]\\$.*");

    private static final Pattern LIGNE_DE_COMPTE = Pattern.compile(
        "\\('([a-z]+)',\\s*'(\\$2[a-z]\\$[^']+)'");

    private static final VerificateurMotDePasse VERIFICATEUR = new BCryptVerificateur();

    private static Map<String, String> empreintes;

    @BeforeAll
    static void lireLesEmpreintesDeLaMigration() throws IOException {
        empreintes = new LinkedHashMap<>();
        try (InputStream flux = EmpreintesDuSeedTest.class.getResourceAsStream(MIGRATION)) {
            assertThat(flux)
                .describedAs("Migration %s introuvable sur le classpath de test", MIGRATION)
                .isNotNull();

            String sql = new String(flux.readAllBytes(), StandardCharsets.UTF_8);
            Matcher trouve = LIGNE_DE_COMPTE.matcher(sql);
            while (trouve.find()) {
                empreintes.put(trouve.group(1), trouve.group(2));
            }
        }
    }

    @Test
    @DisplayName("La migration déclare exactement les comptes documentés")
    void lesComptesAttendusSontPresents() {
        assertThat(empreintes)
            .describedAs("Comptes lus dans %s", MIGRATION)
            .containsOnlyKeys(MOTS_DE_PASSE_ATTENDUS.keySet().toArray(new String[0]));
    }

    @Test
    @DisplayName("Chaque empreinte est dans une révision que jbcrypt sait lire")
    void lesRevisionsSontSupportees() {
        assertThat(empreintes).allSatisfy((login, empreinte) ->
            assertThat(empreinte)
                .describedAs("Empreinte du compte « %s ». Une révision $2b$ lève "
                    + "« Invalid salt revision » à la vérification et rend le compte "
                    + "inutilisable. Régénérer avec BCrypt.gensalt(), qui produit $2a$", login)
                .matches(REVISION_SUPPORTEE));
    }

    @Test
    @DisplayName("Le mot de passe documenté ouvre bien chaque compte")
    void chaqueCompteAccepteSonMotDePasse() {
        assertThat(empreintes).allSatisfy((login, empreinte) -> {
            String motDePasse = MOTS_DE_PASSE_ATTENDUS.get(login);
            assertThat(VERIFICATEUR.correspond(motDePasse.toCharArray(), empreinte))
                .describedAs("Le compte « %s » doit s'ouvrir avec le mot de passe "
                    + "annoncé par la documentation", login)
                .isTrue();
        });
    }

    @Test
    @DisplayName("Un mot de passe faux est refusé — l'empreinte n'accepte pas tout")
    void chaqueCompteRefuseUnMauvaisMotDePasse() {
        // Sans ce test, une implémentation qui retournerait toujours true
        // passerait le précédent. Un test vert doit l'être pour la bonne raison.
        assertThat(empreintes).allSatisfy((login, empreinte) -> {
            char[] faux = (MOTS_DE_PASSE_ATTENDUS.get(login) + "X").toCharArray();
            assertThat(VERIFICATEUR.correspond(faux, empreinte))
                .describedAs("Le compte « %s » ne doit pas s'ouvrir avec un mot de "
                    + "passe erroné", login)
                .isFalse();
        });
    }
}
