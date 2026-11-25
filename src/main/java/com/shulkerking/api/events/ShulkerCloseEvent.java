package com.shulkerking.api.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

/**
 * Вызывается после того, как сессия шалкер-бокса закрыта и содержимое
 * записано обратно в предмет (или сохранено для восстановления).
 */
public class ShulkerCloseEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String sessionId;
    private final ItemStack item;

    public ShulkerCloseEvent(Player player, String sessionId, ItemStack item) {
        this.player = player;
        this.sessionId = sessionId;
        this.item = item;
    }

    public Player getPlayer() {
        return player;
    }

    public String getSessionId() {
        return sessionId;
    }

    /**
     * Предмет, в который записано содержимое (может быть null, если предмет
     * утерян и содержимое сохранено для восстановления при следующем входе).
     */
    public ItemStack getItem() {
        return item;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
