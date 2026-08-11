package com.dracave.title.config;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
public final class Messages {
    private final JavaPlugin plugin;
    private final Map<String, String> cache = new ConcurrentHashMap<>();
    private volatile YamlConfiguration config;
    public Messages(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }
    public void reload() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        this.config = YamlConfiguration.loadConfiguration(file);
        this.cache.clear();
    }
    private String raw(String key) {
        return cache.computeIfAbsent(key, k -> config.getString(k, "<red>缺少消息键 " + k + "</red>"));
    }
    public String rawString(String key) {
        return raw(key);
    }
    public void send(CommandSender sender, String key, TagResolver... resolvers) {
        String prefix = raw("prefix");
        String message = raw(key);
        Component component = MiniMessage.miniMessage().deserialize(prefix + message, resolvers);
        sender.sendMessage(component);
    }
    public Component component(String key, TagResolver... resolvers) {
        return MiniMessage.miniMessage().deserialize(raw(key), resolvers);
    }
    public static TagResolver text(String name, String value) {
        return Placeholder.unparsed(name, value == null ? "" : value);
    }
    public static TagResolver parsed(String name, String value) {
        return Placeholder.component(name, MiniMessage.miniMessage().deserialize(value == null ? "" : value));
    }
}
