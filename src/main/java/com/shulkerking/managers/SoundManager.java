package com.shulkerking.managers;

import com.shulkerking.ShulkerKingPlugin;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

public class SoundManager {
    
    private final ShulkerKingPlugin plugin;
    
    public SoundManager(ShulkerKingPlugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Play sound if sounds are enabled
     */
    private void playSound(Player player, String soundType) {
        if (!plugin.getConfig().getBoolean("sounds.enabled", true)) {
            return;
        }
        
        String soundPath = "sounds." + soundType;
        String soundName = plugin.getConfig().getString(soundPath + ".sound", "");
        float volume = (float) plugin.getConfig().getDouble(soundPath + ".volume", 1.0);
        float pitch = (float) plugin.getConfig().getDouble(soundPath + ".pitch", 1.0);
        
        if (soundName.isEmpty()) {
            return;
        }
        
        try {
            Sound sound = Sound.valueOf(soundName);
            player.playSound(player.getLocation(), sound, volume, pitch);
            plugin.debugLog("Played sound " + soundName + " for " + player.getName());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid sound name: " + soundName);
        }
    }
    
    /**
     * Play shulker box opening sound
     */
    public void playOpenSound(Player player) {
        playSound(player, "open");
    }
    
    /**
     * Play shulker box closing sound
     */
    public void playCloseSound(Player player) {
        playSound(player, "close");
    }
    
    /**
     * Play cooldown active sound
     */
    public void playCooldownSound(Player player) {
        playSound(player, "cooldown");
    }
    
    /**
     * Play blocked action sound
     */
    public void playBlockedSound(Player player) {
        playSound(player, "blocked");
    }
    
    /**
     * Check if sounds are enabled
     */
    public boolean isSoundsEnabled() {
        return plugin.getConfig().getBoolean("sounds.enabled", true);
    }
}
