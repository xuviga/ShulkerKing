package com.shulkerking;

import com.shulkerking.commands.ShulkerKingCommand;
import com.shulkerking.listeners.InventoryListener;
import com.shulkerking.listeners.PlayerInteractListener;
import com.shulkerking.listeners.PlayerListener;
import com.shulkerking.managers.*;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * ShulkerKing - Modern Minecraft plugin for opening shulker boxes from inventory
 * 
 * Features:
 * - Open shulker boxes directly from hand
 * - Anti-duplication protection
 * - Configurable cooldowns with visual display
 * - Multi-language support
 * - PvP combat blocking
 * - World restrictions
 * - Item blacklist system
 * 
 * @author ShulkerKing Team
 * @version 2.0.0
 * @since 1.16.2
 */
public final class ShulkerKingPlugin extends JavaPlugin {
    
    // Plugin instance for static access
    private static ShulkerKingPlugin instance;
    
    // Core managers - initialized in proper order
    private LocaleManager localeManager;
    private ColorManager colorManager;
    private SoundManager soundManager;
    private WorldManager worldManager;
    private ItemBlacklistManager itemBlacklistManager;
    private CombatManager combatManager;
    private CooldownManager cooldownManager;
    private CooldownDisplayManager cooldownDisplayManager;
    private ShulkerInventoryManager inventoryManager;
    
    // Performance: Cached configuration values
    private volatile boolean pvpBlockEnabled;
    private volatile boolean cooldownEnabled;
    private volatile boolean soundsEnabled;
    private volatile boolean debugEnabled;
    private volatile boolean visualCooldownEnabled;
    
    /**
     * Called after plugin is loaded but before enabled
     * Used for early initialization that doesn't depend on other plugins
     */
    @Override
    public void onLoad() {
        instance = this;
        getLogger().info("ShulkerKing v" + getDescription().getVersion() + " загружается...");
        
        // Pre-load configuration
        saveDefaultConfig();
        
        if (isDebugEnabled()) {
            debugLog("Плагин загружен в режиме отладки");
        }
    }
    
