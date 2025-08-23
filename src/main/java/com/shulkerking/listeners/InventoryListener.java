package com.shulkerking.listeners;

import com.shulkerking.ShulkerKingPlugin;
import com.shulkerking.holders.ShulkerInventoryHolder;
import com.shulkerking.managers.ShulkerInventoryManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InventoryListener implements Listener {
    
    private final ShulkerKingPlugin plugin;
    private final Map<UUID, Long> lastSaveTime;
    private final Map<UUID, Long> lastClickTime;
    
    public InventoryListener(ShulkerKingPlugin plugin) {
        this.plugin = plugin;
        this.lastSaveTime = new ConcurrentHashMap<>();
        this.lastClickTime = new ConcurrentHashMap<>();
    }
    
    @EventHandler(priority = EventPriority.NORMAL)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getWhoClicked();
        
        // МГНОВЕННАЯ защита от дупа - закрываем шалкер при любой попытке перемещения
        if (plugin.getInventoryManager().hasActiveSession(player)) {
            ItemStack clickedItem = event.getCurrentItem();
            ItemStack cursorItem = event.getCursor();
            ShulkerInventoryManager.ShulkerSession session = plugin.getInventoryManager().getSession(player);
            
            boolean shouldCloseShulker = false;
            String reason = "";
            
            if (session != null) {
                ItemStack currentShulker = session.isMainHand() ? 
                    player.getInventory().getItemInMainHand() : 
                    player.getInventory().getItemInOffHand();
                
                // 1. МГНОВЕННОЕ закрытие при hotkey действиях (цифры 1-9)
                if (event.getHotbarButton() != -1) {
                    shouldCloseShulker = true;
                    reason = "hotkey movement detected";
                }
                
                // 2. Любое взаимодействие с открытым шалкером в инвентаре игрока
                if (!shouldCloseShulker && event.getClickedInventory() == player.getInventory()) {
                    // Проверяем клик по открытому шалкеру
                    if (clickedItem != null && plugin.getInventoryManager().isSameShulkerBox(clickedItem, currentShulker)) {
                        shouldCloseShulker = true;
                        reason = "clicked on open shulker";
                    }
                    
                    // Проверяем перемещение курсором на открытый шалкер
                    if (cursorItem != null && plugin.getInventoryManager().isSameShulkerBox(cursorItem, currentShulker)) {
                        shouldCloseShulker = true;
                        reason = "cursor item is open shulker";
                    }
                }
                
                // 3. Shift+click любого шалкера
                if (!shouldCloseShulker && event.isShiftClick() && clickedItem != null && 
                    plugin.getInventoryManager().isShulkerBox(clickedItem)) {
                    shouldCloseShulker = true;
                    reason = "shift-click shulker movement";
                }
                
                // 4. Drag & Drop действия с шалкерами
                if (!shouldCloseShulker && (event.getClick().name().contains("DRAG") || 
                    event.getAction().name().contains("MOVE"))) {
                    if ((clickedItem != null && plugin.getInventoryManager().isShulkerBox(clickedItem)) ||
                        (cursorItem != null && plugin.getInventoryManager().isShulkerBox(cursorItem))) {
                        shouldCloseShulker = true;
                        reason = "drag/move shulker action";
                    }
                }
                
                // 5. Попытка поменять местами предметы в хотбаре
                if (!shouldCloseShulker && event.getAction().name().contains("HOTBAR")) {
                    shouldCloseShulker = true;
                    reason = "hotbar swap action";
                }
            }
            
            // МГНОВЕННОЕ закрытие без задержек
            if (shouldCloseShulker) {
                event.setCancelled(true);
                
                // Закрываем шалкер немедленно
                plugin.getInventoryManager().closeShulkerInventory(player);
                
                // Сообщение и звук
                player.sendMessage(plugin.getMessage(player, "messages.shulker-closed-movement"));
                plugin.getSoundManager().playBlockedSound(player);
                plugin.debugLog("INSTANT CLOSE: " + reason + " for " + player.getName());
                
                // Принудительно обновляем инвентарь
                player.updateInventory();
                return;
            }
        }
        
        // Проверяем, является ли это шалкер-инвентарем через кастомный holder
        if (!(event.getInventory().getHolder() instanceof ShulkerInventoryHolder)) {
            return;
        }
        
        ShulkerInventoryHolder holder = (ShulkerInventoryHolder) event.getInventory().getHolder();
        
        // Проверяем, принадлежит ли holder этому игроку
        if (!holder.belongsTo(player)) {
            plugin.debugLog("Holder doesn't belong to player: " + player.getName());
            event.setCancelled(true);
            return;
        }
        
        // Задержка в 1 тик для предотвращения спама кликов
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        Long lastClick = lastClickTime.get(playerId);
        
        if (lastClick != null && (currentTime - lastClick) < 50) { // 1 тик = ~50мс
            event.setCancelled(true);
            plugin.debugLog("Click too fast, cancelled for " + player.getName());
            return;
        }
        lastClickTime.put(playerId, currentTime);
        
        // Check if player has an active shulker session
        if (!plugin.getInventoryManager().hasActiveSession(player)) {
            plugin.debugLog("No active session for " + player.getName());
            event.setCancelled(true);
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
        // Теперь проверяем через holder вместо title
        if (event.getInventory().getHolder() instanceof ShulkerInventoryHolder) {
            
            // Throttle saves to prevent spam - max once per 100ms per player
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
        if (cursorItem != null && event.getInventory().getHolder() instanceof ShulkerInventoryHolder) {
            
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
        if (clickedItem != null && event.getInventory().getHolder() instanceof ShulkerInventoryHolder) {
            
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
        
        // Проверяем через кастомный holder вместо title
        if (event.getInventory().getHolder() instanceof ShulkerInventoryHolder) {
            ShulkerInventoryHolder holder = (ShulkerInventoryHolder) event.getInventory().getHolder();
            
            // Проверяем, принадлежит ли holder этому игроку
            if (!holder.belongsTo(player)) {
                plugin.debugLog("Close event: holder doesn't belong to player " + player.getName());
                return;
            }
            
            // Save final contents and close session
            ShulkerInventoryManager.ShulkerSession session = holder.getSession();
            
            if (session != null && plugin.getInventoryManager().hasActiveSession(player)) {
                plugin.getInventoryManager().saveShulkerContents(
                    player, session, event.getInventory()
                );
            }
            
            // Play closing sound
            plugin.getSoundManager().playCloseSound(player);
            
            plugin.getInventoryManager().closeShulkerInventory(player);
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
        if (event.getInventory().getHolder() instanceof ShulkerInventoryHolder) {
            
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
    
    /**
     * Блокировка выбрасывания предметов когда открыт шалкер
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        
        // Проверяем, есть ли у игрока активная сессия шалкера
        if (!plugin.getInventoryManager().hasActiveSession(player)) {
            return;
        }
        
        // Проверяем, открыт ли шалкер-инвентарь
        if (player.getOpenInventory() != null && 
            player.getOpenInventory().getTopInventory().getHolder() instanceof ShulkerInventoryHolder) {
            
            event.setCancelled(true);
            player.sendMessage(plugin.getMessage(player, "messages.no-drop-while-shulker-open"));
            plugin.getSoundManager().playBlockedSound(player);
            plugin.debugLog("Prevented item drop while shulker open for " + player.getName());
        }
    }
}
