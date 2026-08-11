package com.dracave.title.gui;
import com.dracave.title.DraCaveTitlePlugin;
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
public final class MainMenuGui implements ClickableTitleGui {
    private final DraCaveTitlePlugin plugin;
    private final Player player;
    private Inventory inventory;
    public MainMenuGui(DraCaveTitlePlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }
    public void open() {
        inventory = Bukkit.createInventory(this, 27, MiniMessage.miniMessage().deserialize(plugin.messages().rawString("gui.main-menu-title")));
        fillBorder();
        inventory.setItem(11, button(Material.CHEST, "gui.main-wear"));
        inventory.setItem(13, button(Material.EMERALD, "gui.main-shop"));
        inventory.setItem(15, button(Material.ANVIL, "gui.main-custom"));
        inventory.setItem(22, button(Material.GOLD_INGOT, "gui.main-reward"));
        if (player.hasPermission("dracave.title.admin.panel")) {
            inventory.setItem(26, button(Material.COMMAND_BLOCK, "gui.main-admin"));
        }
        plugin.guiSound().open(player);
        player.openInventory(inventory);
    }
    private void fillBorder() {
        ItemStack pane = pane(Material.GRAY_STAINED_GLASS_PANE);
        boolean hasAdmin = player.hasPermission("dracave.title.admin.panel");
        for (int slot = 0; slot < 27; slot++) {
            if (slot == 11 || slot == 13 || slot == 15 || slot == 22 || (slot == 26 && hasAdmin)) {
                continue;
            }
            inventory.setItem(slot, pane);
        }
    }
    private ItemStack pane(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.empty());
        item.setItemMeta(meta);
        return item;
    }
    private ItemStack button(Material material, String messageKey) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(plugin.messages().component(messageKey).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }
    @Override
    public void click(int rawSlot, ClickType clickType) {
        switch (rawSlot) {
            case 11 -> {
                plugin.guiSound().click(player);
                new TitleRepositoryGui(plugin, player, 0).open();
            }
            case 13 -> {
                plugin.guiSound().click(player);
                new TitleShopGui(plugin, player, 0).open();
            }
            case 15 -> {
                plugin.guiSound().click(player);
                new CustomTitleGui(plugin, player, 0).open();
            }
            case 22 -> {
                plugin.guiSound().click(player);
                new RewardGui(plugin, player).open();
            }
            case 26 -> {
                if (player.hasPermission("dracave.title.admin.panel")) {
                    plugin.guiSound().click(player);
                    new AdminShopGui(plugin, player, 0).open();
                }
            }
            default -> {
            }
        }
    }
    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
