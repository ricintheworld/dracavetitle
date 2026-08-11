package com.dracave.title.gui;
import com.dracave.title.DraCaveTitlePlugin;
import com.dracave.title.api.TitleResult;
import com.dracave.title.config.Messages;
import com.dracave.title.model.PlayerData;
import com.dracave.title.model.TitleDefinition;
import com.dracave.title.render.TitleRenderer;
import com.dracave.title.util.ItemResolver;
import com.dracave.title.util.SchedulerUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
public final class TitleRepositoryGui implements ClickableTitleGui {
    private static final int PAGE_SIZE = 45;
    private final DraCaveTitlePlugin plugin;
    private final Player viewer;
    private final int page;
    private final Map<Integer, String> titleSlots = new HashMap<>();
    private final GuiRefreshCache refreshCache = new GuiRefreshCache();
    private Inventory inventory;
    private SchedulerUtil.Task refreshTask;
    public TitleRepositoryGui(DraCaveTitlePlugin plugin, Player viewer, int page) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.page = Math.max(0, page);
    }
    public void open() {
        PlayerData data = plugin.service().getCached(viewer.getUniqueId());
        if (data == null) {
            plugin.messages().send(viewer, plugin.service().isLoading(viewer.getUniqueId()) ? "loading" : "unavailable");
            return;
        }
        inventory = Bukkit.createInventory(this, 54, MiniMessage.miniMessage().deserialize(plugin.getConfig().getString("gui.title", "<aqua>称号仓库</aqua>")));
        render(data);
        plugin.guiSound().open(viewer);
        viewer.openInventory(inventory);
        refreshTask = SchedulerUtil.runTaskTimer(plugin, this::refreshTitleItems, 2L, 2L);
    }
    private void render(PlayerData data) {
        refreshCache.clear();
        List<TitleDefinition> titles = data.unlocked().stream()
                .map(plugin.registry()::get)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(TitleDefinition::order).thenComparing(TitleDefinition::id))
                .toList();
        int pages = Math.max(1, (titles.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int actualPage = Math.min(page, pages - 1);
        int from = actualPage * PAGE_SIZE;
        int to = Math.min(titles.size(), from + PAGE_SIZE);
        for (int index = from; index < to; index++) {
            TitleDefinition title = titles.get(index);
            boolean equipped = title.id().equals(data.equippedId());
            inventory.setItem(index - from, titleItem(title, equipped));
            titleSlots.put(index - from, title.id());
        }
        if (from >= to) {
            inventory.setItem(22, button(Material.BARRIER, plugin.messages().component("gui.empty")));
        }
        int previousSlot = safeControlSlot("gui.previous-slot", 45);
        int statusSlot = safeControlSlot("gui.status-slot", 48);
        int nextSlot = safeControlSlot("gui.next-slot", 53);
        if (actualPage > 0) {
            inventory.setItem(previousSlot, button(Material.ARROW, plugin.messages().component("gui.previous")));
        }
        inventory.setItem(statusSlot, button(Material.PAPER, plugin.messages().component("gui.status",
                Messages.text("page", Integer.toString(actualPage + 1)),
                Messages.text("pages", Integer.toString(pages)))));
        if (actualPage + 1 < pages) {
            inventory.setItem(nextSlot, button(Material.ARROW, plugin.messages().component("gui.next")));
        }
        ItemStack countItem = button(Material.BOOK, plugin.messages().component("gui.my-title-number",
                Messages.text("number", Integer.toString(data.unlocked().size()))));
        inventory.setItem(46, countItem);
        inventory.setItem(47, button(Material.CHEST, plugin.messages().component("gui.main-wear")));
        inventory.setItem(49, button(Material.ANVIL, plugin.messages().component("gui.main-custom")));
        inventory.setItem(50, button(Material.OAK_DOOR, plugin.messages().component("gui.back-main")));
        inventory.setItem(51, button(Material.LADDER, plugin.messages().component("gui.ranking")));
        inventory.setItem(52, button(Material.EMERALD, plugin.messages().component("gui.main-shop")));
    }
    private ItemStack titleItem(TitleDefinition title, boolean equipped) {
        ItemStack item = ItemResolver.resolve(title.icon());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(TitleRenderer.component(title, System.currentTimeMillis()).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        for (String line : title.description()) {
            lore.add(MiniMessage.miniMessage().deserialize(line).decoration(TextDecoration.ITALIC, false));
        }
        if (!lore.isEmpty()) {
            lore.add(Component.empty());
        }
        lore.add(plugin.messages().component(equipped ? "gui.equipped" : "gui.unlocked").decoration(TextDecoration.ITALIC, false));
        lore.add(plugin.messages().component(equipped ? "gui.click-clear" : "gui.click-equip").decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }
    private ItemStack button(Material material, Component name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }
    private void refreshTitleItems() {
        long now = System.currentTimeMillis();
        for (Map.Entry<Integer, String> entry : titleSlots.entrySet()) {
            TitleDefinition title = plugin.registry().get(entry.getValue());
            if (title == null || !title.animated()) {
                continue;
            }
            int slot = entry.getKey();
            String rendered = TitleRenderer.miniMessage(title, now);
            if (refreshCache.checkAndUpdate(slot, rendered)) {
                ItemStack item = inventory.getItem(slot);
                if (item != null) {
                    ItemMeta meta = item.getItemMeta();
                    meta.displayName(MiniMessage.miniMessage().deserialize(rendered).decoration(TextDecoration.ITALIC, false));
                    item.setItemMeta(meta);
                    inventory.setItem(slot, item);
                }
            }
        }
    }
    @Override
    public void onClose() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
    }
    @Override
    public void click(int rawSlot, ClickType clickType) {
        if (rawSlot < 0 || rawSlot >= inventory.getSize()) {
            return;
        }
        int previousSlot = safeControlSlot("gui.previous-slot", 45);
        int nextSlot = safeControlSlot("gui.next-slot", 53);
        if (rawSlot == 50) {
            plugin.guiSound().click(viewer);
            new MainMenuGui(plugin, viewer).open();
        } else if (rawSlot == 49) {
            plugin.guiSound().switchPage(viewer);
            new CustomTitleGui(plugin, viewer, 0).open();
        } else if (rawSlot == 52) {
            plugin.guiSound().switchPage(viewer);
            new TitleShopGui(plugin, viewer, 0).open();
        } else if (rawSlot == 47) {
            plugin.guiSound().click(viewer);
            new TitleRepositoryGui(plugin, viewer, 0).open();
        } else if (rawSlot == 51) {
            plugin.guiSound().click(viewer);
            showRanking();
        } else if (rawSlot == previousSlot && page > 0) {
            plugin.guiSound().switchPage(viewer);
            new TitleRepositoryGui(plugin, viewer, page - 1).open();
        } else if (rawSlot == nextSlot) {
            PlayerData current = plugin.service().getCached(viewer.getUniqueId());
            int count = current == null ? 0 : (int) current.unlocked().stream().map(plugin.registry()::get).filter(Objects::nonNull).count();
            int pages = Math.max(1, (count + PAGE_SIZE - 1) / PAGE_SIZE);
            if (page + 1 < pages) {
                plugin.guiSound().switchPage(viewer);
                new TitleRepositoryGui(plugin, viewer, page + 1).open();
            } else {
                plugin.guiSound().error(viewer);
            }
        } else {
            String titleId = titleSlots.get(rawSlot);
            if (titleId != null) {
                PlayerData data = plugin.service().getCached(viewer.getUniqueId());
                TitleDefinition title = plugin.registry().get(titleId);
                if (data != null && title != null && data.unlocked().contains(titleId)) {
                    boolean unequip = titleId.equals(data.equippedId());
                    CompletableFuture<TitleResult> operation = unequip
                            ? plugin.service().clear(viewer.getUniqueId())
                            : plugin.service().equip(viewer.getUniqueId(), titleId);
                    operation.thenAccept(result -> SchedulerUtil.runTask(plugin, () -> {
                        if (viewer.isOnline()) {
                            if (result == TitleResult.SUCCESS || result == TitleResult.COOLDOWN) {
                                plugin.guiSound().success(viewer);
                                new TitleRepositoryGui(plugin, viewer, page).open();
                            } else {
                                plugin.guiSound().error(viewer);
                                plugin.messages().send(viewer, "operation-failed");
                            }
                        }
                    }));
                } else {
                    plugin.guiSound().error(viewer);
                }
            } else {
                plugin.guiSound().click(viewer);
            }
        }
    }
    private int safeControlSlot(String path, int fallback) {
        int value = plugin.getConfig().getInt(path, fallback);
        return value >= 45 && value <= 53 && value != 46 && value != 47 && value != 49 && value != 50 && value != 51 && value != 52 ? value : fallback;
    }
    private void showRanking() {
        viewer.closeInventory();
        SchedulerUtil.runTaskAsynchronously(plugin, () -> {
            try {
                List<com.dracave.title.storage.TitleRankEntry> ranking = plugin.titleRepository().ranking(10);
                SchedulerUtil.runTask(plugin, () -> {
                    if (!viewer.isOnline()) {
                        return;
                    }
                    viewer.sendMessage("§e§m-------------§f[§e称号数量排行榜§f]§e§m-------------");
                    if (ranking.isEmpty()) {
                        viewer.sendMessage("§7暂无数据");
                        return;
                    }
                    int rank = 1;
                    for (com.dracave.title.storage.TitleRankEntry entry : ranking) {
                        org.bukkit.OfflinePlayer offline = Bukkit.getOfflinePlayer(entry.playerId());
                        String name = offline.getName() == null ? entry.playerId().toString().substring(0, 8) : offline.getName();
                        viewer.sendMessage("§e" + rank + ". §f" + name + " §7（" + entry.count() + " 个称号）");
                        rank++;
                    }
                });
            } catch (Exception ex) {
                plugin.getLogger().warning("查询称号排行榜失败: " + ex.getMessage());
                SchedulerUtil.runTask(plugin, () -> plugin.messages().send(viewer, "operation-failed"));
            }
        });
    }
    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
