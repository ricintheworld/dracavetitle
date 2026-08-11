package com.dracave.title.gui;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
public interface ClickableTitleGui extends InventoryHolder {
    void click(int rawSlot, ClickType clickType);
    @Override
    Inventory getInventory();
    default void onClose() {}
}
