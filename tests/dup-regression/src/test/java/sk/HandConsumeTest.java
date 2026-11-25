package sk;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import com.shulkerking.util.HandItemUtil;
import org.bukkit.Material;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Регрессия на дюп из отзыва:
 * "если взять шалкер в левую руку, то можно постоянно ставить один и тот же шалкер".
 * Причина была в списании предмета из ГЛАВНОЙ руки при взаимодействии офф-хендом.
 */
class HandConsumeTest {

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void offhandConsumptionDoesNotTouchMainHand() {
        PlayerMock player = server.addPlayer("Tester");
        player.getInventory().setItemInMainHand(new ItemStack(Material.IRON_SWORD));
        player.getInventory().setItemInOffHand(new ItemStack(Material.SHULKER_BOX));

        boolean consumed = HandItemUtil.consumeOne(player, EquipmentSlot.OFF_HAND);

        assertEquals(true, consumed, "предмет в офф-хенде должен быть списан");
        assertEquals(Material.IRON_SWORD, player.getInventory().getItemInMainHand().getType(),
            "главная рука НЕ должна списываться");
        assertEquals(Material.AIR, player.getInventory().getItemInOffHand().getType(),
            "офф-хенд должен опустеть");
    }

    @Test
    void offhandStackDecrementsByOne() {
        PlayerMock player = server.addPlayer("Tester");
        player.getInventory().setItemInOffHand(new ItemStack(Material.SHULKER_BOX, 3));

        HandItemUtil.consumeOne(player, EquipmentSlot.OFF_HAND);

        assertEquals(2, player.getInventory().getItemInOffHand().getAmount(),
            "из стака должен уйти ровно один предмет");
        assertEquals(Material.SHULKER_BOX, player.getInventory().getItemInOffHand().getType());
    }

    @Test
    void repeatedOffhandPlacementStopsWhenEmpty() {
        PlayerMock player = server.addPlayer("Tester");
        player.getInventory().setItemInMainHand(new ItemStack(Material.IRON_SWORD));
        player.getInventory().setItemInOffHand(new ItemStack(Material.SHULKER_BOX));

        assertEquals(true, HandItemUtil.consumeOne(player, EquipmentSlot.OFF_HAND));
        // второй раз ставить нечего - и главная рука не должна пострадать
        assertEquals(false, HandItemUtil.consumeOne(player, EquipmentSlot.OFF_HAND));
        assertEquals(Material.IRON_SWORD, player.getInventory().getItemInMainHand().getType(),
            "главная рука должна остаться нетронутой после повторных попыток");
    }

    @Test
    void mainHandConsumptionStillWorks() {
        PlayerMock player = server.addPlayer("Tester");
        player.getInventory().setItemInMainHand(new ItemStack(Material.SHULKER_BOX));
        player.getInventory().setItemInOffHand(new ItemStack(Material.SHULKER_BOX));

        assertEquals(true, HandItemUtil.consumeOne(player, EquipmentSlot.HAND));
        assertEquals(Material.AIR, player.getInventory().getItemInMainHand().getType());
        assertEquals(Material.SHULKER_BOX, player.getInventory().getItemInOffHand().getType(),
            "офф-хенд не должен трогаться при работе главной рукой");
    }
}
