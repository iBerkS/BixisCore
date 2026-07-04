package com.yeditepemc.bixiscore;

import com.yeditepemc.bixiscore.api.BixisCoreAPI;
import com.yeditepemc.bixiscore.database.DatabaseManager;
import com.yeditepemc.bixiscore.event.LevelUpEvent;
import com.yeditepemc.bixiscore.manager.PlayerDataManager;
import com.yeditepemc.bixiscore.model.PlayerData;
import com.yeditepemc.bixiscore.placeholder.BixisCorePlaceholders;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

/**
 * BixisCore ana plugin sınıfı.
 * YeditepeMC ağının merkezi veri ve API kütüphanesi.
 *
 * <p>Diğer pluginler {@code BixisCorePlugin.getAPI()} ile API'ye erişir.
 */
public final class BixisCorePlugin extends JavaPlugin implements Listener {

    private static BixisCorePlugin instance;
    private static Economy economy;

    private DatabaseManager databaseManager;
    private PlayerDataManager playerDataManager;
    private BixisCoreAPI api;

    @Override
    public void onEnable() {
        instance = this;

        // config.yml'i plugin klasörüne kopyala (yoksa)
        saveDefaultConfig();

        // 1) Veritabanı bağlantısı ve tablolar
        this.databaseManager = new DatabaseManager(this);
        databaseManager.connect();
        databaseManager.createTables();

        // 2) Managerler (singleton)
        this.playerDataManager = new PlayerDataManager(this, databaseManager);

        // 3) Vault ekonomi bağlantısı (softdepend — yoksa coin işlemleri devre dışı)
        if (setupEconomy()) {
            getLogger().info("Vault ekonomisine bağlanıldı: " + economy.getName());
        } else {
            getLogger().warning("Vault veya bir ekonomi eklentisi bulunamadı! Coin işlemleri devre dışı.");
        }

        // 4) Public API — hem static getter hem de Bukkit ServicesManager ile erişilebilir
        this.api = new BixisCoreAPI(this, playerDataManager);
        getServer().getServicesManager().register(
                BixisCoreAPI.class,
                api,
                this,
                ServicePriority.Normal
        );

        // 5) Event dinleyicileri
        getServer().getPluginManager().registerEvents(playerDataManager, this);
        getServer().getPluginManager().registerEvents(this, this); // LevelUpEvent (aşağıda)

        // 6) PlaceholderAPI entegrasyonu (softdepend — varsa placeholder'ları kaydet)
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new BixisCorePlaceholders(this).register();
            getLogger().info("PlaceholderAPI bulundu, placeholder'lar kaydedildi (%bixiscore_...%).");
        }

        // Sunucu yeniden yüklendiyse (reload) zaten online olan oyuncuları yükle
        getServer().getOnlinePlayers().forEach(p ->
                playerDataManager.loadPlayer(p.getUniqueId(), p.getName()));

