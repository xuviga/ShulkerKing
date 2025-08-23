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
        
        List<String> blacklistedItems = plugin.getConfig().getStringList("item-blacklist.blocked-items");
        String itemType = item.getType().name();
        
        return blacklistedItems.contains(itemType);
    }
    
    /**
     * Handle blacklisted item placement attempt
     */
    public void handleBlacklistedItem(Player player, ItemStack item, InventoryClickEvent event) {
        String action = plugin.getConfig().getString("item-blacklist.action", "CANCEL");
        String message = getBlacklistMessage();
        
        switch (action.toUpperCase()) {
            case "CANCEL":
                // Item placement will be cancelled by the calling method
                player.sendMessage(message);
                plugin.getSoundManager().playBlockedSound(player);
                break;
                
            case "REMOVE":
                // Remove the item from inventory
                player.getInventory().remove(item);
                player.sendMessage(message);
                plugin.getSoundManager().playBlockedSound(player);
                plugin.debugLog("Removed blacklisted item " + item.getType() + " from " + player.getName());
                break;
                
            case "WARN":
                // Just warn the player but allow the action
                player.sendMessage(message);
                break;
                
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
     * Get list of blacklisted items
     */
    public List<String> getBlacklistedItems() {
        return plugin.getConfig().getStringList("item-blacklist.blocked-items");
    }
    
    /**
     * Get blacklist action type
     */
    public String getBlacklistAction() {
        return plugin.getConfig().getString("item-blacklist.action", "CANCEL");
    }
}
