package com.shulkerking.managers;

import com.shulkerking.ShulkerKingPlugin;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.List;

public class WorldManager {
    
    private final ShulkerKingPlugin plugin;
    
    public WorldManager(ShulkerKingPlugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Check if shulker functionality is allowed in player's current world
     */
    public boolean isWorldAllowed(Player player) {
        if (!plugin.getConfig().getBoolean("world-blacklist.enabled", false)) {
            return true;
        }
        
        World world = player.getWorld();
        List<String> blacklistedWorlds = plugin.getConfig().getStringList("world-blacklist.worlds");
        
        return !blacklistedWorlds.contains(world.getName());
    }
    
    /**
     * Get blacklisted world message
     */
    public String getBlacklistMessage() {
        String message = plugin.getConfig().getString("world-blacklist.message", "&cShulker boxes cannot be opened in this world!");
        return plugin.getColorManager().colorize(message);
    }
    
    /**
     * Check if world blacklist is enabled
     */
    public boolean isBlacklistEnabled() {
        return plugin.getConfig().getBoolean("world-blacklist.enabled", false);
    }
    
    /**
     * Get list of blacklisted worlds
     */
    public List<String> getBlacklistedWorlds() {
        return plugin.getConfig().getStringList("world-blacklist.worlds");
    }
}
