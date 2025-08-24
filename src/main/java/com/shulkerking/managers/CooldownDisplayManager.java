package com.shulkerking.managers;

import com.shulkerking.ShulkerKingPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CooldownDisplayManager {

    private final ShulkerKingPlugin plugin;
    // Ключ: "UUID_игрока:уникальный_ID_предмета"
    private final Map<String, BukkitRunnable> activeCountdowns;

    public CooldownDisplayManager(ShulkerKingPlugin plugin) {
        this.plugin = plugin;
        this.activeCountdowns = new ConcurrentHashMap<>();
    }

    /**
     * Создает ключ для визуального кулдауна на основе игрока и ID предмета.
     */
    private String createDisplayKey(Player player, String itemIdentifier) {
        return player.getUniqueId().toString() + ":" + itemIdentifier;
    }

    /**
     * Находит предмет в инвентаре игрока по его уникальному идентификатору.
     * @return Найденный ItemStack или null, если предмет не найден.
     */
    private ItemStack findItemInInventory(Player player, String itemIdentifier) {
        // Проверяем основную и вторую руку
        ItemStack mainHandItem = player.getInventory().getItemInMainHand();
        if (mainHandItem != null && itemIdentifier.equals(plugin.getCooldownManager().getItemIdentifier(mainHandItem))) {
            return mainHandItem;
        }
        ItemStack offHandItem = player.getInventory().getItemInOffHand();
        if (offHandItem != null && itemIdentifier.equals(plugin.getCooldownManager().getItemIdentifier(offHandItem))) {
            return offHandItem;
        }

        // Проверяем остальной инвентарь
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && isShulkerBox(item)) {
                if (itemIdentifier.equals(plugin.getCooldownManager().getItemIdentifier(item))) {
                    return item;
                }
            }
        }
        return null;
    }

    /**
     * Запускает визуальный кулдаун для предмета, который обновляется каждую секунду.
     */
    public void startVisualCountdown(Player player, String itemIdentifier) {
        if (!plugin.getConfig().getBoolean("cooldown.visual-display.enabled", true) ||
            !plugin.getConfig().getBoolean("cooldown.visual-display.live-update", true)) {
            return;
        }

        if (itemIdentifier == null) {
            plugin.debugLog("Попытка запустить таймер для null идентификатора.");
            return;
        }

        String displayKey = createDisplayKey(player, itemIdentifier);
        stopVisualCountdown(player, itemIdentifier); // Останавливаем предыдущий таймер для этого предмета

        // Сохраняем оригинальные данные предмета (без лора кулдауна)
        ItemStack currentItem = findItemInInventory(player, itemIdentifier);
        if (currentItem == null) {
            plugin.debugLog("Предмет для кулдауна не найден в инвентаре " + player.getName() + ".");
            return;
        }

        ItemMeta cleanMeta = currentItem.getItemMeta();
        if (cleanMeta == null) return;
        String originalName = cleanMeta.hasDisplayName() ? cleanMeta.getDisplayName() : null;
        List<String> originalLore = cleanMeta.hasLore() ? new ArrayList<>(cleanMeta.getLore()) : new ArrayList<>();

        // ОЧИСТКА: Удаляем старые сообщения кулдауна/готовности перед сохранением "оригинального" состояния.
        String readyMessage = plugin.getConfig().getString("cooldown.visual-display.ready-message", "&aГотов к открытию");
        String readyMessageTranslated = ChatColor.translateAlternateColorCodes('&', readyMessage);
        String cooldownFormat = plugin.getConfig().getString("cooldown.visual-display.format", "&7Кулдаун: &c{time}с");
        String cooldownPrefix = ChatColor.translateAlternateColorCodes('&', cooldownFormat.split("\\{")[0]);

        originalLore.removeIf(line -> line.startsWith(cooldownPrefix) || line.equals(readyMessageTranslated));

        BukkitRunnable countdownTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancelTask();
                    return;
                }

                // Находим актуальный предмет в инвентаре
                ItemStack currentItem = findItemInInventory(player, itemIdentifier);
                if (currentItem == null) {
                    plugin.debugLog("Предмет для кулдауна не найден в инвентаре " + player.getName() + ". Отмена таймера.");
                    cancelTask();
                    return;
                }

                double remainingSeconds = plugin.getCooldownManager().getRemainingCooldown(player, currentItem);

                if (remainingSeconds <= 0) {
                    showReadyMessage(player, currentItem, originalName, originalLore);
                    cancelTask();
                    return;
                }

                updateItemDisplay(currentItem, remainingSeconds, originalName, originalLore);
                player.updateInventory(); // Обновляем инвентарь, чтобы игрок видел изменения
            }

            private void cancelTask() {
                activeCountdowns.remove(displayKey);
                if (!isCancelled()) {
                    cancel();
                }
            }
        };

        activeCountdowns.put(displayKey, countdownTask);
        countdownTask.runTaskTimer(plugin, 0, 20); // Запускаем каждую секунду
    }

    /**
     * Останавливает визуальный кулдаун для конкретного предмета.
     */
    public void stopVisualCountdown(Player player, String itemIdentifier) {
        String displayKey = createDisplayKey(player, itemIdentifier);
        BukkitRunnable task = activeCountdowns.remove(displayKey);
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }

    /**
     * Останавливает все активные визуальные кулдауны.
     */
    public void stopAllCountdowns() {
        activeCountdowns.values().forEach(task -> {
            if (!task.isCancelled()) {
                task.cancel();
            }
        });
        activeCountdowns.clear();
    }

    /**
     * Обновляет лор предмета, отображая оставшееся время кулдауна.
     */
    private void updateItemDisplay(ItemStack item, double remainingSeconds, String originalName, List<String> originalLore) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        // Восстанавливаем оригинальное имя и лор
        meta.setDisplayName(originalName);
        meta.setLore(originalLore != null ? new ArrayList<>(originalLore) : new ArrayList<>());

        // Добавляем строку кулдауна
        List<String> newLore = meta.getLore();
        if (newLore == null) {
            newLore = new ArrayList<>();
        }
        String format = plugin.getConfig().getString("cooldown.visual-display.format", "&7Кулдаун: &c{time}с");
        String cooldownLine = ChatColor.translateAlternateColorCodes('&', format.replace("{time}", String.format("%.0f", remainingSeconds)));
        newLore.add(cooldownLine);
        meta.setLore(newLore);

        item.setItemMeta(meta);
    }

    /**
     * Показывает сообщение "Готов" и планирует его удаление.
     */
    private void showReadyMessage(Player player, ItemStack item, String originalName, List<String> originalLore) {
        if (item == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        // Восстанавливаем оригинальное состояние
        meta.setDisplayName(originalName);
        meta.setLore(originalLore != null ? new ArrayList<>(originalLore) : new ArrayList<>());

        // Добавляем сообщение "Готов"
        List<String> newLore = meta.getLore();
        if (newLore == null) {
            newLore = new ArrayList<>();
        }
        String readyMessage = plugin.getConfig().getString("cooldown.visual-display.ready-message", "&aГотов к открытию");
        newLore.add(ChatColor.translateAlternateColorCodes('&', readyMessage));
        meta.setLore(newLore);
        item.setItemMeta(meta);
        player.updateInventory();

        // Планируем удаление сообщения "Готов"
        new BukkitRunnable() {
            @Override
            public void run() {
                // Находим предмет снова, чтобы убедиться, что он все еще существует
                String itemIdentifier = plugin.getCooldownManager().getItemIdentifier(item);
                ItemStack latestItem = findItemInInventory(player, itemIdentifier);
                if (latestItem != null) {
                    ItemMeta latestMeta = latestItem.getItemMeta();
                    if (latestMeta != null) {
                        latestMeta.setDisplayName(originalName);
                        latestMeta.setLore(originalLore);
                        latestItem.setItemMeta(latestMeta);
                        player.updateInventory();
                    }
                }
            }
        }.runTaskLater(plugin, plugin.getConfig().getInt("cooldown.visual-display.ready-message-duration", 40));
    }

    /**
     * Очищает существующие строки кулдауна из метаданных предмета.
     * Этот метод теперь не нужен, так как мы полностью восстанавливаем лор.
     */
    private void cleanExistingCooldownDisplay(ItemMeta meta) {
        // Логика очистки больше не требуется, так как мы работаем с чистой копией лора
    }

    private boolean isShulkerBox(ItemStack item) {
        return item != null && item.getType().name().endsWith("_SHULKER_BOX");
    }
}
