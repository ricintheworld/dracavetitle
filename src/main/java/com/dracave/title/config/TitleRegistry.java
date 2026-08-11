package com.dracave.title.config;
import com.dracave.title.model.TitleDefinition;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
public final class TitleRegistry {
    private static final Pattern VALID_ID = Pattern.compile("[a-z0-9][a-z0-9_-]{0,63}");
    private final JavaPlugin plugin;
    private volatile Map<String, TitleDefinition> configured = Map.of();
    private final Map<String, TitleDefinition> runtime = new ConcurrentHashMap<>();
    private final Map<String, TitleDefinition> custom = new ConcurrentHashMap<>();
    private final Map<String, UUID> customOwners = new ConcurrentHashMap<>();
    public TitleRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
    }
    public void replaceConfigured(Collection<TitleDefinition> definitions) {
        Map<String, TitleDefinition> loaded = new HashMap<>();
        for (TitleDefinition definition : definitions) {
            String id = normalizeId(definition.id());
            if (!id.equals(definition.id()) || !VALID_ID.matcher(id).matches()) {
                throw new IllegalArgumentException("invalid title id from storage: " + definition.id());
            }
            if (loaded.put(id, definition) != null) {
                throw new IllegalArgumentException("duplicate title id: " + id);
            }
        }
        this.configured = Map.copyOf(loaded);
        plugin.getLogger().info("已从数据库加载 " + this.configured.size() + " 个全局称号及 " + this.runtime.size() + " 个运行时称号");
    }
    public TitleDefinition get(String id) {
        if (id == null) {
            return null;
        }
        String key = normalizeId(id);
        TitleDefinition dynamic = runtime.get(key);
        if (dynamic != null) {
            return dynamic;
        }
        TitleDefinition customTitle = custom.get(key);
        return customTitle != null ? customTitle : configured.get(key);
    }
    public List<TitleDefinition> all() {
        Map<String, TitleDefinition> merged = new LinkedHashMap<>();
        for (TitleDefinition title : configured.values()) {
            merged.put(title.id(), title);
        }
        runtime.forEach(merged::put);
        return merged.values().stream()
                .sorted(Comparator.comparingInt(TitleDefinition::order).thenComparing(TitleDefinition::id))
                .toList();
    }
    public List<TitleDefinition> configured() {
        return configured.values().stream()
                .sorted(Comparator.comparingInt(TitleDefinition::order).thenComparing(TitleDefinition::id))
                .toList();
    }
    public Collection<String> defaultIds() {
        return all().stream().filter(TitleDefinition::defaultUnlocked).map(TitleDefinition::id).toList();
    }
    public boolean register(TitleDefinition title) {
        String id = normalizeId(title.id());
        if (title.id().equals(id) && VALID_ID.matcher(id).matches()
                && !configured.containsKey(id) && !runtime.containsKey(id) && !custom.containsKey(id)) {
            runtime.put(id, title);
            return true;
        }
        return false;
    }
    public boolean unregister(String id) {
        return runtime.remove(normalizeId(id)) != null;
    }
    public void registerCustom(TitleDefinition title, UUID ownerId) {
        custom.put(title.id(), title);
        customOwners.put(title.id(), ownerId);
    }
    public void unregisterCustom(String id) {
        String normalized = normalizeId(id);
        custom.remove(normalized);
        customOwners.remove(normalized);
    }
    public boolean availableTo(String id, UUID playerId) {
        UUID owner = customOwners.get(normalizeId(id));
        return owner == null || owner.equals(playerId);
    }
    public static String normalizeId(String id) {
        return id.toLowerCase(Locale.ROOT).trim();
    }
}
