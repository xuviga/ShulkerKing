package com.shulkerking.holders;

import com.shulkerking.managers.ShulkerInventoryManager;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Кастомный InventoryHolder для шалкер-боксов
 * Позволяет идентифицировать инвентарь шалкера без проверки по title
 */
public class ShulkerInventoryHolder implements InventoryHolder {
    
    private final Player player;
    private final ShulkerInventoryManager.ShulkerSession session;
    private Inventory inventory;
    
    public ShulkerInventoryHolder(Player player, ShulkerInventoryManager.ShulkerSession session) {
        this.player = player;
        this.session = session;
    }
    
    @Override
    public Inventory getInventory() {
        return inventory;
    }
    
    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }
    
    /**
     * Получить игрока, который открыл шалкер
     */
    public Player getPlayer() {
        return player;
    }
    
    /**
     * Получить сессию шалкера
     */
    public ShulkerInventoryManager.ShulkerSession getSession() {
        return session;
    }
    
    /**
     * Проверить, принадлежит ли этот holder указанному игроку
     */
    public boolean belongsTo(Player player) {
        return this.player != null && this.player.getUniqueId().equals(player.getUniqueId());
    }
    
    /**
     * Проверить, является ли этот holder активным для сессии
     */
    public boolean isActiveFor(ShulkerInventoryManager.ShulkerSession session) {
        return this.session != null && 
               this.session.getPlayerId().equals(session.getPlayerId()) &&
               this.session.getStartTime() == session.getStartTime();
    }
}
