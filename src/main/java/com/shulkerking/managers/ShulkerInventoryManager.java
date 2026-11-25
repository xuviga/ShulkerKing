package com.shulkerking.managers;

import com.shulkerking.ShulkerKingPlugin;
import com.shulkerking.api.events.ShulkerCloseEvent;
import com.shulkerking.api.events.ShulkerOpenEvent;
import com.shulkerking.holders.ShulkerInventoryHolder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Управление открытыми шалкер-боксами.
 *
 * АНТИ-ДЮП МОДЕЛЬ:
 * 1) При открытии содержимое переносится из предмета в сессию, а предмет в руке
 *    ОЧИЩАЕТСЯ и помечается session-id в PersistentDataContainer.
 * 2) Пока GUI открыт, единственная копия содержимого - сессия (и её запись в
 *    sessions.yml). Второй копии в мире не существует.
 * 3) При закрытии содержимое пишется обратно в помеченный предмет, метка снимается.
 * 4) Если предмет потерян или сервер упал - сессия остаётся в sessions.yml и
 *    восстанавливается при следующем входе игрока.
 */
public class ShulkerInventoryManager {

    private final ShulkerKingPlugin plugin;
    private final Map<UUID, ShulkerSession> activeSessions;
    private final Map<UUID, Long> placementActions;
    private final NamespacedKey sessionKey;

    public ShulkerInventoryManager(ShulkerKingPlugin plugin) {
        this.plugin = plugin;
        this.activeSessions = new ConcurrentHashMap<>();
        this.placementActions = new ConcurrentHashMap<>();
        this.sessionKey = new NamespacedKey(plugin, "session");
    }

    // ------------------------------------------------------------------
    // Открытие / закрытие
    // ------------------------------------------------------------------

    public boolean openShulkerInventory(Player player, ItemStack shulkerItem, boolean isMainHand) {
        if (player == null || !player.isOnline()) {
            plugin.debugLog("Failed to open: player is null or offline");
            return false;
        }

        if (shulkerItem == null || !isShulkerBox(shulkerItem)) {
            plugin.debugLog("Failed to open: not a shulker box for " + player.getName());
            return false;
        }

        if (shulkerItem.getAmount() != 1) {
            plugin.debugLog("Failed to open: shulker stack size is not 1 for " + player.getName());
            return false;
        }

        if (hasActiveSession(player)) {
            plugin.debugLog("Closing existing session for " + player.getName());
            closeShulkerInventory(player);
        }

        ItemStack handItem = isMainHand
            ? player.getInventory().getItemInMainHand()
            : player.getInventory().getItemInOffHand();

        if (handItem == null || !isShulkerBox(handItem) || handItem.getAmount() != 1) {
            plugin.debugLog("Failed to open: hand item is not a single shulker for " + player.getName());
            return false;
        }

        if (!(handItem.getItemMeta() instanceof BlockStateMeta)) {
            plugin.debugLog("Failed to open: no BlockStateMeta for " + player.getName());
            return false;
        }

        BlockStateMeta meta = (BlockStateMeta) handItem.getItemMeta();
        if (!(meta.getBlockState() instanceof ShulkerBox)) {
            plugin.debugLog("Failed to open: no shulker state for " + player.getName());
            return false;
        }

        // API: даём другим плагинам запретить открытие
        ShulkerOpenEvent openEvent = new ShulkerOpenEvent(player, handItem.clone());
        Bukkit.getPluginManager().callEvent(openEvent);
        if (openEvent.isCancelled()) {
            plugin.debugLog("Opening cancelled by another plugin for " + player.getName());
            return false;
        }

        ShulkerBox sourceBox = (ShulkerBox) meta.getBlockState();
        ItemStack[] contents = sourceBox.getInventory().getContents();

        // Пустой шаблон (тип, имя, лор, но без содержимого)
        ItemStack template = handItem.clone();
        emptyBlockState(template);

        String sessionId = UUID.randomUUID().toString();

        // Очищаем предмет в руке и помечаем его session-id
        if (!tagItem(handItem, sessionId)) {
            plugin.debugLog("Failed to tag shulker item for " + player.getName());
            return false;
        }
        if (isMainHand) {
            player.getInventory().setItemInMainHand(handItem);
        } else {
            player.getInventory().setItemInOffHand(handItem);
        }

        ShulkerSession session = new ShulkerSession(
            player.getUniqueId(),
            template,
            isMainHand,
            System.currentTimeMillis(),
            sessionId,
            contents
        );

        ShulkerInventoryHolder holder = new ShulkerInventoryHolder(player, session);
        Inventory inventory = Bukkit.createInventory(holder, 27, getGuiTitle(player));
        holder.setInventory(inventory);
        inventory.setContents(contents);

        activeSessions.put(player.getUniqueId(), session);
        plugin.getSessionStore().save(session);
        plugin.getSessionStore().flush();

        plugin.debugLog("Opening shulker inventory for " + player.getName()
            + " (main hand: " + isMainHand + ", session: " + sessionId + ")");

        player.openInventory(inventory);
        player.updateInventory();
        return true;
    }

