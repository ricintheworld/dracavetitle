package com.dracave.title.listener;

import com.dracave.title.DraCaveTitlePlugin;
import com.dracave.title.model.PlayerData;
import com.dracave.title.model.TitleDefinition;
import com.dracave.title.render.TitleRenderer;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public final class ChatListener implements Listener {
    private static final class Holder {
        static final MiniMessage MINI = MiniMessage.miniMessage();
        static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    }
    private final DraCaveTitlePlugin plugin;
    private final boolean papi;

    public ChatListener(DraCaveTitlePlugin plugin) {
        this.plugin = plugin;
        this.papi = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onChat(AsyncPlayerChatEvent event) {
        if (event.isCancelled()) return;
        if (!plugin.getConfig().getBoolean("chat.enabled", false)) return;
        PlayerData data = plugin.service().getCached(event.getPlayer().getUniqueId());
        TitleDefinition title = data != null && data.equippedId() != null
                ? plugin.registry().get(data.equippedId()) : null;
        String titleText;
        if (title != null) {
            titleText = TitleRenderer.miniMessage(title, System.currentTimeMillis());
        } else {
            titleText = plugin.getConfig().getString("chat.default-title", "");
            if (titleText == null || titleText.isBlank()) return;
        }
        String format = plugin.getConfig().getString("chat.format", "{title} {player} \u00bb ");
        format = format.replace("{title}", titleText).replace("{player}", event.getPlayer().getName());
        if (papi) format = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(event.getPlayer(), format);
        String prefix = Holder.LEGACY.serialize(Holder.MINI.deserialize(format));
        // 去除服务器原 chat-format 中的玩家名包装：<%1$s></%1$s>、<%1$s>、<玩家名>
        String base = event.getFormat().replaceAll("<[^>]+>", "");
        event.setFormat(prefix + base);
    }
}