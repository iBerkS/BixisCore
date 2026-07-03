package com.yeditepemc.bixiscore.manager;

import com.yeditepemc.bixiscore.BixisCorePlugin;
import com.yeditepemc.bixiscore.database.DatabaseManager;
import com.yeditepemc.bixiscore.model.PlayerData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Oyuncu verisini yöneten singleton.
 * Aktif oyuncuların verisi bellekte (cache) tutulur; giriş/çıkışta
 * asenkron olarak yüklenir/kaydedilir.
 */
public class PlayerDataManager implements Listener {

    private static PlayerDataManager instance;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final BixisCorePlugin plugin;
    private final DatabaseManager database;
    private final ConcurrentHashMap<UUID, PlayerData> cache = new ConcurrentHashMap<>();

    public PlayerDataManager(BixisCorePlugin plugin, DatabaseManager database) {
        this.plugin = plugin;
        this.database = database;
        instance = this;
    }

    public static PlayerDataManager getInstance() {
        return instance;
    }

    // ------------------------------------------------------------------
    //  Cache erişimi
    // ------------------------------------------------------------------

    /**
     * Cache'deki oyuncu verisini döner. Oyuncu online değilse (yüklenmemişse)
     * {@code null} döner.
     */
    public PlayerData getPlayer(UUID uuid) {
        return cache.get(uuid);
    }

    public boolean isCached(UUID uuid) {
        return cache.containsKey(uuid);
    }

    // ------------------------------------------------------------------
    //  Yükleme / Kaydetme (asenkron)
    // ------------------------------------------------------------------

    /**
     * Oyuncu verisini veritabanından asenkron yükler ve cache'e koyar.
     * Kayıt yoksa varsayılan yeni bir veri oluşturulur ve kaydedilir.
     */
    public CompletableFuture<PlayerData> loadPlayer(UUID uuid, String username) {
        String sql = "SELECT * FROM players WHERE uuid = ?";
        return database.executeQuery(sql, rs -> {
            if (rs.next()) {
                return new PlayerData(
                        uuid,
                        rs.getString("username"),
                        rs.getLong("coin"),
                        rs.getLong("xp"),
                        rs.getInt("streak_days"),
                        parse(rs.getString("last_daily")),
                        parse(rs.getString("last_weekly")),
                        parse(rs.getString("last_monthly"))
                );
            }
            return null;
        }, uuid.toString()).thenApply(loaded -> {
            PlayerData data = loaded;
            if (data == null) {
                data = new PlayerData(uuid, username);
                savePlayer(data); // ilk kaydı oluştur
            } else {
                data.setUsername(username); // isim değişmiş olabilir
            }
            cache.put(uuid, data);
            return data;
        });
    }

    /**
     * Oyuncu verisini asenkron kaydeder (upsert).
     */
    public CompletableFuture<Integer> savePlayer(PlayerData data) {
        return database.executeUpdate(
                upsertSql(),
                data.getUuid().toString(),
                data.getUsername(),
                data.getCoin(),
                data.getXp(),
                data.getStreakDays(),
                format(data.getLastDaily()),
                format(data.getLastWeekly()),
                format(data.getLastMonthly())
        );
    }

    /**
     * Tüm cache'i senkron kaydeder. Yalnızca sunucu kapanışında (onDisable)
     * kullanılır — o sırada Bukkit scheduler asenkron görev kabul etmez.
     */
    public void saveAllSync() {
        for (PlayerData data : cache.values()) {
            try {
                database.executeUpdateSync(
                        upsertSql(),
                        data.getUuid().toString(),
                        data.getUsername(),
                        data.getCoin(),
                        data.getXp(),
                        data.getStreakDays(),
                        format(data.getLastDaily()),
                        format(data.getLastWeekly()),
                        format(data.getLastMonthly())
                );
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE,
                        "Kapanışta oyuncu kaydedilemedi: " + data.getUsername(), e);
            }
        }
        cache.clear();
    }

    /**
     * Veritabanı tipine göre upsert cümlesi üretir.
     */
    private String upsertSql() {
        if (database.isMySQL()) {
            return "INSERT INTO players " +
                   "(uuid, username, coin, xp, streak_days, last_daily, last_weekly, last_monthly) " +
                   "VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
                   "ON DUPLICATE KEY UPDATE " +
                   "username = VALUES(username), coin = VALUES(coin), xp = VALUES(xp), " +
                   "streak_days = VALUES(streak_days), last_daily = VALUES(last_daily), " +
                   "last_weekly = VALUES(last_weekly), last_monthly = VALUES(last_monthly)";
        }
        // SQLite
        return "INSERT INTO players " +
               "(uuid, username, coin, xp, streak_days, last_daily, last_weekly, last_monthly) " +
               "VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
               "ON CONFLICT(uuid) DO UPDATE SET " +
               "username = excluded.username, coin = excluded.coin, xp = excluded.xp, " +
               "streak_days = excluded.streak_days, last_daily = excluded.last_daily, " +
               "last_weekly = excluded.last_weekly, last_monthly = excluded.last_monthly";
    }

    // ------------------------------------------------------------------
    //  Event dinleyicileri
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        String name = event.getPlayer().getName();
        loadPlayer(uuid, name).exceptionally(ex -> {
            plugin.getLogger().log(Level.SEVERE, "Oyuncu verisi yüklenemedi: " + name, ex);
            return null;
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        PlayerData data = cache.remove(uuid);
        if (data != null) {
            savePlayer(data).exceptionally(ex -> {
                plugin.getLogger().log(Level.SEVERE,
                        "Çıkışta oyuncu kaydedilemedi: " + data.getUsername(), ex);
                return null;
            });
        }
    }

    // ------------------------------------------------------------------
    //  Yardımcılar
    // ------------------------------------------------------------------

    private static LocalDateTime parse(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        return LocalDateTime.parse(value, FORMATTER);
    }

    private static String format(LocalDateTime value) {
        return value == null ? null : value.format(FORMATTER);
    }
}
