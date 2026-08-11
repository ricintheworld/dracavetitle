package com.dracave.title.listener;
import com.dracave.title.DraCaveTitlePlugin;
import com.dracave.title.model.PlayerData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
public final class TitleListener implements Listener {
    private final DraCaveTitlePlugin plugin;
    public TitleListener(DraCaveTitlePlugin plugin) {
        this.plugin = plugin;
    }
    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (plugin.service() == null) {
            return;
        }
        plugin.service().load(event.getUniqueId());
    }
    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (plugin.service() == null) {
            return;
        }
        plugin.service().load(player.getUniqueId()).thenAccept(data -> {
            if (data == null) {
                return;
            }
            plugin.service().reconcileEffects(player.getUniqueId());
            plugin.service().purgeExpired(player.getUniqueId());
        });
    }
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (plugin.potionService() != null) {
            plugin.potionService().release(player);
        }
        if (plugin.particleService() != null) {
            plugin.particleService().reconcile(player.getUniqueId());
        }
        if (plugin.service() != null) {
            plugin.service().unload(player.getUniqueId());
        }
        if (plugin.chatPrompts() != null) {
            plugin.chatPrompts().clear(player);
        }
    }
}