    /**
     * Called when plugin is enabled
     * Main initialization happens here
     */
    @Override
    public void onEnable() {
        long startTime = System.currentTimeMillis();
        getLogger().info("Включение ShulkerKing v" + getDescription().getVersion() + "...");
        
        try {
            // Initialize in proper dependency order
            if (!initializePlugin()) {
                getLogger().severe("Критическая ошибка при инициализации плагина!");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
            
            long loadTime = System.currentTimeMillis() - startTime;
            getLogger().info(String.format("ShulkerKing успешно включен за %dмс!", loadTime));
            
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Критическая ошибка при включении плагина:", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }
    
    /**
     * Called when plugin is disabled
     * Cleanup all resources and save data
     */
    @Override
    public void onDisable() {
        getLogger().info("Отключение ShulkerKing v" + getDescription().getVersion() + "...");
        
        try {
            // Graceful shutdown in reverse dependency order
            shutdownPlugin();
            
            getLogger().info("ShulkerKing успешно отключен!");
            
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Ошибка при отключении плагина:", e);
        } finally {
            instance = null;
        }
    }
    
    /**
     * Initialize the entire plugin
     * @return true if successful, false if critical error occurred
     */
    private boolean initializePlugin() {
        try {
            // Step 1: Cache configuration values for performance
            cacheConfigValues();
            
            // Step 2: Initialize managers in dependency order
            if (!initializeManagers()) {
                return false;
            }
            
            // Step 3: Register event listeners
            registerEventListeners();
            
            // Step 4: Register commands
            registerCommands();
            
            // Step 5: Post-initialization tasks
            scheduleAsyncTasks();
            
            return true;
            
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Ошибка инициализации плагина:", e);
            return false;
        }
    }
    
    /**
     * Gracefully shutdown the plugin
     */
    private void shutdownPlugin() {
        // Cancel all scheduled tasks
        getServer().getScheduler().cancelTasks(this);
        
        // Stop all active countdown tasks to prevent memory leaks
        if (cooldownDisplayManager != null) {
            cooldownDisplayManager.stopAllCountdowns();
        }
        
        // Close all open shulker inventories
        if (inventoryManager != null) {
            inventoryManager.closeAllInventories();
        }
        
        // Clear all runtime data
        if (cooldownManager != null) {
            cooldownManager.clearAllCooldowns();
        }
        
        if (combatManager != null) {
            combatManager.clearAllCombat();
        }
        
        // Save any pending data asynchronously
        CompletableFuture.runAsync(() -> {
            try {
                saveConfig();
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "Ошибка сохранения конфигурации:", e);
            }
        });
    }
    
    /**
     * Cache frequently accessed configuration values for performance
     */
    private void cacheConfigValues() {
        pvpBlockEnabled = getConfig().getBoolean("pvp-block.enabled", true);
        cooldownEnabled = getConfig().getBoolean("cooldown.enabled", true);
        soundsEnabled = getConfig().getBoolean("sounds.enabled", true);
        debugEnabled = getConfig().getBoolean("settings.debug", false);
        visualCooldownEnabled = getConfig().getBoolean("cooldown.visual-display.enabled", true);
        
        if (debugEnabled) {
            debugLog("Конфигурация кэширована:");
            debugLog("- PvP блокировка: " + pvpBlockEnabled);
            debugLog("- Кулдауны: " + cooldownEnabled);
            debugLog("- Звуки: " + soundsEnabled);
            debugLog("- Визуальные кулдауны: " + visualCooldownEnabled);
        }
    }
    
    /**
     * Initialize all managers in proper dependency order
     * @return true if all managers initialized successfully
     */
    private boolean initializeManagers() {
        try {
            // Core managers first (no dependencies)
            localeManager = new LocaleManager(this);
            colorManager = new ColorManager(this);
            soundManager = new SoundManager(this);
            worldManager = new WorldManager(this);
            itemBlacklistManager = new ItemBlacklistManager(this);
            
            // Combat and cooldown managers
            combatManager = new CombatManager(this);
            cooldownManager = new CooldownManager(this);
            cooldownDisplayManager = new CooldownDisplayManager(this);
            
            // Inventory manager last (depends on others)
            inventoryManager = new ShulkerInventoryManager(this);
            
            if (debugEnabled) {
                debugLog("Все менеджеры успешно инициализированы");
            }
            
            return true;
            
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Ошибка инициализации менеджеров:", e);
            return false;
        }
    }
    
    /**
     * Register all event listeners
     */
    private void registerEventListeners() {
        PluginManager pluginManager = getServer().getPluginManager();
        
        pluginManager.registerEvents(new PlayerInteractListener(this), this);
        pluginManager.registerEvents(new InventoryListener(this), this);
        pluginManager.registerEvents(new PlayerListener(this), this);
        
        if (debugEnabled) {
            debugLog("Слушатели событий зарегистрированы");
        }
    }
    
    /**
     * Register plugin commands
     */
    private void registerCommands() {
        PluginCommand command = getCommand("shulkerking");
        if (command != null) {
            command.setExecutor(new ShulkerKingCommand(this));
            if (debugEnabled) {
                debugLog("Команды зарегистрированы");
            }
        } else {
            getLogger().warning("Не удалось зарегистрировать команду /shulkerking!");
        }
    }
    
    /**
     * Schedule asynchronous tasks for performance
     */
    private void scheduleAsyncTasks() {
        // Schedule periodic cleanup tasks
        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            if (cooldownManager != null) {
                cooldownManager.cleanupExpiredCooldowns();
            }
            if (combatManager != null) {
                combatManager.cleanupExpiredCombat();
            }
        }, 20L * 60L, 20L * 60L); // Every minute
        
        if (debugEnabled) {
            debugLog("Асинхронные задачи запланированы");
        }
    }
    
