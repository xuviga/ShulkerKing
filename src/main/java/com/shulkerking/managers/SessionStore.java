package com.shulkerking.managers;

import com.shulkerking.ShulkerKingPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Персистентное хранилище открытых сессий шалкер-боксов.
 *
 * Пока сессия открыта, содержимое живёт ТОЛЬКО в ней (предмет в руке пуст и
 * помечен session-id), поэтому копии предметов не существует. Каждое изменение
 * помечает сессию "грязной", периодический flush пишет sessions.yml.
 * Если сервер упал или игрок вышел с открытым GUI, содержимое восстанавливается
 * при следующем входе игрока (см. ShulkerInventoryManager#restorePendingSession).
 */
public class SessionStore {

    private final ShulkerKingPlugin plugin;
    private final File file;
    private final YamlConfiguration data;
    private volatile boolean dirty;

    public SessionStore(ShulkerKingPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "sessions.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        if (!data.isConfigurationSection("sessions")) {
            data.createSection("sessions");
        }
    }

    /**
     * Сохраняет состояние сессии в память хранилища (на диск - при flush).
     */
    public void save(ShulkerInventoryManager.ShulkerSession session) {
        if (session == null) return;
        String path = "sessions." + session.getPlayerId();

        data.set(path + ".session-id", session.getSessionId());
        data.set(path + ".main-hand", session.isMainHand());
        data.set(path + ".started", session.getStartTime());
        data.set(path + ".template", session.getTemplate());

        ConfigurationSection contents = data.createSection(path + ".contents");
        ItemStack[] items = session.getContentsClone();
        for (int i = 0; i < items.length; i++) {
            if (items[i] != null) {
                contents.set(String.valueOf(i), items[i]);
            }
        }
        dirty = true;
    }

    /**
     * Забирает сессию из хранилища (для восстановления) и удаляет её оттуда.
     * @return сессия или null
     */
    public ShulkerInventoryManager.ShulkerSession take(UUID playerId) {
        ShulkerInventoryManager.ShulkerSession session = read(playerId);
        if (session != null) {
            remove(playerId);
        }
        return session;
    }

    /**
     * Читает сессию, не удаляя её.
     */
    public ShulkerInventoryManager.ShulkerSession read(UUID playerId) {
        String path = "sessions." + playerId;
        ConfigurationSection section = data.getConfigurationSection(path);
        if (section == null) {
            return null;
        }

        String sessionId = section.getString("session-id");
        ItemStack template = section.getItemStack("template");
        if (sessionId == null || template == null) {
            plugin.getLogger().warning("Повреждённая запись сессии для " + playerId + ", удаляю");
            remove(playerId);
            return null;
        }

        ItemStack[] contents = new ItemStack[27];
        ConfigurationSection items = section.getConfigurationSection("contents");
        if (items != null) {
            for (String key : items.getKeys(false)) {
                try {
                    int slot = Integer.parseInt(key);
                    if (slot >= 0 && slot < contents.length) {
                        contents[slot] = items.getItemStack(key);
                    }
                } catch (NumberFormatException ignored) {
                    // мусорный ключ - пропускаем
                }
            }
        }

        return new ShulkerInventoryManager.ShulkerSession(
            playerId,
            template,
            section.getBoolean("main-hand", true),
            section.getLong("started", System.currentTimeMillis()),
            sessionId,
            contents
        );
    }

    public void remove(UUID playerId) {
        data.set("sessions." + playerId, null);
        dirty = true;
    }

    public int pendingCount() {
        ConfigurationSection section = data.getConfigurationSection("sessions");
        return section == null ? 0 : section.getKeys(false).size();
    }

    /**
     * Сбрасывает состояние на диск (синхронно, вызывается из главного потока).
     */
    public void flush() {
        if (!dirty) return;
        try {
            if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
                plugin.getLogger().warning("Не удалось создать папку плагина для sessions.yml");
            }
            data.save(file);
            dirty = false;
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Не удалось сохранить sessions.yml", e);
        }
    }
}
