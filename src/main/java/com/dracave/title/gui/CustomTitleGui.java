package com.dracave.title.gui;
import com.dracave.title.DraCaveTitlePlugin;
import com.dracave.title.config.Messages;
import com.dracave.title.model.CustomTitle;
import com.dracave.title.model.TitleDefinition;
import com.dracave.title.render.TitleRenderer;
import com.dracave.title.service.CustomTitleService;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
public final class CustomTitleGui implements ClickableTitleGui {
    private static final int PAGE_SIZE = 45;
    private static final int SLOT_QUOTA = 45;
    private static final int SLOT_REPOSITORY = 47;
    private static final int SLOT_SHOP = 49;
    private static final int SLOT_MAIN_MENU = 50;
    private static final int SLOT_CREATE = 51;
    private final DraCaveTitlePlugin plugin;
    private final Player player;
    private final int page;
    private Inventory inventory;
    private SchedulerUtil.Task refreshTask;
    private final Map<Integer, TitleDefinition> titleSlots = new HashMap<>();
    private final GuiRefreshCache refreshCache = new GuiRefreshCache();
    public CustomTitleGui(DraCaveTitlePlugin plugin, Player player, int page) {
        this.plugin = plugin;
        this.player = player;
        this.page = Math.max(0, page);
    }
    public void open() {
        refreshCache.clear();
        inventory = Bukkit.createInventory(this, 54, MiniMessage.miniMessage().deserialize("<light_purple>自定义称号</light_purple>"));
        List<CustomTitle> owned = plugin.customTitles().ownedBy(player.getUniqueId());
        int limit = plugin.customTitles().limit(player);
        String limitDisplay = limit == Integer.MAX_VALUE ? "∞" : Integer.toString(limit);
        inventory.setItem(SLOT_QUOTA, button(Material.PAPER, plugin.messages().component("custom.quota-info",
                Messages.text("used", Integer.toString(owned.size())),
                Messages.text("limit", limitDisplay))));
        int index = page * PAGE_SIZE;
        for (int slot = 0; slot < PAGE_SIZE && index < owned.size(); slot++, index++) {
            CustomTitle custom = owned.get(index);
            TitleDefinition definition = plugin.registry().get(custom.id());
            if (definition == null) {
                continue;
            }
            ItemStack item = ItemResolver.resolve(custom.icon());
            ItemMeta meta = item.getItemMeta();
            meta.displayName(TitleRenderer.component(definition, System.currentTimeMillis()).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    plugin.messages().component("gui.custom-delete").decoration(TextDecoration.ITALIC, false)
            ));
            item.setItemMeta(meta);
            inventory.setItem(slot, item);
            titleSlots.put(slot, definition);
        }
        if (owned.isEmpty()) {
            inventory.setItem(22, button(Material.BARRIER, plugin.messages().component("gui.empty")));
        }
        inventory.setItem(SLOT_REPOSITORY, button(Material.CHEST, plugin.messages().component("gui.main-wear")));
        inventory.setItem(SLOT_SHOP, button(Material.EMERALD, plugin.messages().component("gui.main-shop")));
        inventory.setItem(SLOT_MAIN_MENU, button(Material.OAK_DOOR, plugin.messages().component("gui.back-main")));
        inventory.setItem(SLOT_CREATE, button(Material.WRITABLE_BOOK, plugin.messages().component("gui.custom-create")));
        plugin.guiSound().open(player);
        player.openInventory(inventory);
        refreshTask = SchedulerUtil.runTaskTimer(plugin, this::refreshTitleItems, 2L, 2L);
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
        for (Map.Entry<Integer, TitleDefinition> entry : titleSlots.entrySet()) {
            if (!entry.getValue().animated()) {
                continue;
            }
            int slot = entry.getKey();
            String rendered = TitleRenderer.miniMessage(entry.getValue(), now);
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
        if (rawSlot == SLOT_MAIN_MENU) {
            plugin.guiSound().click(player);
            new MainMenuGui(plugin, player).open();
        } else if (rawSlot == SLOT_REPOSITORY) {
            plugin.guiSound().switchPage(player);
            new TitleRepositoryGui(plugin, player, 0).open();
        } else if (rawSlot == SLOT_SHOP) {
            plugin.guiSound().switchPage(player);
            new TitleShopGui(plugin, player, 0).open();
        } else if (rawSlot == SLOT_CREATE) {
            plugin.guiSound().click(player);
            new CustomTitleStyleGui(plugin, player).open();
        } else if (rawSlot >= 0 && rawSlot < PAGE_SIZE) {
            plugin.guiSound().click(player);
            List<CustomTitle> owned = plugin.customTitles().ownedBy(player.getUniqueId());
            int index = page * PAGE_SIZE + rawSlot;
            if (index < owned.size()) {
                CustomTitle custom = owned.get(index);
                confirmDelete(custom);
            }
        }
    }
    private void confirmDelete(CustomTitle custom) {
        player.closeInventory();
        plugin.chatPrompts().prompt(player,
                "§c确认删除自定义称号 " + custom.text() + " ？输入 §ey§r 确认，输入其他取消：",
                (p, input) -> {
                    if (!input.equalsIgnoreCase("y") && !input.equalsIgnoreCase("yes") && !input.equalsIgnoreCase("确认")) {
                        new CustomTitleGui(plugin, p, 0).open();
                        return;
                    }
                    plugin.customTitles().delete(p, custom.id()).thenAccept(result ->
                            SchedulerUtil.runTask(plugin, () -> {
                                if (!p.isOnline()) {
                                    return;
                                }
                                boolean ok = result == CustomTitleService.Result.SUCCESS;
                                if (ok) {
                                    plugin.guiSound().delete(p);
                                } else {
                                    plugin.guiSound().error(p);
                                }
                                plugin.messages().send(p, ok ? "custom.deleted"
                                        : "custom.result-" + result.name().toLowerCase().replace('_', '-'));
                                new CustomTitleGui(plugin, p, 0).open();
                            }));
                }, true);
    }
    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
