package com.yeditepemc.bixiscore.api;

import com.yeditepemc.bixiscore.BixisCorePlugin;
import com.yeditepemc.bixiscore.event.LevelUpEvent;
import com.yeditepemc.bixiscore.manager.PlayerDataManager;
import com.yeditepemc.bixiscore.model.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Diğer pluginlerin (BixisRewards, BixisAchievements, BixisNavigator vb.)
 * kullandığı public API. {@code BixisCorePlugin.getAPI()} ile erişilir.
 *
 * <p>Tüm mesajlar Türkçedir (CLAUDE.md — Dil).
 */
public class BixisCoreAPI {

    private final BixisCorePlugin plugin;
    private final PlayerDataManager dataManager;

    public BixisCoreAPI(BixisCorePlugin plugin, PlayerDataManager dataManager) {
        this.plugin = plugin;
        this.dataManager = dataManager;
    }

    // ------------------------------------------------------------------
    //  XP / Level
    // ------------------------------------------------------------------

    /**
     * Oyuncuya XP ekler, seviyeyi yeniden hesaplar ve seviye atlandıysa
     * {@link LevelUpEvent} fırlatır.
     *
     * @return XP eklenebildiyse {@code true} (oyuncu verisi cache'de yüklüyse)
     */
    public boolean addXP(Player player, long amount) {
        if (amount <= 0) {
            return false;
        }
        PlayerData data = getPlayerData(player);
        if (data == null) {
            return false;
        }

        int oldLevel = data.getLevel();
        data.addXp(amount);
        int newLevel = data.getLevel();
        dataManager.savePlayer(data);

        player.sendMessage("§a+§e" + amount + " §aXP kazandın!");

        if (newLevel > oldLevel) {
            // Seviye atlama bildirimi (mesaj + ses) LevelUpEvent dinleyicisine aittir.
            // Event her zaman ana thread'de çağrılır.
            LevelUpEvent event = new LevelUpEvent(player, oldLevel, newLevel);
            if (Bukkit.isPrimaryThread()) {
                Bukkit.getPluginManager().callEvent(event);
            } else {
                Bukkit.getScheduler().runTask(plugin, () -> Bukkit.getPluginManager().callEvent(event));
            }
        }
        return true;
    }

    /**
     * Oyuncunun mevcut seviyesi. Veri yüklü değilse {@code 1} döner.
     */
    public int getLevel(Player player) {
        PlayerData data = getPlayerData(player);
        return data == null ? 1 : data.getLevel();
    }

    /**
     * Oyuncunun toplam XP'si. Veri yüklü değilse {@code 0} döner.
     */
    public long getXP(Player player) {
        PlayerData data = getPlayerData(player);
        return data == null ? 0L : data.getXp();
    }

    // ------------------------------------------------------------------
    //  Coin (ekonomi)
    // ------------------------------------------------------------------

    /**
     * Oyuncunun hesabına coin ekler.
     */
    public boolean addCoins(Player player, long amount) {
        if (amount <= 0) {
            return false;
        }
        PlayerData data = getPlayerData(player);
        if (data == null) {
            return false;
        }
        data.setCoin(data.getCoin() + amount);
        dataManager.savePlayer(data);
        player.sendMessage("§a+§e" + amount + " coin §ahesabına eklendi!");
        return true;
    }

    /**
     * Oyuncudan coin siler. Yeterli coin yoksa işlem yapılmaz ve {@code false} döner.
     */
    public boolean removeCoins(Player player, long amount) {
        if (amount <= 0) {
            return false;
        }
        PlayerData data = getPlayerData(player);
        if (data == null) {
            return false;
        }
        if (data.getCoin() < amount) {
            player.sendMessage("§cYeterli coinin yok! §7(Gerekli: §e" + amount
                    + "§7, Mevcut: §e" + data.getCoin() + "§7)");
            return false;
        }
        data.setCoin(data.getCoin() - amount);
        dataManager.savePlayer(data);
        player.sendMessage("§c-§e" + amount + " coin §chesabından düşüldü.");
        return true;
    }

    /**
     * Oyuncunun mevcut coin bakiyesi. Veri yüklü değilse {@code 0} döner.
     */
    public long getCoins(Player player) {
        PlayerData data = getPlayerData(player);
        return data == null ? 0L : data.getCoin();
    }

    // ------------------------------------------------------------------
    //  Ham veri
    // ------------------------------------------------------------------

    /**
     * Oyuncunun cache'deki verisini döner. Oyuncu online değilse {@code null}.
     */
    public PlayerData getPlayerData(Player player) {
        return dataManager.getPlayer(player.getUniqueId());
    }
}
