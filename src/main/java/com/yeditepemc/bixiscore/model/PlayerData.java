package com.yeditepemc.bixiscore.model;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Bir oyuncunun kalıcı verisini temsil eden model.
 * {@code level} alanı kalıcı olarak saklanmaz; her zaman XP'den hesaplanır
 * (bkz. {@link #calculateLevel(long)}).
 */
public class PlayerData {

    /** XP eğrisinin üst seviye sınırı (CLAUDE.md — Cap: 50). */
    public static final int MAX_LEVEL = 50;

    private final UUID uuid;
    private String username;

    private long coin;
    private long xp;
    private int level;
    private int streakDays;

    private LocalDateTime lastDaily;
    private LocalDateTime lastWeekly;
    private LocalDateTime lastMonthly;

    /**
     * Yeni (veritabanında bulunmayan) bir oyuncu için varsayılan veri.
     */
    public PlayerData(UUID uuid, String username) {
        this(uuid, username, 0L, 0L, 0, null, null, null);
    }

    /**
     * Veritabanından yüklenen tam veri.
     */
    public PlayerData(UUID uuid, String username, long coin, long xp, int streakDays,
                      LocalDateTime lastDaily, LocalDateTime lastWeekly, LocalDateTime lastMonthly) {
        this.uuid = uuid;
        this.username = username;
        this.coin = coin;
        this.xp = xp;
        this.streakDays = streakDays;
        this.lastDaily = lastDaily;
        this.lastWeekly = lastWeekly;
        this.lastMonthly = lastMonthly;
        this.level = calculateLevel(xp);
    }

    // ------------------------------------------------------------------
    //  XP eğrisi (CLAUDE.md)
    //    Level 1-10 : 500  XP/level
    //    Level 11-25: 1000 XP/level
    //    Level 26-40: 2000 XP/level
    //    Level 41-50: 3500 XP/level
    //    Cap: 50
    // ------------------------------------------------------------------

    /**
     * Toplam biriktirilmiş XP'den mevcut seviyeyi hesaplar.
     * Oyuncular seviye 1'de (0 XP) başlar ve {@link #MAX_LEVEL} ile sınırlanır.
     */
    public static int calculateLevel(long xp) {
        int level = 1;
        long remaining = Math.max(0L, xp);
        while (level < MAX_LEVEL) {
            long cost = xpForNextLevel(level);
            if (remaining < cost) {
                break;
            }
            remaining -= cost;
            level++;
        }
        return level;
    }

    /**
     * Verilen seviyeden bir sonrakine geçmek için gereken XP miktarı.
     * (Örn. {@code xpForNextLevel(1)} = seviye 1 → 2 için gereken XP.)
     */
    public static long xpForNextLevel(int level) {
        if (level < 1) {
            return 500L;
        }
        if (level <= 10) {
            return 500L;
        }
        if (level <= 25) {
            return 1000L;
        }
        if (level <= 40) {
            return 2000L;
        }
        return 3500L;
    }

    /**
     * Mevcut seviyenin başlangıcından bu yana biriken XP (ilerleme çubuğu için).
     */
    public long getXpIntoLevel() {
        long remaining = Math.max(0L, xp);
        int lvl = 1;
        while (lvl < MAX_LEVEL) {
            long cost = xpForNextLevel(lvl);
            if (remaining < cost) {
                break;
            }
            remaining -= cost;
            lvl++;
        }
        return remaining;
    }

    /** Bir sonraki seviyeye geçmek için gereken toplam XP (cap'te 0). */
    public long getXpToNextLevel() {
        if (level >= MAX_LEVEL) {
            return 0L;
        }
        return xpForNextLevel(level) - getXpIntoLevel();
    }

    // ------------------------------------------------------------------
    //  Getter / Setter
    // ------------------------------------------------------------------

    public UUID getUuid() {
        return uuid;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public long getCoin() {
        return coin;
    }

    public void setCoin(long coin) {
        this.coin = Math.max(0L, coin);
    }

    public long getXp() {
        return xp;
    }

    /** XP'yi ayarlar ve seviyeyi yeniden hesaplar. */
    public void setXp(long xp) {
        this.xp = Math.max(0L, xp);
        this.level = calculateLevel(this.xp);
    }

    /** XP ekler ve seviyeyi yeniden hesaplar. */
    public void addXp(long amount) {
        setXp(this.xp + amount);
    }

    /** Kalıcı olarak saklanmayan, XP'den hesaplanan seviye. */
    public int getLevel() {
        return level;
    }

    public int getStreakDays() {
        return streakDays;
    }

    public void setStreakDays(int streakDays) {
        this.streakDays = Math.max(0, streakDays);
    }

    public LocalDateTime getLastDaily() {
        return lastDaily;
    }

    public void setLastDaily(LocalDateTime lastDaily) {
        this.lastDaily = lastDaily;
    }

    public LocalDateTime getLastWeekly() {
        return lastWeekly;
    }

    public void setLastWeekly(LocalDateTime lastWeekly) {
        this.lastWeekly = lastWeekly;
    }

    public LocalDateTime getLastMonthly() {
        return lastMonthly;
    }

    public void setLastMonthly(LocalDateTime lastMonthly) {
        this.lastMonthly = lastMonthly;
    }
}