        getLogger().info("BixisCore etkinleştirildi. (v" + getPluginMeta().getVersion() + ")");
    }

    @Override
    public void onDisable() {
        // Cache'deki tüm oyuncuları senkron kaydet (scheduler artık çalışmıyor)
        if (playerDataManager != null) {
            playerDataManager.saveAllSync();
        }
        // Bağlantıyı kapat
        if (databaseManager != null) {
            databaseManager.disconnect();
        }
        getLogger().info("BixisCore devre dışı bırakıldı.");
        economy = null;
        instance = null;
    }

    /**
     * Vault üzerinden kayıtlı Economy sağlayıcısını bulur.
     *
     * @return bağlanıldıysa {@code true}
     */
    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        economy = rsp.getProvider();
        return economy != null;
    }

    /**
     * Vault Economy sağlayıcısı. Vault yoksa {@code null} döner.
     */
    public static Economy getEconomy() {
        return economy;
    }

    /**
     * Plugin örneği (singleton).
     */
    public static BixisCorePlugin getInstance() {
        return instance;
    }

    /**
     * Diğer pluginlerin kullandığı public API.
     */
    public static BixisCoreAPI getAPI() {
        if (instance == null) {
            throw new IllegalStateException("BixisCore henüz etkin değil!");
        }
        return instance.api;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }

    // ==================================================================
    //  Seviye atlama bildirimi
    // ==================================================================

    @EventHandler
    public void onLevelUp(LevelUpEvent event) {
        Player player = event.getPlayer();
        player.sendMessage("§6✦ §eSeviye atladın! §6Yeni seviyeniz: §f"
                + event.getNewLevel() + " §6✦");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
    }

    // ==================================================================
    //  GEÇİCİ TEST KOMUTU — sürüm öncesi kaldırılacak (bkz. CLAUDE.md)
    // ==================================================================

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!command.getName().equalsIgnoreCase("bixiscore")) {
            return false;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cBu komut yalnızca oyuncular tarafından kullanılabilir.");
            return true;
        }

        if (args.length == 0) {
            sendUsage(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "info" -> handleInfo(player);
            case "addxp" -> handleAddXp(player, args);
            case "addcoin" -> handleAddCoin(player, args);
            case "reset" -> handleReset(player);
            default -> sendUsage(player);
        }
        return true;
    }

    private void sendUsage(Player player) {
        player.sendMessage("§6BixisCore §7(geçici test komutu):");
        player.sendMessage("§e/bixiscore info §7- coin, xp, level, streak bilgin");
        player.sendMessage("§e/bixiscore addxp <miktar> §7- kendine XP ekle");
        player.sendMessage("§e/bixiscore addcoin <miktar> §7- kendine coin ekle");
        player.sendMessage("§e/bixiscore reset §7- verini sıfırla");
    }

    private void handleInfo(Player player) {
        PlayerData data = api.getPlayerData(player);
        if (data == null) {
            player.sendMessage("§cVerin henüz yüklenmedi, birazdan tekrar dene.");
            return;
        }
        String coinStr = economy != null
                ? String.valueOf((long) economy.getBalance(player))
                : "§8(Vault yok)";
        player.sendMessage("§6§l» BixisCore Bilgilerin");
        player.sendMessage("§7Coin: §e" + coinStr);
        player.sendMessage("§7XP: §e" + data.getXp()
                + " §7(sonraki seviyeye §e" + data.getXpToNextLevel() + " §7XP)");
        player.sendMessage("§7Seviye: §e" + data.getLevel() + "§7/§e" + PlayerData.MAX_LEVEL);
        player.sendMessage("§7Streak: §e" + data.getStreakDays() + " §7gün");
    }

    private void handleAddXp(Player player, String[] args) {
        Long amount = parseAmount(player, args);
        if (amount == null) {
            return;
        }
        api.addXP(player, amount);
    }

    private void handleAddCoin(Player player, String[] args) {
        Long amount = parseAmount(player, args);
        if (amount == null) {
            return;
        }
        api.addCoins(player, amount);
    }

    private void handleReset(Player player) {
        PlayerData data = api.getPlayerData(player);
        if (data == null) {
            player.sendMessage("§cVerin henüz yüklenmedi, birazdan tekrar dene.");
            return;
        }
        data.setXp(0);
        data.setStreakDays(0);
        data.setLastDaily(null);
        data.setLastWeekly(null);
        data.setLastMonthly(null);
        playerDataManager.savePlayer(data);

        // Coin artık Vault'ta — bakiyeyi sıfıra çek
        if (economy != null) {
            double balance = economy.getBalance(player);
            if (balance > 0) {
                economy.withdrawPlayer(player, balance);
            }
        }
        player.sendMessage("§aVerin sıfırlandı. §7(coin, xp, level, streak)");
    }

    private Long parseAmount(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cKullanım: §e/bixiscore " + args[0].toLowerCase() + " <miktar>");
            return null;
        }
        try {
            long amount = Long.parseLong(args[1]);
            if (amount <= 0) {
                player.sendMessage("§cMiktar 0'dan büyük olmalı.");
                return null;
            }
            return amount;
        } catch (NumberFormatException ex) {
            player.sendMessage("§cGeçersiz sayı: §e" + args[1]);
            return null;
        }
    }
}
