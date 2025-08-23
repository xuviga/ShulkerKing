package com.shulkerking.managers;

import com.shulkerking.ShulkerKingPlugin;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CooldownDisplayManager {
    
    private final ShulkerKingPlugin plugin;
    private final Map<UUID, BukkitRunnable> activeCountdowns;
    
    public CooldownDisplayManager(ShulkerKingPlugin plugin) {
        this.plugin = plugin;
        this.activeCountdowns = new ConcurrentHashMap<>();
    }
    
    /**
     * Apply visual cooldown display to a shulker box item
     */
    public void applyCooldownDisplay(Player player, ItemStack item, long remainingTime) {
        if (!plugin.getConfig().getBoolean("cooldown.visual-display.enabled", true)) {
            return;
        }
        
        if (item == null || !isShulkerBox(item)) {
            return;
        }
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        
        // Clean existing cooldown display first
        cleanExistingCooldownDisplay(meta);
        
        // Store cleaned display name and lore as original
        String originalName = meta.hasDisplayName() ? meta.getDisplayName() : null;
        List<String> originalLore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        
        // Get actual remaining time from CooldownManager instead of using parameter
        if (!plugin.getCooldownManager().hasCooldown(player)) {
            return;
        }
        
        double actualRemainingSeconds = plugin.getCooldownManager().getRemainingCooldown(player);
        long actualRemainingTicks = (long) (actualRemainingSeconds * 20);
        
        // Apply cooldown display with actual time
        updateCooldownDisplay(meta, actualRemainingTicks, originalName, originalLore);
        item.setItemMeta(meta);
        
        // Don't schedule removal - let live update handle it
        // The live update system will automatically remove the display when cooldown ends
    }
    
    /**
     * Update the item's display with cooldown information
     */
    private void updateCooldownDisplay(ItemMeta meta, long remainingTime, String originalName, List<String> originalLore) {
        String cooldownLore = plugin.getConfig().getString("cooldown.visual-display.lore", "&7Cooldown: &c{time} seconds");
        
        long seconds = remainingTime / 20; // Convert ticks to seconds
        
        // Keep original display name unchanged
        if (originalName != null) {
            meta.setDisplayName(originalName);
        }
        
        // Update only lore with cooldown information
        List<String> newLore = new ArrayList<>(originalLore);
        String loreText = cooldownLore.replace("{time}", String.valueOf(seconds));
        loreText = plugin.getColorManager().colorize(loreText);
        newLore.add(0, loreText); // Add at the beginning
        meta.setLore(newLore);
    }
    
    /**
     * Remove cooldown display from item
     */
    public void removeCooldownDisplay(Player player, ItemStack item, String originalName, List<String> originalLore) {
        if (item == null || !isShulkerBox(item)) {
            return;
        }
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        
        // Restore original display name
        if (originalName != null) {
            meta.setDisplayName(originalName);
        } else {
            meta.setDisplayName(null);
        }
        
        // Restore original lore
        meta.setLore(originalLore.isEmpty() ? null : originalLore);
        item.setItemMeta(meta);
        
        // Update player's inventory
        player.updateInventory();
    }
    
    /**
     * Clean existing cooldown display from item meta
     */
    private void cleanExistingCooldownDisplay(ItemMeta meta) {
        if (meta.hasLore()) {
            List<String> lore = meta.getLore();
            List<String> cleanLore = new ArrayList<>();
            
            for (String line : lore) {
                // Skip lines that contain cooldown information or ready message
                String cleanLine = line.replaceAll("§[0-9a-fk-or]", ""); // Remove color codes
                if (!cleanLine.contains("Cooldown:") && 
                    !cleanLine.contains("cooldown:") && 
                    !cleanLine.contains("Кулдаун:") &&
                    !cleanLine.contains("кулдаун:") &&
                    !cleanLine.contains("Готов к открытию") &&
                    !cleanLine.contains("Ready to open") &&
                    !cleanLine.contains("✓ Готов") &&
                    !cleanLine.contains("✓ Ready")) {
                    cleanLore.add(line);
                }
            }
            
            meta.setLore(cleanLore.isEmpty() ? null : cleanLore);
        }
    }
    
    /**
     * Start a visual countdown that updates every second
     */
    public void startVisualCountdown(Player player, ItemStack item, long totalTime) {
        if (!plugin.getConfig().getBoolean("cooldown.visual-display.enabled", true)) {
            return;
        }
        
        if (!plugin.getConfig().getBoolean("cooldown.visual-display.live-update", false)) {
            // Just apply static display
            applyCooldownDisplay(player, item, totalTime);
            return;
        }
        
        // Cancel any existing countdown for this player
        stopVisualCountdown(player);
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        
        // Clean existing cooldown display first
        cleanExistingCooldownDisplay(meta);
        
        // Store cleaned values as original
        String originalName = meta.hasDisplayName() ? meta.getDisplayName() : null;
        List<String> originalLore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        
        BukkitRunnable countdownTask = new BukkitRunnable() {
            @Override
            public void run() {
                // Check if player is still online
                if (!player.isOnline()) {
                    activeCountdowns.remove(player.getUniqueId());
                    cancel();
                    return;
                }
                
                // Get actual remaining time from CooldownManager
                if (!plugin.getCooldownManager().hasCooldown(player)) {
                    // Cleanup and show ready message
                    showReadyMessage(player, item, originalName, originalLore);
                    activeCountdowns.remove(player.getUniqueId());
                    cancel();
                    return;
                }
                
                double remainingSeconds = plugin.getCooldownManager().getRemainingCooldown(player);
                if (remainingSeconds <= 0) {
                    // Cleanup and show ready message
                    showReadyMessage(player, item, originalName, originalLore);
                    activeCountdowns.remove(player.getUniqueId());
                    cancel();
                    return;
                }
                
                // Update display
                updateCountdownDisplay(player, item, remainingSeconds, originalName, originalLore);
            }
        };
        
        activeCountdowns.put(player.getUniqueId(), countdownTask);
        countdownTask.runTaskTimer(plugin, 0, 20); // Run every second
    }
    
    /**
     * Stop visual countdown for player
     */
    public void stopVisualCountdown(Player player) {
        BukkitRunnable task = activeCountdowns.remove(player.getUniqueId());
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }
    
    /**
     * Stop all active countdowns
     */
    public void stopAllCountdowns() {
        for (BukkitRunnable task : activeCountdowns.values()) {
            if (!task.isCancelled()) {
                task.cancel();
            }
        }
        activeCountdowns.clear();
    }
    
    /**
     * Show ready message and schedule cleanup
     */
    private void showReadyMessage(Player player, ItemStack item, String originalName, List<String> originalLore) {
        ItemMeta readyMeta = item.getItemMeta();
        if (readyMeta != null) {
            cleanExistingCooldownDisplay(readyMeta);
            
            // Add ready message to lore
            List<String> readyLore = readyMeta.hasLore() ? new ArrayList<>(readyMeta.getLore()) : new ArrayList<>();
            readyLore.add(plugin.getMessage(player, "messages.cooldown-ready"));
            readyMeta.setLore(readyLore);
            
            item.setItemMeta(readyMeta);
            player.updateInventory();
            
            // Schedule cleanup after 3 seconds
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    removeCooldownDisplay(player, item, originalName, originalLore);
                }
            }, 60);
        }
    }
    
    /**
     * Update countdown display efficiently
     */
    private void updateCountdownDisplay(Player player, ItemStack item, double remainingSeconds, String originalName, List<String> originalLore) {
        ItemMeta currentMeta = item.getItemMeta();
        if (currentMeta != null) {
            cleanExistingCooldownDisplay(currentMeta);
            long remainingTicks = (long) (remainingSeconds * 20);
            updateCooldownDisplay(currentMeta, remainingTicks, originalName, originalLore);
            item.setItemMeta(currentMeta);
            player.updateInventory();
        }
    }
    
    /**
     * Check if an item is a shulker box
     */
    private boolean isShulkerBox(ItemStack item) {
        if (item == null) return false;
        Material type = item.getType();
        return type == Material.SHULKER_BOX ||
               type.name().endsWith("_SHULKER_BOX");
    }
    
    /**
     * Remove all cooldown displays from player's inventory
     */
    public void clearAllCooldownDisplays(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isShulkerBox(item) && item != null) {
                ItemMeta meta = item.getItemMeta();
                if (meta != null && meta.hasLore()) {
                    List<String> lore = meta.getLore();
                    
                    // Remove cooldown lore lines
                    lore.removeIf(line -> {
                        String cleanLine = line.replaceAll("§[0-9a-fk-or]", "");
                        return cleanLine.contains("Cooldown:") || 
                               cleanLine.contains("cooldown:") || 
                               cleanLine.contains("Кулдаун:") ||
                               cleanLine.contains("кулдаун:") ||
                               cleanLine.contains("Готов к открытию") ||
                               cleanLine.contains("Ready to open") ||
                               cleanLine.contains("✓ Готов") ||
                               cleanLine.contains("✓ Ready");
                    });
                    
                    meta.setLore(lore.isEmpty() ? null : lore);
                    item.setItemMeta(meta);
                }
            }
        }
        player.updateInventory();
    }
}
