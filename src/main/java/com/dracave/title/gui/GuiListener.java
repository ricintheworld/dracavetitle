package com.dracave.title.gui;
import com.dracave.title.DraCaveTitlePlugin;
import com.dracave.title.util.SchedulerUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
public final class GuiListener implements Listener {
    private final DraCaveTitlePlugin plugin;
    public GuiListener(DraCaveTitlePlugin plugin) {
        this.plugin = plugin;
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        Inventory inventory = event.getInventory();
        if (!(inventory.getHolder() instanceof ClickableTitleGui gui)) {
            return;
        }
        event.setCancelled(true);
        if (event.getWhoClicked() instanceof Player player && event.getClickedInventory() == inventory) {
            int rawSlot = event.getRawSlot();
            ClickType clickType = event.getClick();
            SchedulerUtil.runTask(plugin, () -> gui.click(rawSlot, clickType));
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        Inventory inventory = event.getInventory();
        if (inventory.getHolder() instanceof ClickableTitleGui) {
            event.setCancelled(true);
        }
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof ClickableTitleGui gui) {
            gui.onClose();
        }
    }
}