    /**
     * Синхронизирует сессию с GUI и пишет её в sessions.yml.
     * В предмет содержимое НЕ пишется: предмет пуст до закрытия сессии.
     */
    public void saveShulkerContents(Player player, ShulkerSession session, Inventory inventory) {
        if (session == null || inventory == null) {
            return;
        }
        session.setContents(inventory.getContents());
        plugin.getSessionStore().save(session);
        plugin.debugLog("[SESSION] Synced " + countItems(session.getContentsClone())
            + " items for " + player.getName());
    }

    public void closeShulkerInventory(Player player) {
        closeShulkerInventory(player, true);
    }

    /**
     * Закрывает сессию и записывает содержимое обратно в предмет.
     *
     * @param giveIfMissing если предмет с меткой не найден - выдать новый предмет
     *                      в инвентарь/на землю. false = оставить сессию в
     *                      sessions.yml для восстановления при следующем входе.
     */
    public void closeShulkerInventory(Player player, boolean giveIfMissing) {
        ShulkerSession session = activeSessions.remove(player.getUniqueId());
        if (session == null) {
            plugin.debugLog("[CLOSE] No active session found for " + player.getName());
            return;
        }

        plugin.debugLog("[CLOSE] Closing shulker inventory for " + player.getName());

        // Забираем актуальное состояние из GUI
        Inventory top = player.getOpenInventory() != null
            ? player.getOpenInventory().getTopInventory()
            : null;
        if (top != null && top.getHolder() instanceof ShulkerInventoryHolder) {
            session.setContents(top.getContents());
        }

        ItemStack sessionItem = findSessionItem(player, session.getSessionId());
        int sessionSlot = findSessionSlot(player, session.getSessionId());
        ItemStack written = null;

        if (sessionItem != null && sessionSlot >= 0) {
            applyContents(sessionItem, session.getContentsClone());
            clearSessionTag(sessionItem);
            // Явная запись обратно в слот: не полагаемся на mirror-семантику
            player.getInventory().setItem(sessionSlot, sessionItem);
            written = sessionItem;
            plugin.debugLog("[CLOSE] Contents written back to slot " + sessionSlot + " for " + player.getName());
        } else if (giveIfMissing) {
            ItemStack restored = session.getTemplate().clone();
            applyContents(restored, session.getContentsClone());
            clearSessionTag(restored);
            giveItem(player, restored);
            written = restored;
            plugin.getLogger().warning("[CLOSE] Shulker item of " + player.getName()
                + " was lost - item restored with " + countItems(session.getContentsClone()) + " items");
        } else {
            // Предмета нет (выход/смерть/краш) - оставляем запись для восстановления
            plugin.getSessionStore().save(session);
            plugin.getSessionStore().flush();
            player.sendMessage(plugin.getMessage(player, "messages.contents-saved"));
            plugin.debugLog("[CLOSE] Shulker item missing, session kept for restore for " + player.getName());
            player.closeInventory();
            Bukkit.getPluginManager().callEvent(new ShulkerCloseEvent(player, session.getSessionId(), null));
            return;
        }

        plugin.getSessionStore().remove(player.getUniqueId());
        plugin.getSessionStore().flush();

        player.closeInventory();
        player.updateInventory();
        Bukkit.getPluginManager().callEvent(new ShulkerCloseEvent(player, session.getSessionId(), written));
        plugin.debugLog("[CLOSE] Closed inventory for " + player.getName());
    }

