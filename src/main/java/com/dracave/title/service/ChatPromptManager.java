package com.dracave.title.service;
import com.dracave.title.DraCaveTitlePlugin;
import com.dracave.title.util.SchedulerUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
public final class ChatPromptManager implements Listener {
    private final DraCaveTitlePlugin plugin;
    private final Map<UUID, PendingPrompt> pending = new ConcurrentHashMap<>();
    public ChatPromptManager(DraCaveTitlePlugin plugin) {
        this.plugin = plugin;
    }
    public void prompt(Player player, String message, BiConsumer<Player, String> handler, boolean cancelChat) {
        pending.put(player.getUniqueId(), new PendingPrompt(handler, cancelChat, System.currentTimeMillis()));
        player.sendMessage(message);
    }
    public boolean hasPending(Player player) {
        return pending.containsKey(player.getUniqueId());
    }
    public void clear(Player player) {
        pending.remove(player.getUniqueId());
    }
    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        PendingPrompt prompt = pending.get(event.getPlayer().getUniqueId());
        if (prompt == null) {
            return;
        }
        if (System.currentTimeMillis() - prompt.createdAt > 120_000L) {
            pending.remove(event.getPlayer().getUniqueId());
            return;
        }
        if (prompt.cancelChat) {
            event.setCancelled(true);
        }
        pending.remove(event.getPlayer().getUniqueId());
        String input = event.getMessage();
        SchedulerUtil.runTask(plugin, () -> prompt.handler.accept(event.getPlayer(), input));
    }
    private record PendingPrompt(BiConsumer<Player, String> handler, boolean cancelChat, long createdAt) {
    }
}
