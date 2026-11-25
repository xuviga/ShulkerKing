package com.shulkerking.managers;

import com.shulkerking.ShulkerKingPlugin;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class ItemBlacklistManager {
    
    private final ShulkerKingPlugin plugin;
    
    public ItemBlacklistManager(ShulkerKingPlugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Check if item is blacklisted from being placed in shulker boxes
     */
    public boolean isBlacklisted(ItemStack item) {
        if (!plugin.getConfig().getBoolean("item-blacklist.enabled", false)) {
            return false;
        }
        
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        
        List<String> blacklistedItems = getBlacklistedItems();
        String itemType = item.getType().name();
        
        return blacklistedItems.contains(itemType);
    }
    
    /**
     * Handle blacklisted item placement attempt.
     * Вызывается после event.setCancelled(true) - блокировка гарантирована.
     */
    public void handleBlacklistedItem(Player player, ItemStack item, InventoryClickEvent event) {
        String action = getBlacklistAction();
        String message = getBlacklistMessage();
        
        switch (action.toUpperCase()) {
            case "REMOVE":
                // Удаляем только тот предмет, который игрок пытался положить (в курсоре),
                // а не первый найденный в инвентаре.
                if (event != null && event.getCursor() != null) {
                    event.getCursor().setAmount(0);
                }
                player.sendMessage(message);
                plugin.getSoundManager().playBlockedSound(player);
                plugin.debugLog("Removed blacklisted item " + item.getType() + " from " + player.getName());
                break;
                
            case "WARN":
                player.sendMessage(message);
                break;
                
            case "BLOCK":
            default:
                player.sendMessage(message);
                plugin.getSoundManager().playBlockedSound(player);
                break;
        }
    }
    
    /**
     * Get blacklisted item message
     */
    public String getBlacklistMessage() {
        String message = plugin.getConfig().getString("item-blacklist.message", "&cThis item cannot be placed in shulker boxes!");
        return plugin.getColorManager().error(message);
    }
    
    /**
     * Check if item blacklist is enabled
     */
    public boolean isBlacklistEnabled() {
        return plugin.getConfig().getBoolean("item-blacklist.enabled", false);
    }
    
    /**
     * Get list of blacklisted items (поддержаны оба имени ключа: items и blocked-items)
     */
    public List<String> getBlacklistedItems() {
        if (plugin.getConfig().isSet("item-blacklist.items")) {
            return plugin.getConfig().getStringList("item-blacklist.items");
        }
        return plugin.getConfig().getStringList("item-blacklist.blocked-items");
    }
    
    /**
     * Get blacklist action type (поддержаны mode и action)
     */
    public String getBlacklistAction() {
        if (plugin.getConfig().isSet("item-blacklist.mode")) {
            return plugin.getConfig().getString("item-blacklist.mode", "BLOCK");
        }
        return plugin.getConfig().getString("item-blacklist.action", "BLOCK");
    }
}
