package com.shulkerking.managers;

import com.shulkerking.ShulkerKingPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ShulkerInventoryManager {
    
    private final ShulkerKingPlugin plugin;
    private final Map<UUID, ShulkerSession> activeSessions;
    private final Map<UUID, Long> placementActions;
    
    public ShulkerInventoryManager(ShulkerKingPlugin plugin) {
        this.plugin = plugin;
        this.activeSessions = new HashMap<>();
        this.placementActions = new HashMap<>();
    }
    
    public boolean openShulkerInventory(Player player, ItemStack shulkerItem, boolean isMainHand) {
        if (!isShulkerBox(shulkerItem)) {
            return false;
        }
        
        // Check if player already has a session open
        if (hasActiveSession(player)) {
            closeShulkerInventory(player);
        }
        
        BlockStateMeta meta = (BlockStateMeta) shulkerItem.getItemMeta();
        ShulkerBox shulkerBox = (ShulkerBox) meta.getBlockState();
        
        // Create inventory with shulker contents
        Inventory inventory = Bukkit.createInventory(null, 27, "Shulker Box");
        inventory.setContents(shulkerBox.getInventory().getContents());
        
        // Create session to track this interaction
        ShulkerSession session = new ShulkerSession(
            player.getUniqueId(),
            shulkerItem.clone(),
            isMainHand,
            System.currentTimeMillis()
        );
        
        activeSessions.put(player.getUniqueId(), session);
        
        plugin.debugLog("Opening shulker inventory for " + player.getName() + 
                       " (main hand: " + isMainHand + ")");
        
        player.openInventory(inventory);
        return true;
    }
    
    public void closeShulkerInventory(Player player) {
        ShulkerSession session = activeSessions.remove(player.getUniqueId());
        if (session == null) {
            plugin.debugLog("[CLOSE] No active session found for " + player.getName());
            return;
        }
        
        plugin.debugLog("[CLOSE] Closing shulker inventory for " + player.getName());
        
        // Save inventory contents back to the item
        Inventory openInventory = player.getOpenInventory().getTopInventory();
        plugin.debugLog("[CLOSE] Open inventory size: " + openInventory.getSize());
        
        if (openInventory.getSize() == 27) {
            plugin.debugLog("[CLOSE] Saving contents from 27-slot inventory");
            saveShulkerContents(player, session, openInventory);
        } else {
            plugin.debugLog("[CLOSE] WARNING: Inventory size is not 27, skipping save");
        }
        
        player.closeInventory();
        plugin.debugLog("[CLOSE] Closed inventory for " + player.getName());
    }
    
    public void saveShulkerContents(Player player, ShulkerSession session, Inventory inventory) {
        ItemStack currentItem = session.isMainHand() ? 
            player.getInventory().getItemInMainHand() : 
            player.getInventory().getItemInOffHand();
        
        // Принудительное логирование для диагностики
        plugin.getLogger().info("[SAVE] Starting to save shulker contents for " + player.getName());
        plugin.getLogger().info("[SAVE] Current item: " + (currentItem != null ? currentItem.getType() : "null"));
        plugin.getLogger().info("[SAVE] Is main hand: " + session.isMainHand());
        
        // Verify the item is still the same shulker box
        if (!isSameShulkerBox(currentItem, session.getOriginalItem())) {
            plugin.debugLog("[SAVE] ERROR: Shulker item changed, cannot save contents for " + player.getName());
            player.sendMessage(plugin.getMessage(player, "messages.item-changed"));
            return;
        }
        
        // Count items in inventory before saving
        int itemCount = 0;
        ItemStack[] contents = inventory.getContents();
        for (ItemStack stack : contents) {
            if (stack != null && stack.getType() != Material.AIR) {
                itemCount += stack.getAmount();
            }
        }
        plugin.getLogger().info("[SAVE] Items to save: " + itemCount + " total items in " + contents.length + " slots");
        
        // Update the shulker box contents
        if (currentItem.getItemMeta() instanceof BlockStateMeta) {
            BlockStateMeta meta = (BlockStateMeta) currentItem.getItemMeta();
            plugin.debugLog("[SAVE] Got BlockStateMeta: " + meta.getClass().getSimpleName());
            
            if (meta.getBlockState() instanceof ShulkerBox) {
                ShulkerBox shulkerBox = (ShulkerBox) meta.getBlockState();
                plugin.debugLog("[SAVE] Got ShulkerBox state: " + shulkerBox.getClass().getSimpleName());
                
                // Clear and set new contents
                shulkerBox.getInventory().clear();
                shulkerBox.getInventory().setContents(contents);
                plugin.debugLog("[SAVE] Set contents to shulker box inventory");
                
                // Verify contents were set
                int savedCount = 0;
                for (ItemStack stack : shulkerBox.getInventory().getContents()) {
                    if (stack != null && stack.getType() != Material.AIR) {
                        savedCount += stack.getAmount();
                    }
                }
                plugin.debugLog("[SAVE] Verified saved items: " + savedCount + " items in shulker inventory");
                
                // Update the item meta
                meta.setBlockState(shulkerBox);
                currentItem.setItemMeta(meta);
                plugin.debugLog("[SAVE] Updated item meta with new block state");
                
                // Update the item in player's hand
                if (session.isMainHand()) {
                    player.getInventory().setItemInMainHand(currentItem);
                } else {
                    player.getInventory().setItemInOffHand(currentItem);
                }
                plugin.debugLog("[SAVE] Updated item in player's hand");
                
                // Final verification - check if the item actually has the contents
                ItemStack finalItem = session.isMainHand() ? 
                    player.getInventory().getItemInMainHand() : 
                    player.getInventory().getItemInOffHand();
                    
                if (finalItem != null && finalItem.getItemMeta() instanceof BlockStateMeta) {
                    BlockStateMeta finalMeta = (BlockStateMeta) finalItem.getItemMeta();
                    if (finalMeta.getBlockState() instanceof ShulkerBox) {
                        ShulkerBox finalShulker = (ShulkerBox) finalMeta.getBlockState();
                        int finalCount = 0;
                        for (ItemStack stack : finalShulker.getInventory().getContents()) {
                            if (stack != null && stack.getType() != Material.AIR) {
                                finalCount += stack.getAmount();
                            }
                        }
                                plugin.getLogger().info("[SAVE] FINAL VERIFICATION: " + finalCount + " items in final shulker");
                    }
                }
                
                plugin.debugLog("[SAVE] Successfully saved shulker contents for " + player.getName());
            } else {
                plugin.debugLog("[SAVE] ERROR: BlockState is not a ShulkerBox: " + meta.getBlockState().getClass().getSimpleName());
            }
        } else {
            plugin.debugLog("[SAVE] ERROR: ItemMeta is not BlockStateMeta: " + currentItem.getItemMeta().getClass().getSimpleName());
        }
    }
    
    public boolean hasActiveSession(Player player) {
        return activeSessions.containsKey(player.getUniqueId());
    }
    
    public ShulkerSession getSession(Player player) {
        return activeSessions.get(player.getUniqueId());
    }
    
    public void closeAllInventories() {
        for (UUID playerId : activeSessions.keySet()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                closeShulkerInventory(player);
            }
        }
        activeSessions.clear();
    }
    
    public boolean isShulkerBox(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        
        // Check if it's a shulker box material - more efficient than string operations
        Material type = item.getType();
        if (type != Material.SHULKER_BOX && 
            type != Material.WHITE_SHULKER_BOX &&
            type != Material.ORANGE_SHULKER_BOX &&
            type != Material.MAGENTA_SHULKER_BOX &&
            type != Material.LIGHT_BLUE_SHULKER_BOX &&
            type != Material.YELLOW_SHULKER_BOX &&
            type != Material.LIME_SHULKER_BOX &&
            type != Material.PINK_SHULKER_BOX &&
            type != Material.GRAY_SHULKER_BOX &&
            type != Material.LIGHT_GRAY_SHULKER_BOX &&
            type != Material.CYAN_SHULKER_BOX &&
            type != Material.PURPLE_SHULKER_BOX &&
            type != Material.BLUE_SHULKER_BOX &&
            type != Material.BROWN_SHULKER_BOX &&
            type != Material.GREEN_SHULKER_BOX &&
            type != Material.RED_SHULKER_BOX &&
            type != Material.BLACK_SHULKER_BOX) {
            return false;
        }
        
        // Check if it has proper BlockStateMeta
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof BlockStateMeta)) {
            return false;
        }
        
        BlockStateMeta blockMeta = (BlockStateMeta) meta;
        return blockMeta.getBlockState() instanceof ShulkerBox;
    }
    
    private boolean isSameShulkerBox(ItemStack current, ItemStack original) {
        if (current == null || original == null) {
            return false;
        }
        
        if (!isShulkerBox(current) || !isShulkerBox(original)) {
            return false;
        }
        
        // Compare material type
        if (current.getType() != original.getType()) {
            return false;
        }
        
        // Compare display names if they exist
        ItemMeta currentMeta = current.getItemMeta();
        ItemMeta originalMeta = original.getItemMeta();
        
        if (currentMeta.hasDisplayName() != originalMeta.hasDisplayName()) {
            return false;
        }
        
        if (currentMeta.hasDisplayName() && 
            !currentMeta.getDisplayName().equals(originalMeta.getDisplayName())) {
            return false;
        }
        
        return true;
    }
    
    public static class ShulkerSession {
        private final UUID playerId;
        private final ItemStack originalItem;
        private final boolean isMainHand;
        private final long startTime;
        
        public ShulkerSession(UUID playerId, ItemStack originalItem, boolean isMainHand, long startTime) {
            this.playerId = playerId;
            this.originalItem = originalItem;
            this.isMainHand = isMainHand;
            this.startTime = startTime;
        }
        
        public UUID getPlayerId() { return playerId; }
        public ItemStack getOriginalItem() { return originalItem; }
        public boolean isMainHand() { return isMainHand; }
        public long getStartTime() { return startTime; }
    }
    
    // Methods for placement action tracking
    public void markPlacementAction(Player player) {
        placementActions.put(player.getUniqueId(), System.currentTimeMillis());
    }
    
    public boolean isRecentPlacementAction(Player player) {
        Long timestamp = placementActions.get(player.getUniqueId());
        if (timestamp == null) {
            return false;
        }
        
        // Consider it recent if within 500ms to prevent false item-changed messages
        boolean isRecent = (System.currentTimeMillis() - timestamp) < 500;
        if (!isRecent) {
            placementActions.remove(player.getUniqueId());
        }
        return isRecent;
    }
}
