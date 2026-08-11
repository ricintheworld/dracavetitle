package com.dracave.title.listener;
import com.dracave.title.DraCaveTitlePlugin;
import com.dracave.title.model.CurrencyType;
import com.dracave.title.model.TitleDefinition;
import com.dracave.title.render.TitleRenderer;
import com.dracave.title.service.TitleCardService;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
public final class TitleCardListener implements Listener {
    private final DraCaveTitlePlugin plugin;
    public TitleCardListener(DraCaveTitlePlugin plugin) {
        this.plugin = plugin;
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null || item.getType() == Material.AIR) {
            return;
        }
        TitleCardService cards = plugin.cardService();
        if (cards == null || !cards.isCard(item)) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        String kind = cards.cardKind(item);
        int days = cards.cardDays(item);
        if ("random".equals(kind)) {
            CurrencyType currency = cards.cardCurrency(item);
            if (currency == null) {
                plugin.messages().send(player, "card-none");
                return;
            }
            TitleDefinition title = cards.randomPurchasable(currency, player);
            if (title == null) {
                plugin.messages().send(player, "card-none");
                return;
            }
            consumeOne(player, item);
            plugin.service().grant(player.getUniqueId(), title.id(), days).thenAccept(result ->
                    plugin.messages().send(player, "card-used",
                            com.dracave.title.config.Messages.parsed("title", TitleRenderer.miniMessage(title, System.currentTimeMillis()))));
        } else if ("fixed".equals(kind)) {
            String titleId = cards.cardTitleId(item);
            if (titleId == null || plugin.registry().get(titleId) == null) {
                return;
            }
            consumeOne(player, item);
            plugin.service().grant(player.getUniqueId(), titleId, days).thenAccept(result ->
                    plugin.messages().send(player, "card-used",
                            com.dracave.title.config.Messages.parsed("title", TitleRenderer.miniMessage(plugin.registry().get(titleId), System.currentTimeMillis()))));
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        ItemStack item = event.getPlayer().getInventory().getItemInMainHand();
        if (item.getType() == Material.AIR) {
            return;
        }
        TitleCardService cards = plugin.cardService();
        if (cards == null || !cards.isCard(item)) {
            return;
        }
        event.setCancelled(true);
    }
    private void consumeOne(Player player, ItemStack item) {
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }
    }
}
