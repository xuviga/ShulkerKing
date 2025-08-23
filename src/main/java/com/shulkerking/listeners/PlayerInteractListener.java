package com.shulkerking.listeners;

import com.shulkerking.ShulkerKingPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class PlayerInteractListener implements Listener {
    
    private final ShulkerKingPlugin plugin;
    
    public PlayerInteractListener(ShulkerKingPlugin plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        
        // Check if item is a shulker box
        if (!plugin.getInventoryManager().isShulkerBox(item)) {
            return;
        }
        
        // Check if world is allowed
        if (!plugin.getWorldManager().isWorldAllowed(player)) {
            player.sendMessage(plugin.getWorldManager().getBlacklistMessage());
            plugin.getSoundManager().playBlockedSound(player);
            event.setCancelled(true);
            return;
        }
        
        // Check hand permissions
        if (event.getHand() != null && event.getHand() == EquipmentSlot.OFF_HAND) {
            if (!plugin.getConfig().getBoolean("settings.offhand", true)) {
                player.sendMessage(plugin.getMessage(player, "messages.offhand-disabled"));
                plugin.getSoundManager().playBlockedSound(player);
                event.setCancelled(true);
                return;
            }
        } else {
            if (!plugin.getConfig().getBoolean("settings.main-hand", true)) {
                event.setCancelled(true);
                return;
            }
        }
        
        // Check permissions
        if (!player.hasPermission("shulkerking.open")) {
            player.sendMessage(plugin.getMessage(player, "messages.no-permission-open"));
            plugin.getSoundManager().playBlockedSound(player);
            event.setCancelled(true);
            return;
        }
        
        if (event.getHand() == EquipmentSlot.OFF_HAND && !player.hasPermission("shulkerking.offhand")) {
            player.sendMessage(plugin.getMessage(player, "messages.no-permission-offhand"));
            plugin.getSoundManager().playBlockedSound(player);
            event.setCancelled(true);
            return;
        }
        
        // Check cooldown
        if (plugin.getCooldownManager().hasCooldown(player)) {
            double remaining = plugin.getCooldownManager().getRemainingCooldown(player);
            String cooldownMsg = plugin.getMessage(player, "messages.cooldown-active")
                .replace("{time}", String.format("%.1f", remaining));
            player.sendMessage(cooldownMsg);
            plugin.getSoundManager().playCooldownSound(player);
            event.setCancelled(true);
            return;
        }
        
        // Check if player is in combat
        if (plugin.getConfig().getBoolean("settings.pvp-block", false) && 
            plugin.getCombatManager().isInCombat(player)) {
            player.sendMessage(plugin.getMessage(player, "messages.in-combat"));
            plugin.getSoundManager().playBlockedSound(player);
            event.setCancelled(true);
            return;
        }
        
        // Cancel the event to prevent normal shulker box behavior
        event.setCancelled(true);
        
        // Open the shulker box inventory
        boolean isMainHand = event.getHand() == EquipmentSlot.HAND;
        plugin.getInventoryManager().openShulkerInventory(player, item, isMainHand);
        
        // Get cooldown time and set cooldown AFTER successful opening
        double cooldownTime = plugin.getCooldownManager().getCooldownTime(player);
        plugin.getCooldownManager().setCooldown(player);
        
        // Apply visual cooldown display (convert seconds to ticks)
        long cooldownTicks = (long) (cooldownTime * 20);
        plugin.getCooldownDisplayManager().startVisualCountdown(player, item, cooldownTicks);
        
        // Play sounds
        plugin.getSoundManager().playOpenSound(player);
    }
}
