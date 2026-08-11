package com.dracave.title.currency;

import com.dracave.title.model.CurrencyType;
import com.dracave.title.util.CustomItemProvider;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.util.UUID;

public final class ItemCurrencyProvider implements CurrencyProvider {
    private final String material;
    private final boolean custom;
    private final ItemStack template;
    private final Material vanillaType;

    public ItemCurrencyProvider(String material) {
        this.material = material;
        this.custom = CustomItemProvider.isCustomMaterial(material);
        if (custom) {
            this.template = CustomItemProvider.resolve(material);
            this.vanillaType = template != null ? template.getType() : null;
        } else {
            this.template = null;
            this.vanillaType = Material.matchMaterial(material);
        }
    }

    @Override
    public CurrencyType type() {
        return CurrencyType.ITEM;
    }

    @Override
    public boolean available() {
        if (material == null) return false;
        if (custom) return template != null;
        return vanillaType != null;
    }

    @Override
    public BigDecimal balance(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) return BigDecimal.ZERO;
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (isMatch(item)) count += item.getAmount();
        }
        return BigDecimal.valueOf(count);
    }

    @Override
    public boolean withdraw(UUID playerId, BigDecimal amount) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) return false;
        if (amount.scale() > 0 || amount.signum() <= 0) return false;
        int required = amount.intValueExact();
        if (balance(playerId).intValueExact() < required) return false;
        int remaining = required;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (item == null || !isMatch(item)) continue;
            int take = Math.min(item.getAmount(), remaining);
            item.setAmount(item.getAmount() - take);
            remaining -= take;
            if (item.getAmount() <= 0) player.getInventory().setItem(i, null);
        }
        return remaining == 0;
    }

    @Override
    public boolean refund(UUID playerId, BigDecimal amount) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) return false;
        if (amount.scale() > 0 || amount.signum() <= 0) return false;
        int count = amount.intValueExact();
        ItemStack tpl = custom ? template : (vanillaType != null ? new ItemStack(vanillaType) : null);
        if (tpl == null) return false;
        int maxStack = tpl.getMaxStackSize();
        while (count > 0) {
            int stack = Math.min(count, maxStack);
            ItemStack clone = tpl.clone();
            clone.setAmount(stack);
            for (ItemStack leftover : player.getInventory().addItem(clone).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
            count -= stack;
        }
        return true;
    }

    private boolean isMatch(ItemStack item) {
        if (item == null) return false;
        if (custom) {
            // Fast path: same type check
            if (vanillaType != null && item.getType() != vanillaType) return false;
            if (template != null && item.isSimilar(template)) return true;
            // Slow path: reflection
            return material.equals(CustomItemProvider.getCustomItemId(item));
        }
        return vanillaType != null && item.getType() == vanillaType;
    }
}
