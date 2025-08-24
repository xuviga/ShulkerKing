package com.shulkerking.managers;

import com.shulkerking.ShulkerKingPlugin;
import org.bukkit.ChatColor;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CooldownManager {

    private final ShulkerKingPlugin plugin;
    // Ключ: "UUID_игрока:уникальный_ID_предмета"
    private final Map<String, Long> cooldowns;

    public CooldownManager(ShulkerKingPlugin plugin) {
        this.plugin = plugin;
        this.cooldowns = new HashMap<>();
    }

    /**
     * Создает стабильный уникальный идентификатор для предмета, игнорируя лор с кулдауном.
     */
    public String getItemIdentifier(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return "null_item";
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
        if (meta.hasDisplayName()) {
            identifierBase.append(meta.getDisplayName());
        }
        if (meta.hasLore()) {
            identifierBase.append(meta.getLore().toString());
        }
        
        // Для шалкеров включаем в ID их содержимое
        if (meta instanceof BlockStateMeta) {
            BlockStateMeta blockMeta = (BlockStateMeta) meta;
            if (blockMeta.getBlockState() instanceof ShulkerBox) {
                ShulkerBox shulker = (ShulkerBox) blockMeta.getBlockState();
                for (ItemStack contentItem : shulker.getInventory().getContents()) {
                    if (contentItem != null) {
                        identifierBase.append(contentItem.toString());
                    }
                }
            }
        }

        return String.valueOf(identifierBase.toString().hashCode());
    }

    private String createCooldownKey(Player player, ItemStack item) {
        return player.getUniqueId().toString() + ":" + getItemIdentifier(item);
    }

    public boolean hasCooldown(Player player, ItemStack item) {
        if (item == null) return false;
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
        if (item == null) return;
        
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
                 double time = plugin.getConfig().getDouble("cooldown.premium", 1.0);
                 plugin.debugLog("У игрока " + player.getName() + " премиум кулдаун: " + time + "с");
                 return time;
            }
        }
        if (player.hasPermission("shulkerking.cooldown.vip")) {
             if (!player.isOp() || player.isPermissionSet("shulkerking.cooldown.vip")) {
                double time = plugin.getConfig().getDouble("cooldown.vip", 2.0);
                plugin.debugLog("У игрока " + player.getName() + " VIP кулдаун: " + time + "с");
                return time;
             }
        }

        // Стандартный кулдаун
        double defaultTime = plugin.getConfig().getDouble("cooldown.default", 3.0);
        plugin.debugLog("Игроку " + player.getName() + " назначен стандартный кулдаун: " + defaultTime + "с");
        return defaultTime;
    }
}
