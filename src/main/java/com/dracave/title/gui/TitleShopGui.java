package com.dracave.title.gui;
import com.dracave.title.DraCaveTitlePlugin;
import com.dracave.title.config.Messages;
import com.dracave.title.model.CurrencyType;
import com.dracave.title.model.PlayerData;
import com.dracave.title.model.TitleDefinition;
import com.dracave.title.model.TitlePurchaseOffer;
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
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
public final class TitleShopGui implements ClickableTitleGui {
    private static final class MiniHolder {
        static final MiniMessage MINI = MiniMessage.miniMessage();
    }
    private static final int PAGE_SIZE = 45;
    private static final int SLOT_PREVIOUS = 45;
    private static final int SLOT_FILTER_ALL = 46;
    private static final int SLOT_FILTER_VAULT = 47;
    private static final int SLOT_FILTER_POINTS = 48;
    private static final int SLOT_FILTER_COIN = 49;
    private static final int SLOT_FILTER_ITEM = 50;
    private static final int SLOT_MAIN_MENU = 51;
    private static final int SLOT_BALANCE = 52;
    private static final int SLOT_NEXT = 53;
    private final DraCaveTitlePlugin plugin;
    private final Player player;
    private final int page;
    private final CurrencyType filter;
    private final Map<Integer, TitleDefinition> slots = new HashMap<>();
    private final GuiRefreshCache refreshCache = new GuiRefreshCache();
    private Inventory inventory;
    private SchedulerUtil.Task refreshTask;
    public TitleShopGui(DraCaveTitlePlugin plugin, Player player, int page) {
        this(plugin, player, page, null);
    }
    public TitleShopGui(DraCaveTitlePlugin plugin, Player player, int page, CurrencyType filter) {
        this.plugin = plugin;
        this.player = player;
        this.page = Math.max(0, page);
        this.filter = filter;
    }
    public void open() {
        refreshCache.clear();
        PlayerData data = plugin.service().getCached(player.getUniqueId());
        if (data == null) {
            plugin.messages().send(player, plugin.service().isLoading(player.getUniqueId()) ? "loading" : "unavailable");
            return;
        }
        List<TitleDefinition> titles = available(data);
        int pages = Math.max(1, (titles.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int actualPage = Math.min(page, pages - 1);
        inventory = Bukkit.createInventory(this, 54, MiniHolder.MINI.deserialize("<green>称号商店</green>"));
        for (int index = actualPage * PAGE_SIZE; index < Math.min(titles.size(), (actualPage + 1) * PAGE_SIZE); index++) {
            int slot = index - actualPage * PAGE_SIZE;
            slots.put(slot, titles.get(index));
            inventory.setItem(slot, item(titles.get(index)));
        }
        if (titles.isEmpty()) {
            inventory.setItem(22, button(Material.BARRIER, plugin.messages().component("gui.empty")));
        }
        renderFooter(data, actualPage, pages);
        plugin.guiSound().open(player);
        player.openInventory(inventory);
        refreshTask = SchedulerUtil.runTaskTimer(plugin, this::refreshTitleItems, 2L, 2L);
    }
    private void renderFooter(PlayerData data, int actualPage, int pages) {
        if (actualPage > 0) {
            inventory.setItem(SLOT_PREVIOUS, button(Material.ARROW, plugin.messages().component("gui.previous")));
        }
        if (actualPage + 1 < pages) {
            inventory.setItem(SLOT_NEXT, button(Material.ARROW, plugin.messages().component("gui.next")));
        }
        inventory.setItem(SLOT_FILTER_ALL, filterButton(Material.CHEST, "<aqua>全部</aqua>", null));
        inventory.setItem(SLOT_FILTER_VAULT, filterButton(Material.GOLD_INGOT, "<green>金币</green>", CurrencyType.VAULT));
        inventory.setItem(SLOT_FILTER_POINTS, filterButton(Material.LIGHT_BLUE_DYE, "<aqua>点券</aqua>", CurrencyType.PLAYER_POINTS));
        inventory.setItem(SLOT_FILTER_COIN, filterButton(Material.EMERALD, "<gold>称号币</gold>", CurrencyType.COIN));
        inventory.setItem(SLOT_FILTER_ITEM, filterButton(Material.DIAMOND, "<yellow>物品</yellow>", CurrencyType.ITEM));
        List<Component> lore = new ArrayList<>();
        lore.add(balanceLine(CurrencyType.VAULT, data));
        lore.add(balanceLine(CurrencyType.PLAYER_POINTS, data));
        lore.add(balanceLine(CurrencyType.COIN, data));
        ItemStack balance = button(Material.NETHER_STAR, MiniHolder.MINI.deserialize("<gold>我的资产</gold>"));
        ItemMeta meta = balance.getItemMeta();
        meta.lore(lore);
        balance.setItemMeta(meta);
        inventory.setItem(SLOT_BALANCE, balance);
        inventory.setItem(SLOT_MAIN_MENU, button(Material.OAK_DOOR, plugin.messages().component("gui.back-main")));
    }
    private Component balanceLine(CurrencyType currency, PlayerData data) {
        BigDecimal balance = plugin.purchaseService().balance(player.getUniqueId(),
                new TitlePurchaseOffer(currency, BigDecimal.ONE));
        String display = plugin.getConfig().getString("purchase.currencies." + currency.id() + ".display", currency.id());
        return MiniHolder.MINI.deserialize("<gray>" + stripTags(display) + "：<white>" + (balance == null ? "不可用" : balance.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()));
    }
    private ItemStack filterButton(Material material, String label, CurrencyType currencyType) {
        boolean selected = filter == currencyType;
        ItemStack item = button(material, MiniHolder.MINI.deserialize((selected ? "<green><bold>" : "<yellow>") + label
                + (selected ? "</bold> <gray>[筛选中]" : "")));
        return item;
    }
    private String stripTags(String miniMessage) {
        return miniMessage.replaceAll("<[^>]+>", "");
    }
    private List<TitleDefinition> available(PlayerData data) {
        return plugin.registry().all().stream()
                .filter(title -> !title.shopHidden() && !data.unlocked().contains(title.id()))
                .filter(title -> filter == null || (title.purchaseOffer() != null && title.purchaseOffer().currency() == filter))
                .toList();
    }
    private ItemStack item(TitleDefinition title) {
        ItemStack item = ItemResolver.resolve(title.icon());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(TitleRenderer.component(title, System.currentTimeMillis()).decoration(TextDecoration.ITALIC, false));
        TitlePurchaseOffer offer = title.purchaseOffer();
        String currency = currencyDisplay(offer);
        List<Component> lore = new ArrayList<>();
        title.description().forEach(line -> lore.add(MiniHolder.MINI.deserialize(line).decoration(TextDecoration.ITALIC, false)));
        if (!lore.isEmpty()) {
            lore.add(Component.empty());
        }
        if (offer.currency() == CurrencyType.ITEM) {
            Material itemMaterial = Material.matchMaterial(offer.itemMaterial());
            String itemName = itemMaterial == null ? offer.itemMaterial() : itemMaterial.name();
            lore.add(plugin.messages().component("gui.price",
                    Messages.text("price", offer.price().toPlainString()),
                    Messages.parsed("currency", itemName)).decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(plugin.messages().component("gui.price",
                    Messages.text("price", offer.price().toPlainString()),
                    Messages.parsed("currency", currency)).decoration(TextDecoration.ITALIC, false));
        }
        if (!title.permission().isEmpty() && !player.hasPermission(title.permission())) {
            lore.add(plugin.messages().component("gui.permission-required",
                    Messages.text("permission", title.permission())).decoration(TextDecoration.ITALIC, false));
        } else if (!plugin.purchaseService().currencyAvailable(offer)) {
            lore.add(plugin.messages().component("gui.currency-unavailable").decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(plugin.messages().component("gui.click-buy").decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }
    private String currencyDisplay(TitlePurchaseOffer offer) {
        return plugin.getConfig().getString("purchase.currencies." + offer.currency().id() + ".display", offer.currency().id());
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
        for (Map.Entry<Integer, TitleDefinition> entry : slots.entrySet()) {
            if (!entry.getValue().animated()) {
                continue;
            }
            int slot = entry.getKey();
            String rendered = TitleRenderer.miniMessage(entry.getValue(), now);
            if (refreshCache.checkAndUpdate(slot, rendered)) {
                ItemStack item = inventory.getItem(slot);
                if (item != null) {
                    ItemMeta meta = item.getItemMeta();
                    meta.displayName(MiniHolder.MINI.deserialize(rendered).decoration(TextDecoration.ITALIC, false));
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
    public void click(int slot, ClickType clickType) {
        if (slot == SLOT_MAIN_MENU) {
            plugin.guiSound().click(player);
            new MainMenuGui(plugin, player).open();
        } else if (slot == SLOT_FILTER_ALL) {
            plugin.guiSound().switchPage(player);
            new TitleShopGui(plugin, player, 0, null).open();
        } else if (slot == SLOT_FILTER_VAULT) {
            plugin.guiSound().switchPage(player);
            new TitleShopGui(plugin, player, 0, CurrencyType.VAULT).open();
        } else if (slot == SLOT_FILTER_POINTS) {
            plugin.guiSound().switchPage(player);
            new TitleShopGui(plugin, player, 0, CurrencyType.PLAYER_POINTS).open();
        } else if (slot == SLOT_FILTER_COIN) {
            plugin.guiSound().switchPage(player);
            new TitleShopGui(plugin, player, 0, CurrencyType.COIN).open();
        } else if (slot == SLOT_FILTER_ITEM) {
            plugin.guiSound().switchPage(player);
            new TitleShopGui(plugin, player, 0, CurrencyType.ITEM).open();
        } else {
            PlayerData data = plugin.service().getCached(player.getUniqueId());
            if (data == null) {
                return;
            }
            int pages = Math.max(1, (available(data).size() + PAGE_SIZE - 1) / PAGE_SIZE);
            int actualPage = Math.min(page, pages - 1);
            if (slot == SLOT_PREVIOUS && actualPage > 0) {
                plugin.guiSound().switchPage(player);
                new TitleShopGui(plugin, player, actualPage - 1, filter).open();
            } else if (slot == SLOT_NEXT && actualPage + 1 < pages) {
                plugin.guiSound().switchPage(player);
                new TitleShopGui(plugin, player, actualPage + 1, filter).open();
            } else {
                TitleDefinition title = slots.get(slot);
                if (title != null
                        && (title.permission().isEmpty() || player.hasPermission(title.permission()))
                        && plugin.purchaseService().currencyAvailable(title.purchaseOffer())) {
                    plugin.guiSound().click(player);
                    new PurchaseConfirmGui(plugin, player, title, actualPage, PurchaseConfirmGui.ReturnTarget.SHOP).open();
                } else if (title != null) {
                    plugin.guiSound().error(player);
                }
            }
        }
    }
    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
