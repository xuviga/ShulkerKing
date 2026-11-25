package com.shulkerking.util;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Работа с предметом в конкретной руке.
 *
 * Вынесено отдельно, потому что именно здесь был дюп из отзыва
 * "взял шалкер в левую руку - ставлю один и тот же бесконечно":
 * старый код всегда списывал предмет из ГЛАВНОЙ руки (held slot),
 * а офф-хенд оставался нетронутым.
 */
public final class HandItemUtil {

    private HandItemUtil() {
    }

    /**
     * Списывает ровно один предмет из указанной руки.
     *
     * @param player игрок
     * @param hand   рука, которой игрок взаимодействовал
     * @return true если предмет был списан
     */
    public static boolean consumeOne(Player player, EquipmentSlot hand) {
        if (player == null) {
            return false;
        }

        if (hand == EquipmentSlot.OFF_HAND) {
            ItemStack item = player.getInventory().getItemInOffHand();
            if (isEmpty(item)) {
                return false;
            }
            if (item.getAmount() > 1) {
                item.setAmount(item.getAmount() - 1);
            } else {
                player.getInventory().setItemInOffHand(null);
            }
            return true;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (isEmpty(item)) {
            return false;
        }
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }
        return true;
    }

    private static boolean isEmpty(ItemStack item) {
        return item == null || item.getType() == Material.AIR || item.getAmount() <= 0;
    }
}
