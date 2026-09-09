package fr.lahorde.arenaleague.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;

import javax.sql.DataSource;

/**
 * Source de données et migrations.
 *
 * Deux profils partagent le même SQL de migration :
 *   - postgres : le mode normal, aligné sur docker-compose.yml
 *   - h2       : repli de démonstration, H2 en mode compatibilité PostgreSQL
 *
 * Le repli existe pour une raison précise : si Docker n'est pas disponible
 * sur le poste le jour de la soutenance, l'application démarre quand même.
 */
public final class Database implements AutoCloseable {

    private final HikariDataSource dataSource;

    public Database(AppConfig config) {
        String profil = config.profil();

        HikariConfig hikari = new HikariConfig();
        switch (profil) {
            case "postgres" -> {
                hikari.setJdbcUrl(config.get("db.postgres.url"));
                hikari.setUsername(config.get("db.postgres.user"));
                hikari.setPassword(config.get("db.postgres.password"));
            }
            case "h2" -> {
                hikari.setJdbcUrl(config.get("db.h2.url"));
                hikari.setUsername(config.get("db.h2.user"));
                hikari.setPassword(config.get("db.h2.password", ""));
            }
            default -> throw new IllegalStateException(
                "Profil inconnu : " + profil + " (attendu : postgres ou h2)");
        }

        hikari.setMaximumPoolSize(config.getInt("db.pool.maxSize", 10));
        hikari.setMinimumIdle(config.getInt("db.pool.minIdle", 2));
        hikari.setConnectionTimeout(config.getInt("db.pool.connectionTimeoutMs", 5000));
        hikari.setPoolName("arenaleague-pool");
        hikari.setAutoCommit(false); // les transactions sont pilotées par la couche service

        this.dataSource = new HikariDataSource(hikari);

        if (config.getBoolean("db.migrateOnStartup", true)) {
            migrer();
        }
    }

    private void migrer() {
        Flyway.configure()
              .dataSource(dataSource)
              .locations("classpath:db/migration")
              .load()
              .migrate();
    }

    public DataSource dataSource() {
        return dataSource;
    }

    @Override
    public void close() {
        dataSource.close();
    }
}
