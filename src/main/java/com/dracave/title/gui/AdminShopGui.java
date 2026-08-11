package com.dracave.title.gui;
import com.dracave.title.DraCaveTitlePlugin;
import com.dracave.title.config.Messages;
import com.dracave.title.model.TitleDefinition;
import com.dracave.title.model.TitlePurchaseOffer;
import com.dracave.title.panel.TitleAdminPanel;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
public final class AdminShopGui implements ClickableTitleGui {
    private static final int PAGE_SIZE = 45;
    private final DraCaveTitlePlugin plugin;
    private final Player player;
    private final int page;
    private final Map<Integer, String> slots = new HashMap<>();
    private final GuiRefreshCache refreshCache = new GuiRefreshCache();
    private Inventory inventory;
    private SchedulerUtil.Task refreshTask;
    public AdminShopGui(DraCaveTitlePlugin plugin, Player player, int page) {
        this.plugin = plugin;
        this.player = player;
        this.page = Math.max(0, page);
    }
    public void open() {
        refreshCache.clear();
        List<TitleDefinition> titles = plugin.registry().configured();
        int pages = Math.max(1, (titles.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int actualPage = Math.min(page, pages - 1);
        inventory = Bukkit.createInventory(this, 54, MiniMessage.miniMessage().deserialize(plugin.messages().rawString("gui.admin-title")));
        for (int index = actualPage * PAGE_SIZE; index < Math.min(titles.size(), (actualPage + 1) * PAGE_SIZE); index++) {
            int slot = index - actualPage * PAGE_SIZE;
            slots.put(slot, titles.get(index).id());
            inventory.setItem(slot, titleItem(titles.get(index)));
        }
        if (titles.isEmpty()) {
            inventory.setItem(22, button(Material.BARRIER, plugin.messages().component("gui.no-title-data")));
        }
        if (actualPage > 0) {
            inventory.setItem(45, button(Material.ARROW, plugin.messages().component("gui.previous")));
        }
        if (actualPage + 1 < pages) {
            inventory.setItem(53, button(Material.ARROW, plugin.messages().component("gui.next")));
        }
        if (player.hasPermission("dracave.title.admin.upload")) {
            inventory.setItem(48, button(Material.WRITABLE_BOOK, plugin.messages().component("gui.admin-upload")));
            inventory.setItem(50, button(Material.BOOK, plugin.messages().component("gui.admin-check")));
        }
        inventory.setItem(49, button(Material.OAK_DOOR, plugin.messages().component("gui.back-main")));
        plugin.guiSound().open(player);
        player.openInventory(inventory);
        refreshTask = SchedulerUtil.runTaskTimer(plugin, this::refreshTitleItems, 2L, 2L);
    }
    private ItemStack titleItem(TitleDefinition title) {
        ItemStack item = ItemResolver.resolve(title.icon());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(TitleRenderer.component(title, System.currentTimeMillis()).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        TitlePurchaseOffer offer = title.purchaseOffer();
        if (offer != null) {
            lore.add(plugin.messages().component("gui.price",
                    Messages.text("price", offer.price().toPlainString()),
                    Messages.parsed("currency", offer.currency().id())).decoration(TextDecoration.ITALIC, false));
        }
        if (title.shopHidden()) {
            lore.add(MiniMessage.miniMessage().deserialize("<dark_gray>商店隐藏</dark_gray>").decoration(TextDecoration.ITALIC, false));
        }
        lore.add(plugin.messages().component("gui.admin-edit").decoration(TextDecoration.ITALIC, false));
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
        for (Map.Entry<Integer, String> entry : slots.entrySet()) {
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
            plugin.guiSound().click(player);
            new MainMenuGui(plugin, player).open();
        } else if (rawSlot == 48) {
            plugin.guiSound().click(player);
            plugin.definitionService().upload().thenAccept(result -> SchedulerUtil.runTask(plugin, () -> {
                if (result.valid()) {
                    plugin.guiSound().success(player);
                    player.sendMessage("§a上传完成：新增 " + result.inserted() + " 个，更新 " + result.updated() + " 个。");
                } else {
                    plugin.guiSound().error(player);
                    player.sendMessage("§c上传失败：" + String.join("；", result.errors()));
                }
                new AdminShopGui(plugin, player, page).open();
            }));
        } else if (rawSlot == 50) {
            plugin.guiSound().click(player);
            plugin.definitionService().checkUpload().thenAccept(result -> SchedulerUtil.runTask(plugin, () -> {
                if (result.valid()) {
                    plugin.guiSound().success(player);
                    player.sendMessage("§a校验通过，共 " + result.count() + " 个称号。");
                } else {
                    plugin.guiSound().error(player);
                    player.sendMessage("§c校验失败：" + String.join("；", result.errors()));
                }
            }));
        } else if (rawSlot == 45 && page > 0) {
            plugin.guiSound().switchPage(player);
            new AdminShopGui(plugin, player, page - 1).open();
        } else if (rawSlot == 53) {
            List<TitleDefinition> titles = plugin.registry().configured();
            int pages = Math.max(1, (titles.size() + PAGE_SIZE - 1) / PAGE_SIZE);
            if (page + 1 < pages) {
                plugin.guiSound().switchPage(player);
                new AdminShopGui(plugin, player, page + 1).open();
            } else {
                plugin.guiSound().error(player);
            }
        } else {
            String titleId = slots.get(rawSlot);
            if (titleId != null) {
                plugin.guiSound().click(player);
                plugin.adminPanel().openEditor(player, titleId, TitleAdminPanel.EditorReturn.ADMIN_SHOP, page);
            }
        }
    }
    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
