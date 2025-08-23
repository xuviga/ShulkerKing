package com.shulkerking.managers;

import com.shulkerking.ShulkerKingPlugin;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CooldownManager {
    
    private final ShulkerKingPlugin plugin;
    private final Map<UUID, Long> cooldowns;
    
    public CooldownManager(ShulkerKingPlugin plugin) {
        this.plugin = plugin;
        this.cooldowns = new HashMap<>();
    }
    
    /**
     * Check if player has an active cooldown
     */
    public boolean hasCooldown(Player player) {
        if (!plugin.getConfig().getBoolean("cooldown.enabled", true)) {
            return false;
        }
        
        UUID playerId = player.getUniqueId();
        if (!cooldowns.containsKey(playerId)) {
            return false;
        }
        
        long cooldownEnd = cooldowns.get(playerId);
        long currentTime = System.currentTimeMillis();
        
        if (currentTime >= cooldownEnd) {
            cooldowns.remove(playerId);
            return false;
        }
        
        return true;
    }
    
    /**
     * Get remaining cooldown time in seconds
     */
    public double getRemainingCooldown(Player player) {
        if (!hasCooldown(player)) {
            return 0.0;
        }
        
        UUID playerId = player.getUniqueId();
        long cooldownEnd = cooldowns.get(playerId);
        long currentTime = System.currentTimeMillis();
        
        return (cooldownEnd - currentTime) / 1000.0;
    }
    
    /**
     * Set cooldown for player
     */
    public void setCooldown(Player player) {
        if (!plugin.getConfig().getBoolean("cooldown.enabled", true)) {
            return;
        }
        
        double cooldownTime = getCooldownTime(player);
        if (cooldownTime <= 0) {
            return;
        }
        
        UUID playerId = player.getUniqueId();
        long cooldownEnd = System.currentTimeMillis() + (long) (cooldownTime * 1000);
        cooldowns.put(playerId, cooldownEnd);
        
        plugin.debugLog("Set cooldown for " + player.getName() + " for " + cooldownTime + " seconds");
    }
    
    /**
     * Get cooldown time for specific player based on permissions
     */
    public double getCooldownTime(Player player) {
        // Check permission-based cooldown overrides
        if (player.hasPermission("shulkerking.cooldown.bypass")) {
            return 0.0;
        }
        
        if (player.hasPermission("shulkerking.cooldown.premium")) {
            return plugin.getConfig().getDouble("cooldown.premium", 1.0);
        }
        
        if (player.hasPermission("shulkerking.cooldown.vip")) {
            return plugin.getConfig().getDouble("cooldown.vip", 2.0);
        }
        
        // Default cooldown
        return plugin.getConfig().getDouble("cooldown.default", 3.0);
    }
    
    /**
     * Remove cooldown for player
     */
    public void removeCooldown(Player player) {
        cooldowns.remove(player.getUniqueId());
    }
    
    /**
     * Clear all cooldowns
     */
    public void clearAllCooldowns() {
        cooldowns.clear();
        plugin.debugLog("Cleared all cooldowns");
    }
    
    /**
     * Get formatted cooldown message
     */
    public String getCooldownMessage(Player player) {
        double remaining = getRemainingCooldown(player);
        String format = plugin.getConfig().getString("cooldown.visual-display.lore", "&7Кулдаун: &c{time} сек");
        return format.replace("{time}", String.format("%.1f", remaining));
    }
    
    /**
     * Check if visual cooldown is enabled
     */
    public boolean isVisualCooldownEnabled() {
        return plugin.getConfig().getBoolean("cooldown.visual-display.enabled", true);
    }
    
    /**
     * Check if cooldown should be shown on item
     */
    public boolean shouldShowOnItem() {
        return plugin.getConfig().getBoolean("cooldown.visual-display.show-progress", true);
    }
    
    /**
     * Cleanup expired cooldowns from memory
     */
    public void cleanupExpiredCooldowns() {
        long currentTime = System.currentTimeMillis();
        cooldowns.entrySet().removeIf(entry -> currentTime >= entry.getValue());
        plugin.debugLog("Cleaned up expired cooldowns");
    }
    
    /**
     * Get number of active cooldowns
     */
    public int getActiveCooldownsCount() {
        cleanupExpiredCooldowns();
        return cooldowns.size();
    }
}
