package com.dracave.title.gui;
import com.dracave.title.DraCaveTitlePlugin;
import com.dracave.title.api.PurchaseResult;
import com.dracave.title.api.PurchaseStatus;
import com.dracave.title.config.Messages;
import com.dracave.title.model.TitleDefinition;
import com.dracave.title.model.TitlePurchaseOffer;
import com.dracave.title.render.TitleRenderer;
import com.dracave.title.util.ItemResolver;
import com.dracave.title.util.SchedulerUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
import java.util.List;
public final class PurchaseConfirmGui implements ClickableTitleGui {
    private final DraCaveTitlePlugin plugin;
    private final Player player;
    private final TitleDefinition title;
    private final int returnPage;
    private final ReturnTarget returnTarget;
    private final int size;
    private final int titleSlot;
    private final int confirmSlot;
    private final int cancelSlot;
    private final int mainMenuSlot;
    private Inventory inventory;
    private boolean processing;
    private String lastRendered;
    private SchedulerUtil.Task refreshTask;
    public PurchaseConfirmGui(DraCaveTitlePlugin plugin, Player player, TitleDefinition title,
                              int returnPage, ReturnTarget returnTarget) {
        this.plugin = plugin;
        this.player = player;
        this.title = title;
        this.returnPage = returnPage;
        this.returnTarget = returnTarget;
        int configuredSize = plugin.getConfig().getInt("gui.purchase-confirm.size", 27);
        this.size = configuredSize >= 18 && configuredSize <= 54 && configuredSize % 9 == 0 ? configuredSize : 27;
        int configuredTitle = plugin.getConfig().getInt("gui.purchase-confirm.title-slot", 13);
        int configuredConfirm = plugin.getConfig().getInt("gui.purchase-confirm.confirm-slot", 11);
        int configuredCancel = plugin.getConfig().getInt("gui.purchase-confirm.cancel-slot", 15);
        if (valid(configuredTitle) && valid(configuredConfirm) && valid(configuredCancel)
                && configuredTitle != configuredConfirm && configuredTitle != configuredCancel
                && configuredConfirm != configuredCancel) {
            this.titleSlot = configuredTitle;
            this.confirmSlot = configuredConfirm;
            this.cancelSlot = configuredCancel;
        } else {
            this.titleSlot = 13;
            this.confirmSlot = 11;
            this.cancelSlot = 15;
            plugin.getLogger().warning("购买确认 GUI 槽位无效或冲突，已使用默认布局");
        }
        this.mainMenuSlot = findMainMenuSlot();
    }
    private boolean valid(int slot) {
        return slot >= 0 && slot < size;
    }
    public void open() {
        if (title.purchaseOffer() == null) {
            return;
        }
        lastRendered = null;
        inventory = Bukkit.createInventory(this, size,
                MiniMessage.miniMessage().deserialize(plugin.getConfig().getString("gui.purchase-confirm.title", "<gold>确认购买称号</gold>")));
        ItemStack pane = pane(Material.GRAY_STAINED_GLASS_PANE);
        for (int slot = 0; slot < size; slot++) {
            if (slot != titleSlot && slot != confirmSlot && slot != cancelSlot && slot != mainMenuSlot) {
                inventory.setItem(slot, pane);
            }
        }
        inventory.setItem(titleSlot, titleItem());
        inventory.setItem(confirmSlot, button(material("confirm-material", Material.LIME_CONCRETE), plugin.messages().component("gui.confirm")));
        inventory.setItem(cancelSlot, button(material("cancel-material", Material.RED_CONCRETE), plugin.messages().component("gui.cancel")));
        inventory.setItem(mainMenuSlot, button(Material.OAK_DOOR, plugin.messages().component("gui.back-main")));
        plugin.guiSound().open(player);
        player.openInventory(inventory);
        if (title.animated()) {
            refreshTask = SchedulerUtil.runTaskTimer(plugin, this::refreshTitleItem, 2L, 2L);
        }
    }
    private int findMainMenuSlot() {
        int preferred = size - 5;
        if (preferred != titleSlot && preferred != confirmSlot && preferred != cancelSlot) {
            return preferred;
        }
        for (int slot = size - 1; slot >= 0; slot--) {
            if (slot != titleSlot && slot != confirmSlot && slot != cancelSlot) {
                return slot;
            }
        }
        return 0;
    }
    private ItemStack titleItem() {
        ItemStack item = ItemResolver.resolve(title.icon());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(TitleRenderer.component(title, System.currentTimeMillis()).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        title.description().forEach(line -> lore.add(MiniMessage.miniMessage().deserialize(line).decoration(TextDecoration.ITALIC, false)));
        TitlePurchaseOffer offer = title.purchaseOffer();
        String currency = currencyDisplay();
        lore.add(Component.empty());
        lore.add(plugin.messages().component("gui.price",
                Messages.text("price", offer.price().toPlainString()),
                Messages.parsed("currency", currency)).decoration(TextDecoration.ITALIC, false));
        BigDecimal balance = plugin.purchaseService().balance(player.getUniqueId(), offer);
        if (balance != null) {
            lore.add(plugin.messages().component("gui.balance",
                    Messages.text("balance", balance.stripTrailingZeros().toPlainString()),
                    Messages.parsed("currency", currency)).decoration(TextDecoration.ITALIC, false));
        }
        if (plugin.getConfig().getBoolean("purchase.auto-equip", true)) {
            lore.add(plugin.messages().component("gui.auto-equip").decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }
    private ItemStack pane(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.empty());
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
    private Material material(String key, Material fallback) {
        Material value = Material.matchMaterial(plugin.getConfig().getString("gui.purchase-confirm." + key, fallback.name()));
        return value != null && !value.isAir() ? value : fallback;
    }
    private void refreshTitleItem() {
        String rendered = TitleRenderer.miniMessage(title, System.currentTimeMillis());
        if (rendered.equals(lastRendered)) {
            return;
        }
        lastRendered = rendered;
        ItemStack item = inventory.getItem(titleSlot);
        if (item != null) {
            ItemMeta meta = item.getItemMeta();
            meta.displayName(MiniMessage.miniMessage().deserialize(rendered).decoration(TextDecoration.ITALIC, false));
            item.setItemMeta(meta);
            inventory.setItem(titleSlot, item);
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
        if (processing) {
            plugin.guiSound().error(player);
            plugin.messages().send(player, "purchase.in-progress");
        } else if (rawSlot == mainMenuSlot) {
            plugin.guiSound().click(player);
            new MainMenuGui(plugin, player).open();
        } else if (rawSlot == cancelSlot) {
            plugin.guiSound().click(player);
            returnToOrigin();
        } else if (rawSlot == confirmSlot) {
            plugin.guiSound().click(player);
            TitleDefinition current = plugin.registry().get(title.id());
            if (current != null && current.purchaseOffer() != null) {
                if (current.revision() == title.revision() && current.purchaseOffer().equals(title.purchaseOffer())) {
                    processing = true;
                    plugin.purchaseService().purchase(player.getUniqueId(), title.id())
                            .whenComplete((result, error) -> SchedulerUtil.runTask(plugin, () -> {
                                if (!player.isOnline()) {
                                    return;
                                }
                                processing = false;
                                if (error == null && result != null) {
                                    if (result.status() == PurchaseStatus.SUCCESS) {
                                        plugin.guiSound().success(player);
                                    } else {
                                        plugin.guiSound().error(player);
                                    }
                                    sendResult(result);
                                } else {
                                    plugin.guiSound().error(player);
                                    plugin.messages().send(player, "operation-failed");
                                }
                                returnToOrigin();
                            }));
                } else {
                    plugin.guiSound().error(player);
                    player.sendMessage(Component.text("称号价格已更新，请重新确认。", NamedTextColor.YELLOW));
                    new PurchaseConfirmGui(plugin, player, current, returnPage, returnTarget).open();
                }
            } else {
                plugin.guiSound().error(player);
                plugin.messages().send(player, "purchase.not-purchasable");
                returnToOrigin();
            }
        }
    }
    private void sendResult(PurchaseResult result) {
        String key = switch (result.status()) {
            case SUCCESS -> "purchase.success";
            case ALREADY_UNLOCKED -> "purchase.already-owned";
            case NOT_PURCHASABLE -> "purchase.not-purchasable";
            case PERMISSION_DENIED -> "purchase.permission-denied";
            case CURRENCY_UNAVAILABLE -> "purchase.currency-unavailable";
            case INSUFFICIENT_FUNDS -> "purchase.insufficient-funds";
            case PURCHASE_IN_PROGRESS -> "purchase.in-progress";
            case CANCELLED -> "purchase.cancelled";
            case PAYMENT_FAILED -> "purchase.payment-failed";
            case REFUNDED -> "purchase.refunded";
            case REFUND_PENDING -> "purchase.refund-pending";
            default -> "operation-failed";
        };
        plugin.messages().send(player, key,
                Messages.parsed("title", TitleRenderer.miniMessage(title, System.currentTimeMillis())),
                Messages.text("price", result.price().toPlainString()),
                Messages.parsed("currency", currencyDisplay()),
                Messages.text("operation", result.operationId() == null ? "-" : result.operationId().toString()));
    }
    private String currencyDisplay() {
        TitlePurchaseOffer offer = title.purchaseOffer();
        if (offer.currency() == com.dracave.title.model.CurrencyType.ITEM) {
            return offer.itemMaterial();
        }
        return plugin.getConfig().getString("purchase.currencies." + offer.currency().id() + ".display",
                offer.currency().id());
    }
    private void returnToOrigin() {
        if (returnTarget == ReturnTarget.SHOP) {
            new TitleShopGui(plugin, player, returnPage).open();
        } else {
            new TitleRepositoryGui(plugin, player, returnPage).open();
        }
    }
    @Override
    public Inventory getInventory() {
        return inventory;
    }
    public enum ReturnTarget {
        SHOP,
        WEAR
    }
}
