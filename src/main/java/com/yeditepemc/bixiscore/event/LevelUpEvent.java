package com.yeditepemc.bixiscore.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Bir oyuncu XP kazanarak seviye atladığında fırlatılır.
 * Diğer pluginler (BixisRewards, BixisAchievements vb.) bu event'i dinleyebilir.
 * Her zaman ana thread'de çağrılır.
 */
public class LevelUpEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final int oldLevel;
    private final int newLevel;

    public LevelUpEvent(Player player, int oldLevel, int newLevel) {
        this.player = player;
        this.oldLevel = oldLevel;
        this.newLevel = newLevel;
    }

    public Player getPlayer() {
        return player;
    }

    public int getOldLevel() {
        return oldLevel;
    }

    public int getNewLevel() {
        return newLevel;
    }

    /** Bu atlamada kaç seviye kazanıldığı (aynı anda birden fazla olabilir). */
    public int getLevelsGained() {
        return newLevel - oldLevel;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
