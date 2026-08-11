package com.dracave.title.hook;
import com.dracave.title.DraCaveTitlePlugin;
import com.dracave.title.api.DraCaveTitleAPI;
import com.dracave.title.model.TitleDefinition;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import java.util.Locale;
public final class DraCaveTitleExpansion extends PlaceholderExpansion {
    private final DraCaveTitlePlugin plugin;
    public DraCaveTitleExpansion(DraCaveTitlePlugin plugin) {
        this.plugin = plugin;
    }
    @Override
    public @NotNull String getIdentifier() {
        return "dracavetitle";
    }
    @Override
    public @NotNull String getAuthor() {
        return "DraCave";
    }
    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }
    @Override
    public boolean persist() {
        return true;
    }
    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) {
            return "";
        }
        TitleDefinition title = DraCaveTitleAPI.getEquippedTitle(player.getUniqueId()).orElse(null);
        return switch (params.toLowerCase(Locale.ROOT)) {
            case "title" -> DraCaveTitleAPI.getMiniMessage(player.getUniqueId());
            case "title_legacy" -> DraCaveTitleAPI.getLegacyAmpersand(player.getUniqueId());
            case "title_legacy_section" -> DraCaveTitleAPI.getLegacySection(player.getUniqueId());
            case "title_plain" -> DraCaveTitleAPI.getPlainText(player.getUniqueId());
            case "title_id" -> title == null ? "" : title.id();
            case "has_title" -> Boolean.toString(title != null);
            case "coin" -> String.valueOf(DraCaveTitleAPI.getCoinBalance(player.getUniqueId()));
            default -> null;
        };
    }
}
