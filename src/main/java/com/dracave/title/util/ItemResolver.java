package com.dracave.title.util;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import com.destroystokyo.paper.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;
import java.net.URI;
import java.net.URL;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;
/**
 * 统一物品解析工具，支持多种物品来源格式。
 * <ul>
 *   <li>原版材质名：NAME_TAG、NETHER_STAR（大小写不敏感）</li>
 *   <li>玩家头颅纹理：head:&lt;base64-texture&gt; 或 head:&lt;player-name&gt;</li>
 *   <li>序列化物品：base64:&lt;encoded-item-stack&gt;</li>
 * </ul>
 * 解析失败时回退到 NAME_TAG。
 */
public final class ItemResolver {
    private static final UUID PROFILE_UUID = UUID.randomUUID();
    private ItemResolver() {
    }
    public static ItemStack resolve(String icon) {
        if (icon == null || icon.isBlank()) {
            return new ItemStack(Material.NAME_TAG);
        }
        String trimmed = icon.trim();
        if (trimmed.toLowerCase(Locale.ROOT).startsWith("head:")) {
            return resolveHead(trimmed.substring(5).trim());
        }
        if (trimmed.toLowerCase(Locale.ROOT).startsWith("base64:")) {
            return resolveBase64(trimmed.substring(7).trim());
        }
        org.bukkit.inventory.ItemStack custom = CustomItemProvider.resolve(trimmed);
        if (custom != null) return custom;
        Material material = Material.matchMaterial(trimmed);
        if (material != null && !material.isAir()) {
            return new ItemStack(material);
        }
        return new ItemStack(Material.NAME_TAG);
    }
    public static boolean isValid(String icon) {
        if (icon == null || icon.isBlank()) {
            return false;
        }
        String trimmed = icon.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.startsWith("head:") && trimmed.length() > 5) {
            return true;
        }
        if (lower.startsWith("base64:") && trimmed.length() > 7) {
            return true;
        }
        if (CustomItemProvider.resolve(trimmed) != null) return true;
        Material material = Material.matchMaterial(trimmed);
        return material != null && material != Material.AIR
                && material != Material.CAVE_AIR && material != Material.VOID_AIR;
    }
    private static ItemStack resolveHead(String value) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (meta == null) {
            return item;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(value);
            String json = new String(decoded, java.nio.charset.StandardCharsets.UTF_8);
            String textureUrl = extractTextureUrl(json);
            if (textureUrl != null) {
                applyTexture(meta, textureUrl);
            }
        } catch (IllegalArgumentException notBase64) {
            PlayerProfile profile = org.bukkit.Bukkit.createProfile(value);
            meta.setPlayerProfile(profile);
        } catch (Exception ignored) {
        }
        item.setItemMeta(meta);
        return item;
    }
    private static String extractTextureUrl(String json) {
        int idx = json.indexOf("\"url\":\"");
        if (idx < 0) {
            return null;
        }
        int start = idx + 7;
        int end = json.indexOf("\"", start);
        if (end < 0) {
            return null;
        }
        return json.substring(start, end);
    }
    private static void applyTexture(SkullMeta meta, String textureUrl) {
        try {
            PlayerProfile profile = org.bukkit.Bukkit.createProfile(PROFILE_UUID);
            PlayerTextures textures = profile.getTextures();
            URI uri = new URI(textureUrl);
            URL url = uri.toURL();
            textures.setSkin(url);
            profile.setTextures(textures);
            meta.setPlayerProfile(profile);
        } catch (Exception ignored) {
        }
    }
    @SuppressWarnings("deprecation")
    private static ItemStack resolveBase64(String encoded) {
        try {
            byte[] data = Base64.getDecoder().decode(encoded);
            return org.bukkit.inventory.ItemStack.deserializeBytes(data);
        } catch (Exception ignored) {
            try {
                return org.bukkit.Bukkit.getUnsafe().deserializeItem(Base64.getDecoder().decode(encoded));
            } catch (Exception fallback) {
                return new ItemStack(Material.NAME_TAG);
            }
        }
    }
}
