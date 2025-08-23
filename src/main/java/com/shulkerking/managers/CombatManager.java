package com.shulkerking.managers;

import com.shulkerking.ShulkerKingPlugin;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CombatManager {
    
    private final ShulkerKingPlugin plugin;
    private final Map<UUID, Long> combatData;
    
    public CombatManager(ShulkerKingPlugin plugin) {
        this.plugin = plugin;
        this.combatData = new HashMap<>();
    }
    
    public void markInCombat(Player player) {
        combatData.put(player.getUniqueId(), System.currentTimeMillis());
        plugin.debugLog("Player " + player.getName() + " marked as in combat");
    }
    
    public boolean isInCombat(Player player) {
        if (!plugin.getConfig().getBoolean("settings.pvp-block", false)) {
            return false;
        }
        
        Long lastCombatTime = combatData.get(player.getUniqueId());
        if (lastCombatTime == null) {
            return false;
        }
        
        long combatTimeout = plugin.getConfig().getLong("settings.pvp-timeout", 10) * 1000;
        boolean inCombat = (System.currentTimeMillis() - lastCombatTime) < combatTimeout;
        
        if (!inCombat) {
            combatData.remove(player.getUniqueId());
        }
        
        return inCombat;
    }
    
    public void removeCombat(Player player) {
        combatData.remove(player.getUniqueId());
        plugin.debugLog("Player " + player.getName() + " removed from combat");
    }
    
    public void clearCombatData() {
        combatData.clear();
        plugin.debugLog("Combat data cleared");
    }
    
    /**
     * Clears all combat data for all players
     * Alias for clearCombatData() for compatibility
     */
    public void clearAllCombat() {
        clearCombatData();
    }
    
    /**
     * Gets the number of players currently in combat
     * @return number of players in combat
     */
    public int getCombatPlayersCount() {
        // Remove expired combat entries
        long currentTime = System.currentTimeMillis();
        long combatTimeout = plugin.getConfig().getLong("settings.pvp-timeout", 10) * 1000;
        
        combatData.entrySet().removeIf(entry -> 
            (currentTime - entry.getValue()) >= combatTimeout);
        
        return combatData.size();
    }
    
    /**
     * Checks if combat blocking is enabled in configuration
     * @return true if PvP blocking is enabled
     */
    public boolean isCombatBlockingEnabled() {
        return plugin.getConfig().getBoolean("settings.pvp-block", false);
    }
    
    /**
     * Cleanup expired combat entries from memory
     */
    public void cleanupExpiredCombat() {
        if (!isCombatBlockingEnabled()) {
            return;
        }
        
        long currentTime = System.currentTimeMillis();
        long combatTimeout = plugin.getConfig().getLong("settings.pvp-timeout", 10) * 1000;
        
        combatData.entrySet().removeIf(entry -> 
            (currentTime - entry.getValue()) >= combatTimeout);
        
        plugin.debugLog("Cleaned up expired combat entries");
    }
}
