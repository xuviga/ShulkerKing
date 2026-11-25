package com.shulkerking.listeners;

import com.shulkerking.ShulkerKingPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.ItemStack;
import org.bukkit.block.Block;
import org.bukkit.Material;
import org.bukkit.Location;

public class PlayerListener implements Listener {
    
    private final ShulkerKingPlugin plugin;
    
    public PlayerListener(ShulkerKingPlugin plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getEntity();
        
        // ВАЖНО: боевая метка теперь ставится только за PvP (onPlayerDamageByEntity),
        // а не за любой урон (падение, огонь, кактус).
        
        // Close shulker inventory if player takes damage and PvP blocking is enabled
        if (plugin.isPvpBlockEnabled() && plugin.getInventoryManager().hasActiveSession(player)) {
            plugin.getInventoryManager().closeShulkerInventory(player);
            player.sendMessage(plugin.getMessage(player, "messages.in-combat"));
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDamageByEntity(EntityDamageByEntityEvent event) {
        // PvP: игрок или снаряд игрока
        Player attacker = resolvePlayerDamager(event.getDamager());
        
        if (attacker != null) {
            plugin.getCombatManager().markInCombat(attacker);
            
            if (plugin.isPvpBlockEnabled() && 
                plugin.getInventoryManager().hasActiveSession(attacker)) {
                plugin.getInventoryManager().closeShulkerInventory(attacker);
                attacker.sendMessage(plugin.getMessage(attacker, "messages.in-combat"));
            }
        }
        
        if (event.getEntity() instanceof Player && attacker != null) {
            Player victim = (Player) event.getEntity();
            if (attacker.getUniqueId().equals(victim.getUniqueId())) {
                return;
            }
            plugin.getCombatManager().markInCombat(victim);
            
            if (plugin.isPvpBlockEnabled() && 
                plugin.getInventoryManager().hasActiveSession(victim)) {
                plugin.getInventoryManager().closeShulkerInventory(victim);
                victim.sendMessage(plugin.getMessage(victim, "messages.in-combat"));
            }
        }
    }
    
    /**
     * Возвращает игрока-источник урона: прямой удар или снаряд игрока.
     */
    private Player resolvePlayerDamager(org.bukkit.entity.Entity damager) {
        if (damager instanceof Player) {
            return (Player) damager;
        }
        if (damager instanceof org.bukkit.entity.Projectile) {
            org.bukkit.projectiles.ProjectileSource shooter =
                ((org.bukkit.entity.Projectile) damager).getShooter();
            if (shooter instanceof Player) {
                return (Player) shooter;
            }
        }
        return null;
    }
    
    // УДАЛЕН: Дублирующий обработчик onPlayerDropItem
    // Обработка выбрасывания предметов теперь полностью в InventoryListener
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        
        // Close any active shulker session to prevent data loss
        if (plugin.getInventoryManager().hasActiveSession(player)) {
            plugin.getInventoryManager().closeShulkerInventory(player);
            plugin.debugLog("Closed shulker inventory for " + player.getName() + " due to death");
        }
        
        // Remove from combat
        plugin.getCombatManager().removeCombat(player);
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        
        // Close any active shulker session to prevent data loss.
        // Если предмет с меткой потерян - сессия остаётся в sessions.yml
        // и восстановится при следующем входе (giveIfMissing = false).
        if (plugin.getInventoryManager().hasActiveSession(player)) {
            plugin.getInventoryManager().closeShulkerInventory(player, false);
            plugin.debugLog("Closed shulker inventory for " + player.getName() + " due to quit");
        }
        
        // Remove from combat
        plugin.getCombatManager().removeCombat(player);
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // Восстановление сессии, оставшейся после краша сервера/выхода с открытым GUI
        plugin.getInventoryManager().restorePendingSession(player);
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Событие могли отменить другие плагины (или наш собственный обработчик) -
        // размещать в этом случае нельзя.
        if (event.isCancelled()) {
            return;
        }
        
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        
        // Check if player is holding a shulker box and shift+right-clicking
        if (item == null || !plugin.getInventoryManager().isShulkerBox(item)) {
            return;
        }
        
        // Check if shift+right-click and placement is enabled
        if (!player.isSneaking() || 
            (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_AIR) ||
            !plugin.getConfig().getBoolean("settings.shift-place", true)) {
            return;
        }
        
        // Check permissions
        if (!player.hasPermission("shulkerking.place")) {
            player.sendMessage(plugin.getMessage(player, "messages.no-permission-place"));
            event.setCancelled(true);
            return;
        }
        
        // Check world blacklist (единая точка проверки)
        if (!plugin.getWorldManager().isWorldAllowed(player)) {
            player.sendMessage(plugin.getWorldManager().getBlacklistMessage());
            event.setCancelled(true);
            return;
        }
        
        // Handle placement logic
        Block targetBlock = null;
        
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
            // Place on clicked block
            Block clickedBlock = event.getClickedBlock();
            targetBlock = clickedBlock.getRelative(event.getBlockFace());
        } else if (event.getAction() == Action.RIGHT_CLICK_AIR) {
            // Use raytracing to find target block more accurately
            org.bukkit.util.RayTraceResult result = player.getWorld().rayTraceBlocks(
                player.getEyeLocation(), 
                player.getEyeLocation().getDirection(), 
                5.0, 
                org.bukkit.FluidCollisionMode.NEVER, 
                true
            );
            
            if (result != null && result.getHitBlock() != null) {
                // Place on the hit block's face
                Block hitBlock = result.getHitBlock();
                targetBlock = hitBlock.getRelative(result.getHitBlockFace());
            } else {
                // Fallback: place at player's feet if no target found
                Location playerLoc = player.getLocation();
                Block blockBelow = playerLoc.getBlock().getRelative(0, -1, 0);
                if (blockBelow.getType().isSolid()) {
                    targetBlock = playerLoc.getBlock();
                } else {
                    // Can't place anywhere safe
                    player.sendMessage(plugin.getMessage(player, "messages.cannot-place"));
                    event.setCancelled(true);
                    return;
                }
            }
        }
        
        // Check if target location is valid
        if (targetBlock == null || !targetBlock.getType().isAir() || targetBlock.isLiquid()) {
            player.sendMessage(plugin.getMessage(player, "messages.cannot-place"));
            event.setCancelled(true);
            return;
        }
        
        // ИСПРАВЛЕНИЕ: Если у игрока есть активная сессия с этим шалкером, сначала сохраняем содержимое
        plugin.debugLog("[PLACE] Checking for active session for " + player.getName());
        if (plugin.getInventoryManager().hasActiveSession(player)) {
            plugin.debugLog("[PLACE] Found active session, closing and saving before placement");
            plugin.getInventoryManager().closeShulkerInventory(player);
            plugin.debugLog("[PLACE] Closed active shulker session before placement for " + player.getName());
        } else {
            plugin.debugLog("[PLACE] No active session found for " + player.getName());
        }

        // Содержимое только что записано обратно в предмет - перечитываем его из руки,
        // иначе можно поставить блок с устаревшим (пустым) состоянием.
        ItemStack liveItem = event.getHand() == org.bukkit.inventory.EquipmentSlot.OFF_HAND
            ? player.getInventory().getItemInOffHand()
            : player.getInventory().getItemInMainHand();
        if (liveItem == null || !plugin.getInventoryManager().isShulkerBox(liveItem)) {
            player.sendMessage(plugin.getMessage(player, "messages.cannot-place"));
            event.setCancelled(true);
            return;
        }
        item = liveItem;
        
        // Place the shulker box
        Material shulkerType = item.getType();

        // API: собственное событие размещения (можно отменить другим плагинам)
        com.shulkerking.api.events.ShulkerPlaceEvent shulkerPlaceEvent =
            new com.shulkerking.api.events.ShulkerPlaceEvent(player, item.clone(), targetBlock);
        plugin.getServer().getPluginManager().callEvent(shulkerPlaceEvent);
        if (shulkerPlaceEvent.isCancelled()) {
            event.setCancelled(true);
            player.sendMessage(plugin.getMessage(player, "messages.cannot-place"));
            return;
        }

        // Ставим блок и даём защитам (WorldGuard, GriefPrevention, Towny...) шанс отменить
        // установку: без BlockPlaceEvent плагин обходил все региональные запреты.
        org.bukkit.block.BlockState replacedState = targetBlock.getState();
        targetBlock.setType(shulkerType, false);

        org.bukkit.inventory.EquipmentSlot hand = event.getHand() != null
            ? event.getHand() : org.bukkit.inventory.EquipmentSlot.HAND;

        org.bukkit.event.block.BlockPlaceEvent placeEvent = new org.bukkit.event.block.BlockPlaceEvent(
            targetBlock,
            replacedState,
            event.getClickedBlock() != null ? event.getClickedBlock() : targetBlock,
            item,
            player,
            true,
            hand
        );
        plugin.getServer().getPluginManager().callEvent(placeEvent);

        if (placeEvent.isCancelled()) {
            replacedState.update(true, false);
            event.setCancelled(true);
            player.sendMessage(plugin.getMessage(player, "messages.cannot-place"));
            return;
        }

        // КРИТИЧНО: блок поставлен вручную - гасим ванильную установку,
        // иначе сервер поставит блок второй раз и спишет ещё один предмет
        // (это и был "недопис кода", из-за которого шалкер ставился бесконечно).
        event.setCancelled(true);
        
        // Copy the shulker box data to the placed block
        if (targetBlock.getState() instanceof org.bukkit.block.ShulkerBox) {
            org.bukkit.block.ShulkerBox placedShulker = (org.bukkit.block.ShulkerBox) targetBlock.getState();
            
            if (item.hasItemMeta() && item.getItemMeta() instanceof org.bukkit.inventory.meta.BlockStateMeta) {
                org.bukkit.inventory.meta.BlockStateMeta meta = (org.bukkit.inventory.meta.BlockStateMeta) item.getItemMeta();
                
                // Получаем актуальное содержимое из BlockState
                org.bukkit.block.BlockState blockState = meta.getBlockState();
                plugin.debugLog("[PLACE] Got BlockState: " + blockState.getClass().getSimpleName());
                
                if (blockState instanceof org.bukkit.block.ShulkerBox) {
                    org.bukkit.block.ShulkerBox itemShulker = (org.bukkit.block.ShulkerBox) blockState;
                    plugin.debugLog("[PLACE] Got ShulkerBox from item: " + itemShulker.getClass().getSimpleName());
                    
                    // Получаем содержимое инвентаря
                    org.bukkit.inventory.ItemStack[] contents = itemShulker.getInventory().getContents();
                    plugin.debugLog("[PLACE] Got contents array with " + contents.length + " slots");
                    
                    // Подсчитываем предметы для детального логирования
                    int itemCount = 0;
                    int nonEmptySlots = 0;
                    for (org.bukkit.inventory.ItemStack stack : contents) {
                        if (stack != null && stack.getType() != Material.AIR) {
                            itemCount += stack.getAmount();
                            nonEmptySlots++;
                            plugin.debugLog("[PLACE] Slot content: " + stack.getType() + " x" + stack.getAmount());
                        }
                    }
                    plugin.debugLog("[PLACE] Total items in shulker: " + itemCount + " items in " + nonEmptySlots + " non-empty slots");
                    
                    if (itemCount > 0) {
                        // Копируем содержимое в размещенный шалкер
                        plugin.debugLog("[PLACE] Copying " + itemCount + " items to placed shulker");
                        
                        // Альтернативный способ: копируем по одному предмету
                        org.bukkit.inventory.Inventory placedInventory = placedShulker.getInventory();
                        placedInventory.clear(); // Очищаем на всякий случай
                        
                        for (int i = 0; i < contents.length && i < placedInventory.getSize(); i++) {
                            if (contents[i] != null && contents[i].getType() != Material.AIR) {
                                placedInventory.setItem(i, contents[i].clone());
                                plugin.debugLog("[PLACE] Copied slot " + i + ": " + contents[i].getType() + " x" + contents[i].getAmount());
                            }
                        }
                        
                        // Проверяем что содержимое действительно скопировалось
                        int copiedCount = 0;
                        for (org.bukkit.inventory.ItemStack stack : placedShulker.getInventory().getContents()) {
                            if (stack != null && stack.getType() != Material.AIR) {
                                copiedCount += stack.getAmount();
                            }
                        }
                        plugin.debugLog("[PLACE] Verification: " + copiedCount + " items copied to placed shulker");
                        
                        if (copiedCount != itemCount) {
                            plugin.getLogger().warning("[PLACE] WARNING: Item count mismatch! Original: " + itemCount + ", Copied: " + copiedCount);
                        }
                    } else {
                        plugin.debugLog("[PLACE] Shulker box is empty, no items to copy for " + player.getName());
                    }
                    
                    // Копируем кастомное имя если есть
                    if (meta.hasDisplayName()) {
                        placedShulker.setCustomName(meta.getDisplayName());
                        plugin.debugLog("Set custom name: " + meta.getDisplayName());
                    }
                    
                    // Создаем final копии для использования в лямбда
                    final int finalItemCount = itemCount;
                    final Block finalTargetBlock = targetBlock;
                    final org.bukkit.inventory.ItemStack[] finalContents = contents.clone();

                    // НОВЫЙ ПОДХОД: Устанавливаем содержимое через отложенную задачу
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                        if (finalTargetBlock.getState() instanceof org.bukkit.block.ShulkerBox) {
                            org.bukkit.block.ShulkerBox delayedShulker = (org.bukkit.block.ShulkerBox) finalTargetBlock.getState();
                            
                            // Очищаем и устанавливаем содержимое
                            delayedShulker.getInventory().clear();
                            delayedShulker.getInventory().setContents(finalContents);
                            
                            // Копируем кастомное имя если есть
                            if (meta.hasDisplayName()) {
                                delayedShulker.setCustomName(meta.getDisplayName());
                            }
                            
                            // Обновляем блок
                            delayedShulker.update();
                            
                            plugin.debugLog("[PLACE] DELAYED: Set " + finalItemCount + " items to placed shulker");
                            
                            // Финальная проверка через еще один тик
                            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                                if (finalTargetBlock.getState() instanceof org.bukkit.block.ShulkerBox) {
                                    org.bukkit.block.ShulkerBox verifyShulker = (org.bukkit.block.ShulkerBox) finalTargetBlock.getState();
                                    int finalCount = 0;
                                    for (org.bukkit.inventory.ItemStack stack : verifyShulker.getInventory().getContents()) {
                                        if (stack != null && stack.getType() != Material.AIR) {
                                            finalCount += stack.getAmount();
                                        }
                                    }
                                    plugin.debugLog("[PLACE] FINAL CHECK: Placed shulker contains " + finalCount + " items after delayed update");
                                    if (finalCount == 0 && finalItemCount > 0) {
                                        plugin.getLogger().warning("[PLACE] CRITICAL: Items were still lost after delayed placement! Original: " + finalItemCount + ", Final: " + finalCount);
                                    } else if (finalCount == finalItemCount) {
                                        plugin.debugLog("[PLACE] SUCCESS: All items preserved during placement!");
                                    }
                                }
                            }, 1L);
                        }
                    }, 1L);
                } else {
                    plugin.debugLog("Warning: Item meta does not contain valid ShulkerBox state for " + player.getName());
                }
            } else {
                plugin.debugLog("Warning: Item does not have BlockStateMeta for " + player.getName());
            }
        } else {
            plugin.debugLog("Warning: Placed block is not a ShulkerBox for " + player.getName());
        }
        
        // Mark this as a placement action to prevent false item-changed messages
        plugin.getInventoryManager().markPlacementAction(player);
        
        // Remove item from the hand that was actually used (main/off hand)
        com.shulkerking.util.HandItemUtil.consumeOne(player, hand);
        player.updateInventory();
        
        // Play sound effect
        if (plugin.getConfig().getBoolean("sounds.enabled", true)) {
            String sound = plugin.getConfig().getString("sounds.place.sound", "BLOCK_STONE_PLACE");
            try {
                player.playSound(player.getLocation(), 
                    org.bukkit.Sound.valueOf(sound), 
                    (float) plugin.getConfig().getDouble("sounds.place.volume", 1.0),
                    (float) plugin.getConfig().getDouble("sounds.place.pitch", 1.0));
            } catch (IllegalArgumentException e) {
                plugin.debugLog("Invalid sound: " + sound);
            }
        }
        
        player.sendMessage(plugin.getMessage(player, "messages.shulker-placed"));
        plugin.debugLog("Player " + player.getName() + " placed shulker box at " + targetBlock.getLocation());
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        
        // Check if player has an active shulker session
        if (plugin.getInventoryManager().hasActiveSession(player)) {
            // Close session when changing held item to prevent duplication
            plugin.getInventoryManager().closeShulkerInventory(player);
            player.sendMessage(plugin.getMessage(player, "messages.item-changed"));
            plugin.debugLog("Closed shulker session due to item change for " + player.getName());
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getEntity();
        
        // Check if player has an active shulker session
        if (plugin.getInventoryManager().hasActiveSession(player)) {
            // Prevent item pickup during shulker session to avoid inventory conflicts
            event.setCancelled(true);
            plugin.debugLog("Prevented item pickup during shulker session for " + player.getName());
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        
        // Check if player has an active shulker session
        if (plugin.getInventoryManager().hasActiveSession(player)) {
            // Close session when swapping hands to prevent duplication
            plugin.getInventoryManager().closeShulkerInventory(player);
            player.sendMessage(plugin.getMessage(player, "messages.item-changed"));
            plugin.debugLog("Closed shulker session due to hand swap for " + player.getName());
        }
    }
}
