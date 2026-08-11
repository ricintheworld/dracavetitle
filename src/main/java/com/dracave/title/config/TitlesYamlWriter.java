package com.dracave.title.config;
import com.dracave.title.model.CurrencyType;
import com.dracave.title.model.TitleAnimation;
import com.dracave.title.model.TitleDefinition;
import com.dracave.title.model.TitleParticle;
import com.dracave.title.model.TitlePotionEffect;
import com.dracave.title.model.TitlePurchaseOffer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
/**
 * 将 {@link TitleDefinition} 序列化回 YAML 格式。
 * 支持写入完整的 titles.yml（带 titles 根节点）和单个标签文件（不带根节点）。
 */
public final class TitlesYamlWriter {
    public void writeAll(List<TitleDefinition> definitions, File file) {
        YamlConfiguration yaml = new YamlConfiguration();
        for (TitleDefinition def : definitions) {
            writeDefinition(yaml.createSection("titles." + def.id()), def);
        }
        save(yaml, file, "titles.yml");
    }
    public void writeSingle(TitleDefinition def, File file) {
        YamlConfiguration yaml = new YamlConfiguration();
        writeDefinition(yaml, def);
        save(yaml, file, def.id() + ".yml");
    }
    private void save(YamlConfiguration yaml, File file, String displayName) {
        try {
            yaml.save(file);
        } catch (IOException ex) {
            throw new RuntimeException("保存 " + displayName + " 失败: " + ex.getMessage(), ex);
        }
    }
    private void writeDefinition(ConfigurationSection section, TitleDefinition def) {
        section.set("text", def.display());
        if (!def.description().isEmpty()) {
            section.set("description", def.description());
        }
        section.set("icon", def.icon());
        section.set("order", def.order());
        section.set("default-unlocked", def.defaultUnlocked());
        if (!def.permission().isBlank()) {
            section.set("permission", def.permission());
        }
        if (!def.colors().isEmpty()) {
            section.set("colors", def.colors());
        }
        writeAnimation(section, def.animation());
        writeShop(section, def);
        writePotionEffects(section, def.potionEffects());
        writeParticle(section, def.particle());
    }
    private void writeAnimation(ConfigurationSection section, TitleAnimation anim) {
        if (anim == null) {
            return;
        }
        section.set("animation-type", animationTypeName(anim.type()));
        double seconds = anim.periodTicks() / 20.0;
        if (seconds >= 0.2 && seconds <= 60.0 && Math.abs(seconds - Math.round(seconds * 10.0) / 10.0) < 0.001) {
            section.set("gradient-cycle-seconds", String.format(java.util.Locale.ROOT, "%.1f", seconds));
        } else {
            section.set("period-ticks", anim.periodTicks());
        }
        if (anim.mode() == TitleAnimation.GradientMode.PINGPONG) {
            section.set("gradient-mode", "pingpong");
        }
        if (anim.type() == TitleAnimation.Type.TEXT_FRAMES && !anim.frames().isEmpty()) {
            section.set("frames", anim.frames());
        }
    }
    private void writeShop(ConfigurationSection section, TitleDefinition def) {
        TitlePurchaseOffer offer = def.purchaseOffer();
        if (offer == null && def.shopHidden()) {
            return;
        }
        ConfigurationSection shop = section.createSection("shop");
        shop.set("hidden", def.shopHidden());
        if (offer != null) {
            shop.set("currency", offer.currency().id());
            shop.set("price", offer.price().toPlainString());
            if (offer.currency() == CurrencyType.ITEM && offer.itemMaterial() != null) {
                shop.set("item", offer.itemMaterial());
            }
        }
    }
    private void writePotionEffects(ConfigurationSection section, List<TitlePotionEffect> effects) {
        if (effects.isEmpty()) {
            return;
        }
        List<Map<String, Object>> list = new ArrayList<>();
        for (TitlePotionEffect effect : effects) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("type", effect.effectType());
            map.put("level", effect.level());
            list.add(map);
        }
        section.set("potion-effects", list);
    }
    private void writeParticle(ConfigurationSection section, TitleParticle particle) {
        if (particle == null) {
            return;
        }
        section.set("particle.type", particle.particleType());
        if (particle.particleId() != null && !particle.particleId().isBlank()) {
            section.set("particle.id", particle.particleId());
        }
        if (!particle.colors().isEmpty()) {
            section.set("particle.colors", particle.colors());
        }
    }
    private String animationTypeName(TitleAnimation.Type type) {
        return switch (type) {
            case FLOWING_GRADIENT -> "gradient";
            case SOLID_GRADIENT -> "solid";
            case TEXT_FRAMES -> "frames";
            case RAINBOW -> "rainbow";
            case FLASHING_COLORS -> "flash";
        };
    }
}
