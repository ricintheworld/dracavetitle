package com.dracave.title.service;
import com.dracave.title.DraCaveTitlePlugin;
import com.dracave.title.model.TitleDefinition;
import com.dracave.title.model.TitleParticle;
import com.dracave.title.util.SchedulerUtil;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
public final class TitleParticleService {
    private final DraCaveTitlePlugin plugin;
    private final TitleService titles;
    private final Map<UUID, TitleParticle> active = new ConcurrentHashMap<>();
    private SchedulerUtil.Task particleTask;
    public TitleParticleService(DraCaveTitlePlugin plugin, TitleService titles) {
        this.plugin = plugin;
        this.titles = titles;
    }
    public void reconcile(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            active.remove(playerId);
            return;
        }
        TitleDefinition equipped = titles.equipped(playerId);
        TitleParticle particle = equipped == null ? null : equipped.particle();
        if (particle == null) {
            active.remove(playerId);
        } else {
            active.put(playerId, particle);
        }
    }
    public void start() {
        if (particleTask != null) {
            return;
        }
        particleTask = SchedulerUtil.runTaskTimer(plugin, () -> {
            for (Map.Entry<UUID, TitleParticle> entry : active.entrySet()) {
                Player player = Bukkit.getPlayer(entry.getKey());
                if (player == null || !player.isOnline()) {
                    continue;
                }
                TitleParticle config = entry.getValue();
                player.getScheduler().run(plugin, task -> spawn(player, config), null);
            }
        }, 5L, 5L);
    }
    public void stop() {
        if (particleTask != null) {
            particleTask.cancel();
            particleTask = null;
        }
        active.clear();
    }
    private void spawn(Player player, TitleParticle config) {
        try {
            Particle particle = Particle.valueOf(config.particleType().toUpperCase(Locale.ROOT));
            Location location = player.getLocation().add(0, 1.1, 0);
            switch (particle) {
                case DUST -> {
                    Color color = color(config, 0);
                    player.spawnParticle(particle, location, 1, 0.35, 0.35, 0.35, new Particle.DustOptions(color, 1.0F));
                }
                case DUST_COLOR_TRANSITION -> {
                    Color from = color(config, 0);
                    Color to = color(config, 1);
                    player.spawnParticle(particle, location, 1, 0.35, 0.35, 0.35,
                            new Particle.DustTransition(from, to, 1.0F));
                }
                default -> player.spawnParticle(particle, location, 1, 0.35, 0.35, 0.35, 0.01);
            }
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("粒子特效无效 " + config.particleType() + ": " + ex.getMessage());
            active.remove(player.getUniqueId());
        }
    }
    private Color color(TitleParticle config, int index) {
        if (config.colors().size() > index) {
            try {
                String hex = config.colors().get(index).replace("#", "");
                return Color.fromRGB(Integer.parseInt(hex, 16));
            } catch (NumberFormatException ignored) {
            }
        }
        return Color.WHITE;
    }
    public static boolean validParticle(String particleType) {
        if (particleType == null || particleType.isBlank()) {
            return false;
        }
        try {
            Particle.valueOf(particleType.toUpperCase(Locale.ROOT));
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
