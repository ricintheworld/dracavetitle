package com.dracave.title.gui;
import com.dracave.title.DraCaveTitlePlugin;
import com.dracave.title.config.Messages;
import com.dracave.title.model.Reward;
import com.dracave.title.service.RewardService;
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
public final class RewardGui implements ClickableTitleGui {
    private final DraCaveTitlePlugin plugin;
    private final Player player;
    private final Map<Integer, Long> rewardSlots = new HashMap<>();
    private Inventory inventory;
    public RewardGui(DraCaveTitlePlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }
    public void open() {
        inventory = Bukkit.createInventory(this, 45, MiniMessage.miniMessage().deserialize("<gold>奖励中心</gold>"));
        int unlockedCount = 0;
        var data = plugin.service().getCached(player.getUniqueId());
        if (data != null) {
            unlockedCount = data.unlocked().size();
        }
        List<Reward> rewards = plugin.rewards().all();
        int slot = 0;
        for (Reward reward : rewards) {
            if (slot >= 36) {
                break;
            }
            rewardSlots.put(slot, reward.id());
            inventory.setItem(slot, rewardItem(reward, unlockedCount));
            slot++;
        }
        if (rewards.isEmpty()) {
            inventory.setItem(22, button(Material.BARRIER, plugin.messages().component("gui.empty")));
        }
        ItemStack pane = pane(Material.GRAY_STAINED_GLASS_PANE);
        for (int s = 36; s < 44; s++) {
            inventory.setItem(s, pane);
        }
        inventory.setItem(44, button(Material.OAK_DOOR, plugin.messages().component("gui.back-main")));
        plugin.guiSound().open(player);
        player.openInventory(inventory);
    }
    private ItemStack rewardItem(Reward reward, int unlockedCount) {
        Material material = switch (reward.type()) {
            case VAULT -> Material.GOLD_INGOT;
            case PLAYER_POINTS -> Material.LIGHT_BLUE_DYE;
            case COIN -> Material.EMERALD;
        };
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MiniMessage.miniMessage().deserialize("<gold>称号数量奖励 #" + reward.id() + "</gold>").decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(plugin.messages().component("gui.reward-count",
                Messages.text("number", Integer.toString(reward.number()))).decoration(TextDecoration.ITALIC, false));
        boolean claimed = plugin.rewards().isClaimed(player.getUniqueId(), reward.id());
        boolean met = unlockedCount >= reward.number();
        if (claimed) {
            lore.add(plugin.messages().component("gui.reward-done").decoration(TextDecoration.ITALIC, false));
        } else if (met) {
            lore.add(plugin.messages().component("gui.reward-claim").decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(plugin.messages().component("gui.reward-locked").decoration(TextDecoration.ITALIC, false));
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
    @Override
    public void click(int rawSlot, ClickType clickType) {
        if (rawSlot == 44) {
            plugin.guiSound().click(player);
            new MainMenuGui(plugin, player).open();
            return;
        }
        Long rewardId = rewardSlots.get(rawSlot);
        if (rewardId == null) {
            return;
        }
        plugin.guiSound().click(player);
        plugin.rewards().claim(player, rewardId).thenAccept(result -> SchedulerUtil.runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (result == RewardService.ClaimResult.SUCCESS) {
                plugin.guiSound().success(player);
                plugin.messages().send(player, "reward-claimed");
            } else if (result == RewardService.ClaimResult.NOT_MET) {
                plugin.guiSound().error(player);
                plugin.messages().send(player, "reward-unavailable");
            }
            new RewardGui(plugin, player).open();
        }));
    }
    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