    /**
     * Reload plugin configuration and refresh all cached values
     */
    public void reloadPluginConfig() {
        try {
            reloadConfig();
            cacheConfigValues();
            
            // Clear runtime data that might be affected by config changes
            if (combatManager != null) {
                combatManager.clearAllCombat();
            }
            if (cooldownManager != null) {
                cooldownManager.clearAllCooldowns();
            }
            if (cooldownDisplayManager != null) {
                cooldownDisplayManager.stopAllCountdowns();
            }
            if (localeManager != null) {
                localeManager.reloadLanguages();
            }
            
            // Restore visual cooldowns for online players
            for (org.bukkit.entity.Player player : getServer().getOnlinePlayers()) {
                for (org.bukkit.inventory.ItemStack item : player.getInventory().getContents()) {
                    if (item != null && cooldownManager.hasCooldown(player, item)) {
                        String itemIdentifier = cooldownManager.getItemIdentifier(item);
                        cooldownDisplayManager.startVisualCountdown(player, itemIdentifier);
                    }
                }
            }
            
            getLogger().info("Конфигурация успешно перезагружена!");
            
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Ошибка при перезагрузке конфигурации:", e);
            throw e;
        }
    }
    
    /**
     * Get plugin instance for static access
     * @return plugin instance
     */
    public static ShulkerKingPlugin getInstance() {
        return instance;
    }
    
    public ShulkerInventoryManager getInventoryManager() {
        return inventoryManager;
    }
    
    public CombatManager getCombatManager() {
        return combatManager;
    }
    
    public LocaleManager getLocaleManager() {
        return localeManager;
    }
    
    public CooldownManager getCooldownManager() {
        return cooldownManager;
    }
    
    public ColorManager getColorManager() {
        return colorManager;
    }
    
    public SoundManager getSoundManager() {
        return soundManager;
    }
    
    public WorldManager getWorldManager() {
        return worldManager;
    }
    
    public ItemBlacklistManager getItemBlacklistManager() {
        return itemBlacklistManager;
    }
    
    public CooldownDisplayManager getCooldownDisplayManager() {
        return cooldownDisplayManager;
    }
    
    /**
     * Get localized and colorized message for player
     * @param player target player
     * @param key message key
     * @return formatted message
     */
    public String getMessage(org.bukkit.entity.Player player, String key) {
        if (colorManager == null || localeManager == null) {
            return key; // Fallback during initialization
        }
        return colorManager.colorize(localeManager.getMessage(player, key));
    }
    
    /**
     * Get localized and colorized message with default locale
     * @param key message key
     * @return formatted message
     */
    public String getMessage(String key) {
        if (colorManager == null || localeManager == null) {
            return key; // Fallback during initialization
        }
        return colorManager.colorize(localeManager.getMessage("en_us", key));
    }
    
    public boolean isDebugEnabled() {
        return debugEnabled;
    }
    
    public boolean isPvpBlockEnabled() {
        return pvpBlockEnabled;
    }
    
    public boolean isCooldownEnabled() {
        return cooldownEnabled;
    }
    
    public boolean isSoundsEnabled() {
        return soundsEnabled;
    }
    
    /**
     * Log debug message if debug mode is enabled
     * @param message debug message
     */
    public void debugLog(String message) {
        if (isDebugEnabled()) {
            getLogger().info("[DEBUG] " + message);
        }
    }
    
    /**
     * Check if visual cooldown is enabled
     * @return true if enabled
     */
    public boolean isVisualCooldownEnabled() {
        return visualCooldownEnabled;
    }
    
    /**
     * Get all manager instances as a formatted string for debugging
     * @return manager status string
     */
    public String getManagerStatus() {
        StringBuilder status = new StringBuilder("Manager Status:\n");
        status.append("- LocaleManager: ").append(localeManager != null ? "OK" : "NULL").append("\n");
        status.append("- ColorManager: ").append(colorManager != null ? "OK" : "NULL").append("\n");
        status.append("- SoundManager: ").append(soundManager != null ? "OK" : "NULL").append("\n");
        status.append("- WorldManager: ").append(worldManager != null ? "OK" : "NULL").append("\n");
        status.append("- ItemBlacklistManager: ").append(itemBlacklistManager != null ? "OK" : "NULL").append("\n");
        status.append("- CombatManager: ").append(combatManager != null ? "OK" : "NULL").append("\n");
        status.append("- CooldownManager: ").append(cooldownManager != null ? "OK" : "NULL").append("\n");
        status.append("- CooldownDisplayManager: ").append(cooldownDisplayManager != null ? "OK" : "NULL").append("\n");
        status.append("- InventoryManager: ").append(inventoryManager != null ? "OK" : "NULL");
        return status.toString();
    }
}
