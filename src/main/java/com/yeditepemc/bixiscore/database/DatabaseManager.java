package com.yeditepemc.bixiscore.database;

import com.yeditepemc.bixiscore.BixisCorePlugin;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Veritabanı katmanı. HikariCP connection pool'u üzerinden çalışır.
 * Varsayılan olarak SQLite kullanır; config {@code database.type: mysql}
 * yapıldığında MySQL'e hazırdır.
 *
 * <p>Tüm public query/update metotları asenkrondur ve ana thread'i bloke etmez
 * (CLAUDE.md — Async veritabanı operasyonları zorunlu).
 */
public class DatabaseManager {

    /** Desteklenen veritabanı tipleri. */
    public enum Type {
        SQLITE,
        MYSQL
    }

    /** {@link ResultSet}'i bir değere dönüştüren, SQLException fırlatabilen fonksiyon. */
    @FunctionalInterface
    public interface ResultSetFunction<T> {
        T apply(ResultSet resultSet) throws SQLException;
    }

    private final BixisCorePlugin plugin;
    private Type type;
    private HikariDataSource dataSource;

    public DatabaseManager(BixisCorePlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    //  Bağlantı yönetimi
    // ------------------------------------------------------------------

    /**
     * Config'i okuyup connection pool'u kurar.
     */
    public void connect() {
        FileConfiguration config = plugin.getConfig();
        String rawType = config.getString("database.type", "sqlite");
        this.type = "mysql".equalsIgnoreCase(rawType) ? Type.MYSQL : Type.SQLITE;

        HikariConfig hikari = new HikariConfig();
        hikari.setPoolName("BixisCore-Pool");

        if (type == Type.MYSQL) {
            String host = config.getString("database.mysql.host", "localhost");
            int port = config.getInt("database.mysql.port", 3306);
            String database = config.getString("database.mysql.database", "bixiscore");
            String username = config.getString("database.mysql.username", "root");
            String password = config.getString("database.mysql.password", "");
            int poolSize = config.getInt("database.mysql.pool-size", 10);
            boolean useSsl = config.getBoolean("database.mysql.use-ssl", false);

            hikari.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database
                    + "?useSSL=" + useSsl + "&useUnicode=true&characterEncoding=UTF-8");
            hikari.setUsername(username);
            hikari.setPassword(password);
            hikari.setMaximumPoolSize(poolSize);
        } else {
            // SQLite — plugin klasöründe tek dosya
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists() && !dataFolder.mkdirs()) {
                plugin.getLogger().warning("Plugin klasörü oluşturulamadı: " + dataFolder.getAbsolutePath());
            }
            String fileName = config.getString("database.sqlite.file", "data.db");
            File dbFile = new File(dataFolder, fileName);

            hikari.setDriverClassName("org.sqlite.JDBC");
            hikari.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
            // SQLite tek yazıcıyı sever; yazma kilitlerini önlemek için havuz küçük tutulur.
            hikari.setMaximumPoolSize(1);
        }

        hikari.setConnectionTimeout(10_000L);
        this.dataSource = new HikariDataSource(hikari);
        plugin.getLogger().info("Veritabanı bağlantısı kuruldu (" + type + ").");
    }

    /**
     * Connection pool'u kapatır.
     */
    public void disconnect() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("Veritabanı bağlantısı kapatıldı.");
        }
    }

    public Type getType() {
        return type;
    }

    public boolean isMySQL() {
        return type == Type.MYSQL;
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    // ------------------------------------------------------------------
    //  Tablo oluşturma
    // ------------------------------------------------------------------

    /**
     * Gerekli tabloları (players) oluşturur. Bağlantı kurulur kurulmaz çağrılır;
     * sunucu açılışında bir kez, senkron çalışması sorun değildir.
     */
    public void createTables() {
        // level alanı saklanmaz — XP'den hesaplanır.
        String sql =
                "CREATE TABLE IF NOT EXISTS players (" +
                "  uuid VARCHAR(36) PRIMARY KEY," +
                "  username VARCHAR(16) NOT NULL," +
                "  coin BIGINT NOT NULL DEFAULT 0," +
                "  xp BIGINT NOT NULL DEFAULT 0," +
                "  streak_days INT NOT NULL DEFAULT 0," +
                "  last_daily VARCHAR(32)," +
                "  last_weekly VARCHAR(32)," +
                "  last_monthly VARCHAR(32)" +
                ")";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.executeUpdate();
            plugin.getLogger().info("Veritabanı tabloları hazır.");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Tablolar oluşturulamadı!", e);
        }
    }

    // ------------------------------------------------------------------
    //  Asenkron operasyonlar (Bukkit scheduler)
    // ------------------------------------------------------------------

    /**
     * Asenkron INSERT/UPDATE/DELETE. Etkilenen satır sayısını döner.
     */
    public CompletableFuture<Integer> executeUpdate(String sql, Object... params) {
        CompletableFuture<Integer> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                future.complete(executeUpdateSync(sql, params));
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "executeUpdate hatası: " + sql, e);
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    /**
     * Asenkron SELECT. {@link ResultSet} verilen fonksiyonla dönüştürülür.
     */
    public <T> CompletableFuture<T> executeQuery(String sql, ResultSetFunction<T> handler, Object... params) {
        CompletableFuture<T> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                applyParams(ps, params);
                try (ResultSet rs = ps.executeQuery()) {
                    future.complete(handler.apply(rs));
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "executeQuery hatası: " + sql, e);
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    /**
     * Senkron update — yalnızca sunucu kapanışı gibi scheduler'ın çalışmadığı
     * durumlarda kullanılır (örn. onDisable içinde son kayıt).
     */
    public int executeUpdateSync(String sql, Object... params) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            applyParams(ps, params);
            return ps.executeUpdate();
        }
    }

    private void applyParams(PreparedStatement ps, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            ps.setObject(i + 1, params[i]);
        }
    }
}
