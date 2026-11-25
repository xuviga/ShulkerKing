package com.shulkerking.managers;

import com.shulkerking.ShulkerKingPlugin;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CooldownManager {

    private final ShulkerKingPlugin plugin;
    // Ключ: "UUID_игрока:уникальный_ID_предмета"
    // ConcurrentHashMap: очистка выполняется в асинхронной задаче
    private final Map<String, Long> cooldowns;

    public CooldownManager(ShulkerKingPlugin plugin) {
        this.plugin = plugin;
        this.cooldowns = new ConcurrentHashMap<>();
    }

    /**
     * Создает стабильный уникальный идентификатор для предмета.
     * ВАЖНО: содержимое шалкера и лор кулдауна в ID НЕ входят - иначе
     * после любого изменения содержимого менялся ключ и кулдаун "сбрасывался".
     */
    public String getItemIdentifier(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return item == null ? "null_item" : item.getType().name();
        }
        ItemMeta meta = item.getItemMeta().clone();

        // Очищаем лор от строк кулдауна для стабильного ID
        if (meta.hasLore()) {
            List<String> cleanLore = new ArrayList<>();
            for (String line : meta.getLore()) {
                String strippedLine = ChatColor.stripColor(line).toLowerCase();
                if (!strippedLine.contains("cooldown") && !strippedLine.contains("кулдаун") &&
                    !strippedLine.contains("ready") && !strippedLine.contains("готов")) {
                    cleanLore.add(line);
                }
            }
            meta.setLore(cleanLore.isEmpty() ? null : cleanLore);
        }

        StringBuilder identifierBase = new StringBuilder(item.getType().name());
        identifierBase.append('|').append(meta.hasDisplayName() ? meta.getDisplayName() : "");
        identifierBase.append('|').append(meta.hasLore() ? meta.getLore() : "");

        // Без hashCode: коллизии хешей давали ложные срабатывания кулдауна
        return identifierBase.toString();
    }

    private String createCooldownKey(Player player, ItemStack item) {
        return player.getUniqueId().toString() + ":" + getItemIdentifier(item);
    }

    public boolean hasCooldown(Player player, ItemStack item) {
        if (item == null || !plugin.isCooldownEnabled()) return false;
        String cooldownKey = createCooldownKey(player, item);
        
        if (!cooldowns.containsKey(cooldownKey)) {
            return false;
        }

        long cooldownEnd = cooldowns.get(cooldownKey);
        if (System.currentTimeMillis() >= cooldownEnd) {
            cooldowns.remove(cooldownKey);
            return false;
        }
        return true;
    }

    public double getRemainingCooldown(Player player, ItemStack item) {
        if (!hasCooldown(player, item)) {
            return 0.0;
        }
        String cooldownKey = createCooldownKey(player, item);
        long cooldownEnd = cooldowns.get(cooldownKey);
        return (cooldownEnd - System.currentTimeMillis()) / 1000.0;
    }

    public void setCooldown(Player player, ItemStack item) {
        if (item == null || !plugin.isCooldownEnabled()) return;
        
        double cooldownTime = getCooldownTime(player);
        if (cooldownTime <= 0) {
            return; // Кулдаун не требуется
        }

        String cooldownKey = createCooldownKey(player, item);
        long cooldownEnd = System.currentTimeMillis() + (long) (cooldownTime * 1000);
        cooldowns.put(cooldownKey, cooldownEnd);
        plugin.debugLog("Установлен кулдаун для " + player.getName() + " на предмет " + getItemIdentifier(item) + " на " + cooldownTime + "с");
    }

    /**
     * Очищает все активные кулдауны.
     */
    public void clearAllCooldowns() {
        cooldowns.clear();
        plugin.debugLog("Все кулдауны были очищены.");
    }

    /**
     * Удаляет истекшие кулдауны для предотвращения утечек памяти.
     */
    public void cleanupExpiredCooldowns() {
        long now = System.currentTimeMillis();
        cooldowns.entrySet().removeIf(entry -> entry.getValue() <= now);
    }

    public double getCooldownTime(Player player) {
        // Проверка права на обход кулдауна
        if (player.hasPermission("shulkerking.cooldown.bypass")) {
             if (player.isOp() && !player.isPermissionSet("shulkerking.cooldown.bypass")) {
                 // OP без явного права, используется стандартный кулдаун
             } else {
                plugin.debugLog("Игрок " + player.getName() + " имеет право на обход кулдауна.");
                return 0.0;
             }
        }

        // Проверка прав на кастомные кулдауны
        if (player.hasPermission("shulkerking.cooldown.premium")) {
            if (!player.isOp() || player.isPermissionSet("shulkerking.cooldown.premium")) {
                 double time = getConfiguredCooldown("cooldown.premium", 1.0);
                 plugin.debugLog("У игрока " + player.getName() + " премиум кулдаун: " + time + "с");
                 return time;
            }
        }
        if (player.hasPermission("shulkerking.cooldown.vip")) {
             if (!player.isOp() || player.isPermissionSet("shulkerking.cooldown.vip")) {
                double time = getConfiguredCooldown("cooldown.vip", 2.0);
                plugin.debugLog("У игрока " + player.getName() + " VIP кулдаун: " + time + "с");
                return time;
             }
        }

        // Стандартный кулдаун (поддержаны оба имени ключа: cooldown.default и cooldown.default-time)
        double defaultTime = getConfiguredCooldown("cooldown.default", 3.0);
        plugin.debugLog("Игроку " + player.getName() + " назначен стандартный кулдаун: " + defaultTime + "с");
        return defaultTime;
    }

    /**
     * Читает кулдаун из конфига, поддерживая и новый, и старый (README) формат ключей:
     * cooldown.default / cooldown.default-time и cooldown.permission-overrides.&lt;permission&gt;.
     */
    private double getConfiguredCooldown(String key, double fallback) {
        if (plugin.getConfig().isSet(key)) {
            return plugin.getConfig().getDouble(key, fallback);
        }
        if ("cooldown.default".equals(key) && plugin.getConfig().isSet("cooldown.default-time")) {
            return plugin.getConfig().getDouble("cooldown.default-time", fallback);
        }
        String overrides = "cooldown.permission-overrides." + key.substring("cooldown.".length());
        if (plugin.getConfig().isSet(overrides)) {
            return plugin.getConfig().getDouble(overrides, fallback);
        }
        return fallback;
    }
}
