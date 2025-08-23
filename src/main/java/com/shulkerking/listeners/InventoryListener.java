package com.shulkerking.listeners;

import com.shulkerking.ShulkerKingPlugin;
import com.shulkerking.managers.ShulkerInventoryManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InventoryListener implements Listener {
    
    private final ShulkerKingPlugin plugin;
    private final Map<UUID, Long> lastSaveTime;
    
    public InventoryListener(ShulkerKingPlugin plugin) {
        this.plugin = plugin;
        this.lastSaveTime = new ConcurrentHashMap<>();
    }
    
    @EventHandler(priority = EventPriority.NORMAL)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getWhoClicked();
        
        // Check if player has an active shulker session
        if (!plugin.getInventoryManager().hasActiveSession(player)) {
            return;
        }
        
        // Verify the item is still in hand
        ShulkerInventoryManager.ShulkerSession session = plugin.getInventoryManager().getSession(player);
        if (session == null) {
            return;
        }
        
        ItemStack currentItem = session.isMainHand() ? 
            player.getInventory().getItemInMainHand() : 
            player.getInventory().getItemInOffHand();
        
        // If the shulker box is no longer in hand, close the inventory
        if (!plugin.getInventoryManager().isShulkerBox(currentItem)) {
            plugin.getInventoryManager().closeShulkerInventory(player);
            player.sendMessage(plugin.getMessage(player, "messages.item-changed"));
            event.setCancelled(true);
            return;
        }
        
        // Allow normal inventory interactions for the shulker GUI
        if (event.getInventory().getSize() == 27 && 
            event.getView().getTitle().equals("Shulker Box")) {
            
            // Throttle saves to prevent spam - max once per 100ms per player
            UUID playerId = player.getUniqueId();
            long currentTime = System.currentTimeMillis();
            Long lastSave = lastSaveTime.get(playerId);
            
            if (lastSave == null || (currentTime - lastSave) > 100) {
                lastSaveTime.put(playerId, currentTime);
                
                // Save contents immediately (synchronous to prevent race conditions)
                ShulkerInventoryManager.ShulkerSession currentSession = 
                    plugin.getInventoryManager().getSession(player);
                if (currentSession != null) {
                    plugin.getInventoryManager().saveShulkerContents(
                        player, currentSession, event.getInventory()
                    );
                }
            }
        }
        
        // Check item blacklist and prevent putting shulker boxes inside themselves
        ItemStack clickedItem = event.getCurrentItem();
        ItemStack cursorItem = event.getCursor();
        
        // Check if placing an item from cursor into shulker
        if (cursorItem != null && event.getInventory().getSize() == 27 && 
            event.getView().getTitle().equals("Shulker Box")) {
            
            // Check item blacklist
            if (plugin.getItemBlacklistManager().isBlacklisted(cursorItem)) {
                plugin.getItemBlacklistManager().handleBlacklistedItem(player, cursorItem, event);
                return;
            }
            
            // Prevent shulker box nesting
            if (plugin.getInventoryManager().isShulkerBox(cursorItem)) {
                event.setCancelled(true);
                player.sendMessage(plugin.getMessage(player, "messages.no-nesting"));
                plugin.getSoundManager().playBlockedSound(player);
                plugin.debugLog("Prevented shulker box nesting for " + player.getName());
                return;
            }
        }
        
        // Check if moving an item that's already in the shulker
        if (clickedItem != null && event.getInventory().getSize() == 27 && 
            event.getView().getTitle().equals("Shulker Box")) {
            
            if (plugin.getInventoryManager().isShulkerBox(clickedItem)) {
                event.setCancelled(true);
                player.sendMessage(plugin.getMessage(player, "messages.no-nesting"));
                plugin.getSoundManager().playBlockedSound(player);
                plugin.debugLog("Prevented shulker box nesting for " + player.getName());
            }
        }
    }
    
    @EventHandler(priority = EventPriority.NORMAL)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getPlayer();
        
        // Check if this is a shulker inventory being closed
        if (plugin.getInventoryManager().hasActiveSession(player)) {
            if (event.getInventory().getSize() == 27 && 
                event.getView().getTitle().equals("Shulker Box")) {
                
                // Save final contents and close session
                ShulkerInventoryManager.ShulkerSession session = 
                    plugin.getInventoryManager().getSession(player);
                
                if (session != null) {
                    plugin.getInventoryManager().saveShulkerContents(
                        player, session, event.getInventory()
                    );
                }
                
                // Play closing sound
                plugin.getSoundManager().playCloseSound(player);
                
                plugin.getInventoryManager().closeShulkerInventory(player);
            }
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getWhoClicked();
        
        // Check if player has an active shulker session
        if (!plugin.getInventoryManager().hasActiveSession(player)) {
            return;
        }
        
        // Verify the item is still in hand
        ShulkerInventoryManager.ShulkerSession session = plugin.getInventoryManager().getSession(player);
        if (session == null) {
            return;
        }
        
        ItemStack currentItem = session.isMainHand() ? 
            player.getInventory().getItemInMainHand() : 
            player.getInventory().getItemInOffHand();
        
        // If the shulker box is no longer in hand, close the inventory
        if (!plugin.getInventoryManager().isShulkerBox(currentItem)) {
            plugin.getInventoryManager().closeShulkerInventory(player);
            player.sendMessage(plugin.getMessage(player, "messages.item-changed"));
            event.setCancelled(true);
            return;
        }
        
        // Check if dragging into shulker inventory
        if (event.getInventory().getSize() == 27 && 
            event.getView().getTitle().equals("Shulker Box")) {
            
            ItemStack draggedItem = event.getOldCursor();
            
            // Check item blacklist
            if (draggedItem != null && plugin.getItemBlacklistManager().isBlacklisted(draggedItem)) {
                event.setCancelled(true);
                player.sendMessage(plugin.getMessage(player, "messages.blacklisted-item"));
                plugin.getSoundManager().playBlockedSound(player);
                plugin.debugLog("Prevented blacklisted item drag for " + player.getName());
                return;
            }
            
            // Prevent shulker box nesting
            if (draggedItem != null && plugin.getInventoryManager().isShulkerBox(draggedItem)) {
                event.setCancelled(true);
                player.sendMessage(plugin.getMessage(player, "messages.no-nesting"));
                plugin.getSoundManager().playBlockedSound(player);
                plugin.debugLog("Prevented shulker box nesting via drag for " + player.getName());
                return;
            }
            
            // Save contents immediately (synchronous to prevent race conditions)
            ShulkerInventoryManager.ShulkerSession currentSession = 
                plugin.getInventoryManager().getSession(player);
            if (currentSession != null) {
                plugin.getInventoryManager().saveShulkerContents(
                    player, currentSession, event.getInventory()
                );
            }
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        // Prevent hoppers from moving items to/from shulker inventories
        if (event.getDestination().getSize() == 27 && 
            event.getDestination().getViewers().size() > 0) {
            
            for (org.bukkit.entity.HumanEntity viewer : event.getDestination().getViewers()) {
                if (viewer instanceof Player) {
                    Player player = (Player) viewer;
                    if (plugin.getInventoryManager().hasActiveSession(player) &&
                        event.getDestination().equals(player.getOpenInventory().getTopInventory())) {
                        
                        event.setCancelled(true);
                        plugin.debugLog("Prevented hopper interaction with active shulker session for " + player.getName());
                        return;
                    }
                }
            }
        }
        
        // Also check source inventory
        if (event.getSource().getSize() == 27 && 
            event.getSource().getViewers().size() > 0) {
            
            for (org.bukkit.entity.HumanEntity viewer : event.getSource().getViewers()) {
                if (viewer instanceof Player) {
                    Player player = (Player) viewer;
                    if (plugin.getInventoryManager().hasActiveSession(player) &&
                        event.getSource().equals(player.getOpenInventory().getTopInventory())) {
                        
                        event.setCancelled(true);
                        plugin.debugLog("Prevented hopper interaction with active shulker session for " + player.getName());
                        return;
                    }
                }
            }
        }
    }
}