    public void closeAllInventories() {
        List<UUID> ids = new ArrayList<>(activeSessions.keySet());
        for (UUID playerId : ids) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                closeShulkerInventory(player);
            }
        }
        activeSessions.clear();
        placementActions.clear();
    }

    /**
     * Восстанавливает сессию, оставшуюся после краша/выхода.
     * @return true если что-то восстановлено
     */
    public boolean restorePendingSession(Player player) {
        ShulkerSession stored = plugin.getSessionStore().take(player.getUniqueId());
        if (stored == null) {
            return false;
        }

        ItemStack sessionItem = findSessionItem(player, stored.getSessionId());
        int sessionSlot = findSessionSlot(player, stored.getSessionId());
        ItemStack target;

        if (sessionItem != null && sessionSlot >= 0) {
            target = sessionItem;
            applyContents(target, stored.getContentsClone());
            clearSessionTag(target);
            player.getInventory().setItem(sessionSlot, target);
            player.updateInventory();
        } else {
            target = stored.getTemplate().clone();
            applyContents(target, stored.getContentsClone());
            clearSessionTag(target);
            giveItem(player, target);
        }

        plugin.getSessionStore().flush();
        player.sendMessage(plugin.getMessage(player, "messages.contents-restored"));
        plugin.getLogger().info("Restored shulker session for " + player.getName()
            + " (" + countItems(stored.getContentsClone()) + " items)");
        return true;
    }

    // ------------------------------------------------------------------
    // Идентичность предмета (PersistentDataContainer)
    // ------------------------------------------------------------------

    /**
     * @return true если предмет - это шалкер, помеченный указанной сессией
     */
    public boolean isSessionItem(ItemStack item, String sessionId) {
        if (item == null || sessionId == null || !isShulkerBox(item)) {
            return false;
        }
        String tag = getSessionId(item);
        return sessionId.equals(tag);
    }

    public boolean isSessionItem(ItemStack item, ShulkerSession session) {
        return session != null && isSessionItem(item, session.getSessionId());
    }

    public String getSessionId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        return meta.getPersistentDataContainer().get(sessionKey, PersistentDataType.STRING);
    }

    private ItemStack findSessionItem(Player player, String sessionId) {
        int slot = findSessionSlot(player, sessionId);
        return slot >= 0 ? player.getInventory().getItem(slot) : null;
    }

    /**
     * Ищет слот с предметом, помеченным session-id.
     * @return индекс слота (40 = off hand) или -1
     */
    private int findSessionSlot(Player player, String sessionId) {
        org.bukkit.inventory.PlayerInventory inv = player.getInventory();

        int held = inv.getHeldItemSlot();
        if (isSessionItem(inv.getItem(held), sessionId)) {
            return held;
        }
        if (isSessionItem(inv.getItemInOffHand(), sessionId)) {
            return 40;
        }
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) {
            if (i == 40 || i == held) {
                continue;
            }
            if (isSessionItem(contents[i], sessionId)) {
                return i;
            }
        }
        return -1;
    }

    private boolean tagItem(ItemStack item, String sessionId) {
        if (!(item.getItemMeta() instanceof BlockStateMeta)) {
            return false;
        }
        BlockStateMeta meta = (BlockStateMeta) item.getItemMeta();
        if (!(meta.getBlockState() instanceof ShulkerBox)) {
            return false;
        }
        ShulkerBox box = (ShulkerBox) meta.getBlockState();
        box.getInventory().clear();
        meta.setBlockState(box);
        meta.getPersistentDataContainer().set(sessionKey, PersistentDataType.STRING, sessionId);
        item.setItemMeta(meta);
        return true;
    }

    private void clearSessionTag(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.getPersistentDataContainer().remove(sessionKey);
        item.setItemMeta(meta);
    }

    private void emptyBlockState(ItemStack item) {
        if (!(item.getItemMeta() instanceof BlockStateMeta)) {
            return;
        }
        BlockStateMeta meta = (BlockStateMeta) item.getItemMeta();
        if (!(meta.getBlockState() instanceof ShulkerBox)) {
            return;
        }
        ShulkerBox box = (ShulkerBox) meta.getBlockState();
        box.getInventory().clear();
        meta.setBlockState(box);
        meta.getPersistentDataContainer().remove(sessionKey);
        item.setItemMeta(meta);
    }

    private void applyContents(ItemStack item, ItemStack[] contents) {
        if (item == null || !(item.getItemMeta() instanceof BlockStateMeta)) {
            return;
        }
        BlockStateMeta meta = (BlockStateMeta) item.getItemMeta();
        if (!(meta.getBlockState() instanceof ShulkerBox)) {
            return;
        }
        ShulkerBox box = (ShulkerBox) meta.getBlockState();
        box.getInventory().clear();
        if (contents != null) {
            box.getInventory().setContents(contents);
        }
        meta.setBlockState(box);
        item.setItemMeta(meta);
    }

    private void giveItem(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        if (!leftover.isEmpty()) {
            Location loc = player.getLocation();
            for (ItemStack rest : leftover.values()) {
                player.getWorld().dropItemNaturally(loc, rest);
            }
        }
    }

    private String getGuiTitle(Player player) {
        String title = plugin.getMessage(player, "gui.title");
        if (title == null || title.isEmpty() || title.contains("Message not found")) {
            title = "Shulker Box";
        }
        return title;
    }

    private int countItems(ItemStack[] contents) {
        int count = 0;
        if (contents == null) return 0;
        for (ItemStack stack : contents) {
            if (stack != null && stack.getType() != Material.AIR) {
                count += stack.getAmount();
            }
        }
        return count;
    }

    // ------------------------------------------------------------------
    // Состояние / утилиты
    // ------------------------------------------------------------------

    public boolean hasActiveSession(Player player) {
        return player != null && activeSessions.containsKey(player.getUniqueId());
    }

    public ShulkerSession getSession(Player player) {
        return player == null ? null : activeSessions.get(player.getUniqueId());
    }

    public boolean isShulkerBox(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }

        Material type = item.getType();
        if (type != Material.SHULKER_BOX &&
            type != Material.WHITE_SHULKER_BOX &&
            type != Material.ORANGE_SHULKER_BOX &&
            type != Material.MAGENTA_SHULKER_BOX &&
            type != Material.LIGHT_BLUE_SHULKER_BOX &&
            type != Material.YELLOW_SHULKER_BOX &&
            type != Material.LIME_SHULKER_BOX &&
            type != Material.PINK_SHULKER_BOX &&
            type != Material.GRAY_SHULKER_BOX &&
            type != Material.LIGHT_GRAY_SHULKER_BOX &&
            type != Material.CYAN_SHULKER_BOX &&
            type != Material.PURPLE_SHULKER_BOX &&
            type != Material.BLUE_SHULKER_BOX &&
            type != Material.BROWN_SHULKER_BOX &&
            type != Material.GREEN_SHULKER_BOX &&
            type != Material.RED_SHULKER_BOX &&
            type != Material.BLACK_SHULKER_BOX) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof BlockStateMeta)) {
            return false;
        }

        BlockStateMeta blockMeta = (BlockStateMeta) meta;
        return blockMeta.getBlockState() instanceof ShulkerBox;
    }

    /**
     * Совместимость: сравнение "тот же самый бокс".
     * Точная идентичность теперь определяется session-id (PDC),
     * поэтому здесь сравниваются только тип и имя.
     */
    public boolean isSameShulkerBox(ItemStack current, ItemStack original) {
        if (current == null || original == null) {
            return false;
        }
        if (!isShulkerBox(current) || !isShulkerBox(original)) {
            return false;
        }
        if (current.getType() != original.getType()) {
            return false;
        }

        ItemMeta currentMeta = current.getItemMeta();
        ItemMeta originalMeta = original.getItemMeta();

        if (currentMeta.hasDisplayName() != originalMeta.hasDisplayName()) {
            return false;
        }

        return !currentMeta.hasDisplayName()
            || currentMeta.getDisplayName().equals(originalMeta.getDisplayName());
    }

    /**
     * Сессия шалкера: единственный владелец содержимого, пока GUI открыт.
     */
    public static class ShulkerSession {
        private final UUID playerId;
        private final ItemStack template;
        private final boolean isMainHand;
        private final long startTime;
        private final String sessionId;
        private ItemStack[] contents;

        public ShulkerSession(UUID playerId, ItemStack template, boolean isMainHand,
                              long startTime, String sessionId, ItemStack[] contents) {
            this.playerId = playerId;
            this.template = template;
            this.isMainHand = isMainHand;
            this.startTime = startTime;
            this.sessionId = sessionId;
            this.contents = contents != null ? contents : new ItemStack[27];
        }

        public UUID getPlayerId() { return playerId; }
        public ItemStack getTemplate() { return template; }
        public boolean isMainHand() { return isMainHand; }
        public long getStartTime() { return startTime; }
        public String getSessionId() { return sessionId; }

        public ItemStack[] getContents() { return contents; }

        public ItemStack[] getContentsClone() {
            ItemStack[] copy = new ItemStack[contents.length];
            for (int i = 0; i < contents.length; i++) {
                copy[i] = contents[i] == null ? null : contents[i].clone();
            }
            return copy;
        }

        public void setContents(ItemStack[] contents) {
            this.contents = contents != null ? contents : new ItemStack[27];
        }
    }

    // ------------------------------------------------------------------
    // Отметки о размещении
    // ------------------------------------------------------------------

    public void markPlacementAction(Player player) {
        placementActions.put(player.getUniqueId(), System.currentTimeMillis());
    }

    public boolean isRecentPlacementAction(Player player) {
        Long timestamp = placementActions.get(player.getUniqueId());
        if (timestamp == null) {
            return false;
        }
        boolean isRecent = (System.currentTimeMillis() - timestamp) < 500;
        if (!isRecent) {
            placementActions.remove(player.getUniqueId());
        }
        return isRecent;
    }

    public void cleanupPlacementActions() {
        long now = System.currentTimeMillis();
        placementActions.entrySet().removeIf(entry -> (now - entry.getValue()) >= 500L);
    }
}
