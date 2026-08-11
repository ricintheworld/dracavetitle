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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
public final class ViewOpenGui implements ClickableTitleGui {
    private static final int PAGE_SIZE = 45;
    private final DraCaveTitlePlugin plugin;
    private final Player viewer;
    private final UUID targetId;
    private final String targetName;
    private final int page;
    private final Map<Integer, String> titleSlots = new HashMap<>();
    private final GuiRefreshCache refreshCache = new GuiRefreshCache();
    private Inventory inventory;
    private SchedulerUtil.Task refreshTask;
    public ViewOpenGui(DraCaveTitlePlugin plugin, Player viewer, UUID targetId, String targetName, int page) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.targetId = targetId;
        this.targetName = targetName;
        this.page = Math.max(0, page);
    }
    public void open() {
        PlayerData data = plugin.service().getCached(targetId);
        if (data == null) {
            plugin.service().load(targetId).thenAccept(loaded -> SchedulerUtil.runTask(plugin, () -> {
                if (loaded == null) {
                    plugin.messages().send(viewer, "unavailable");
                } else {
                    open();
                }
            }));
            return;
        }
        refreshCache.clear();
        List<TitleDefinition> titles = data.unlocked().stream()
                .map(plugin.registry()::get)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(TitleDefinition::order).thenComparing(TitleDefinition::id))
                .toList();
        int pages = Math.max(1, (titles.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int actualPage = Math.min(page, pages - 1);
        inventory = Bukkit.createInventory(this, 54, MiniMessage.miniMessage().deserialize("<red>查看玩家称号</red> <gray>" + targetName));
        for (int index = actualPage * PAGE_SIZE; index < Math.min(titles.size(), (actualPage + 1) * PAGE_SIZE); index++) {
            TitleDefinition title = titles.get(index);
            boolean equipped = title.id().equals(data.equippedId());
            inventory.setItem(index - actualPage * PAGE_SIZE, titleItem(title, equipped));
            titleSlots.put(index - actualPage * PAGE_SIZE, title.id());
        }
        if (titles.isEmpty()) {
            inventory.setItem(22, button(Material.BARRIER, plugin.messages().component("gui.empty")));
        }
        if (actualPage > 0) {
            inventory.setItem(45, button(Material.ARROW, plugin.messages().component("gui.previous")));
        }
        inventory.setItem(48, button(Material.PAPER, plugin.messages().component("gui.status",
                Messages.text("page", Integer.toString(actualPage + 1)),
                Messages.text("pages", Integer.toString(pages)))));
        if (actualPage + 1 < pages) {
            inventory.setItem(53, button(Material.ARROW, plugin.messages().component("gui.next")));
        }
        inventory.setItem(49, button(Material.OAK_DOOR, plugin.messages().component("gui.back-main")));
        plugin.guiSound().open(viewer);
        viewer.openInventory(inventory);
        refreshTask = SchedulerUtil.runTaskTimer(plugin, this::refreshTitleItems, 2L, 2L);
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
        lore.add(MiniMessage.miniMessage().deserialize(equipped ? "<yellow>点击为玩家卸下</yellow>" : "<yellow>点击为玩家穿戴</yellow>")
                .decoration(TextDecoration.ITALIC, false));
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
        if (rawSlot == 49) {
            plugin.guiSound().click(viewer);
            new AdminShopGui(plugin, viewer, 0).open();
        } else if (rawSlot == 45 && page > 0) {
            plugin.guiSound().switchPage(viewer);
            new ViewOpenGui(plugin, viewer, targetId, targetName, page - 1).open();
        } else if (rawSlot == 53) {
            PlayerData current = plugin.service().getCached(targetId);
            int count = current == null ? 0 : (int) current.unlocked().stream().map(plugin.registry()::get).filter(Objects::nonNull).count();
            int pages = Math.max(1, (count + PAGE_SIZE - 1) / PAGE_SIZE);
            if (page + 1 < pages) {
                plugin.guiSound().switchPage(viewer);
                new ViewOpenGui(plugin, viewer, targetId, targetName, page + 1).open();
            } else {
                plugin.guiSound().error(viewer);
            }
        } else {
            String titleId = titleSlots.get(rawSlot);
            if (titleId != null) {
                PlayerData data = plugin.service().getCached(targetId);
                if (data == null || !data.unlocked().contains(titleId)) {
                    plugin.guiSound().error(viewer);
                    return;
                }
                boolean unequip = titleId.equals(data.equippedId());
                CompletableFuture<TitleResult> operation = unequip
                        ? plugin.service().clear(targetId)
                        : plugin.service().equip(targetId, titleId);
                operation.thenAccept(result -> SchedulerUtil.runTask(plugin, () -> {
                    if (viewer.isOnline() && result == TitleResult.SUCCESS) {
                        plugin.guiSound().success(viewer);
                        new ViewOpenGui(plugin, viewer, targetId, targetName, page).open();
                    } else if (viewer.isOnline()) {
                        plugin.guiSound().error(viewer);
                    }
                }));
            }
        }
    }
    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
