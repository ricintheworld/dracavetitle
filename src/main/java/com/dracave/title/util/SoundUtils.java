package com.dracave.title.util;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
/**
 * 音效播放工具，兼容不同 Minecraft 版本和资源包自定义音效。
 * <p>
 * 支持两种模式：
 * <ul>
 *   <li>原版 Sound 枚举名（如 BLOCK_NOTE_BLOCK_PLING）——通过缓存加速查找</li>
 *   <li>命名空间音效字符串（如 mypack:custom.click）——直接以字符串形式播放</li>
 * </ul>
 * 查找失败时静默跳过，不会抛出异常。
 */
public final class SoundUtils {
    private static final Map<String, Sound> SOUND_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> MISSING_CACHE = new ConcurrentHashMap<>();
    private SoundUtils() {
    }
    public static void play(Player player, String soundName, float volume, float pitch) {
        if (soundName == null || soundName.isBlank()) {
            return;
        }
        String name = soundName.trim();
        if (name.contains(":")) {
            player.playSound(player.getLocation(), name, volume, pitch);
            return;
        }
        Sound sound = SOUND_CACHE.get(name);
        if (sound == null) {
            if (MISSING_CACHE.getOrDefault(name, false)) {
                return;
            }
            try {
                sound = Sound.valueOf(name.toUpperCase(Locale.ROOT));
                SOUND_CACHE.put(name, sound);
            } catch (IllegalArgumentException ex) {
                MISSING_CACHE.put(name, true);
                return;
            }
        }
        player.playSound(player.getLocation(), sound, volume, pitch);
    }
    public static void play(Player player, String primary, String fallback, float volume, float pitch) {
        if (primary != null && !primary.isBlank() && !isMissing(primary)) {
            play(player, primary, volume, pitch);
        } else if (fallback != null && !fallback.isBlank()) {
            play(player, fallback, volume, pitch);
        }
    }
    private static boolean isMissing(String name) {
        if (name.contains(":")) {
            return false;
        }
        if (SOUND_CACHE.containsKey(name)) {
            return false;
        }
        return MISSING_CACHE.getOrDefault(name, false) || !exists(name);
    }
    private static boolean exists(String name) {
        try {
            Sound sound = Sound.valueOf(name.toUpperCase(Locale.ROOT));
            SOUND_CACHE.put(name, sound);
            return true;
        } catch (IllegalArgumentException e) {
            MISSING_CACHE.put(name, true);
            return false;
        }
    }
}
