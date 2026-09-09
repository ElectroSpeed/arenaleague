package fr.lahorde.arenaleague.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Lecture de application.properties.
 *
 * Volontairement minimaliste : aucune bibliothèque de configuration, le
 * fichier est chargé une fois au démarrage. Ce choix accompagne l'absence
 * de framework — voir le dossier d'architecture, §2.
 */
public final class AppConfig {

    private final Properties props = new Properties();

    public AppConfig() {
        try (InputStream in = AppConfig.class.getResourceAsStream("/application.properties")) {
            if (in == null) {
                throw new IllegalStateException(
                    "application.properties introuvable. "
                    + "Copiez application.properties.example puis relancez.");
            }
            props.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Lecture de la configuration impossible", e);
        }
    }

    public String get(String cle) {
        String valeur = props.getProperty(cle);
        if (valeur == null) {
            throw new IllegalStateException("Clé de configuration absente : " + cle);
        }
        return valeur;
    }

    public String get(String cle, String defaut) {
        return props.getProperty(cle, defaut);
    }

    public int getInt(String cle, int defaut) {
        String valeur = props.getProperty(cle);
        return valeur == null ? defaut : Integer.parseInt(valeur.trim());
    }

    public boolean getBoolean(String cle, boolean defaut) {
        String valeur = props.getProperty(cle);
        return valeur == null ? defaut : Boolean.parseBoolean(valeur.trim());
    }

    /** postgres ou h2 */
    public String profil() {
        return get("app.profile", "postgres").trim().toLowerCase();
    }
}
