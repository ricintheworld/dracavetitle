package com.dracave.title.render;
import com.dracave.title.model.TitleAnimation;
import com.dracave.title.model.TitleDefinition;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import java.awt.Color;
import java.util.concurrent.ConcurrentHashMap;
public final class TitleRenderer {
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_AMPERSAND = legacy('&');
    private static final LegacyComponentSerializer LEGACY_SECTION = legacy('§');
    private static final long TICK_MILLIS = 50L;
    private static final ConcurrentHashMap<String, CachedFrame> CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, String> PLAIN_CACHE = new ConcurrentHashMap<>();
    private static volatile int frameStepTicks = 2;
    private static volatile int gradientCharStep = 1;
    private TitleRenderer() {
    }
    public static void configure(int frameStepTicks, int gradientCharStep) {
        TitleRenderer.frameStepTicks = Math.max(1, frameStepTicks);
        TitleRenderer.gradientCharStep = Math.max(1, gradientCharStep);
        CACHE.clear();
    }
    private static long tick(long nowMillis) {
        long tick = nowMillis / TICK_MILLIS;
        return tick - Math.floorMod(tick, frameStepTicks);
    }
    public static String miniMessage(TitleDefinition title, long nowMillis) {
        TitleAnimation animation = title.animation();
        if (animation == null) {
            if (title.colors().size() == 1) {
                String color = title.colors().get(0);
                return "<" + color + ">" + title.display() + "</" + color + ">";
            }
            return title.display();
        }
        long tick = tick(nowMillis);
        CachedFrame cached = CACHE.get(title.id());
        if (cached != null && cached.title() == title && cached.tick() == tick) {
            return cached.miniMessage();
        }
        String rendered = render(title, animation, tick);
        CACHE.put(title.id(), new CachedFrame(title, tick, rendered, null, null, null));
        if (CACHE.size() > 4096) {
            CACHE.entrySet().removeIf(entry -> entry.getValue().tick() < tick - 1);
        }
        return rendered;
    }
    private static String render(TitleDefinition title, TitleAnimation animation, long tick) {
        double cycle = cycle(tick, animation.periodTicks());
        return switch (animation.type()) {
            case FLOWING_GRADIENT ->
                    FlowingGradientRenderer.render(title.display(), animation.colors(), cycle, animation.mode(), gradientCharStep);
            case SOLID_GRADIENT ->
                    SolidGradientRenderer.render(title.display(), animation.colors(), cycle, animation.mode());
            case TEXT_FRAMES -> animation.frames().get(frame(tick, animation.periodTicks(), animation.frames().size()));
            case FLASHING_COLORS -> {
                String color = animation.colors().get(frame(tick, animation.periodTicks(), animation.colors().size()));
                yield "<" + color + ">" + title.display() + "</" + color + ">";
            }
            case RAINBOW -> rainbow(plain(title.display()), cycle);
        };
    }
    public static Component component(TitleDefinition title, long nowMillis) {
        if (!title.animated()) {
            return MINI.deserialize(miniMessage(title, nowMillis));
        }
        long tick = tick(nowMillis);
        String rendered = miniMessage(title, nowMillis);
        CachedFrame cached = CACHE.get(title.id());
        if (cached != null && cached.title() == title && cached.tick() == tick && cached.component() != null) {
            return cached.component();
        }
        Component component = MINI.deserialize(rendered);
        CACHE.computeIfPresent(title.id(), (id, frame) ->
                frame.title() == title && frame.tick() == tick
                        ? new CachedFrame(title, tick, rendered, component, frame.legacyAmpersand(), frame.legacySection())
                        : frame);
        return component;
    }
    public static String plain(TitleDefinition title, long nowMillis) {
        return PlainTextComponentSerializer.plainText().serialize(component(title, nowMillis));
    }
    public static String legacyAmpersand(TitleDefinition title, long nowMillis) {
        return legacy(title, nowMillis, false);
    }
    public static String legacySection(TitleDefinition title, long nowMillis) {
        return legacy(title, nowMillis, true);
    }
    private static String legacy(TitleDefinition title, long nowMillis, boolean section) {
        Component component = component(title, nowMillis);
        if (!title.animated()) {
            return (section ? LEGACY_SECTION : LEGACY_AMPERSAND).serialize(component);
        }
        long tick = tick(nowMillis);
        CachedFrame cached = CACHE.get(title.id());
        if (cached != null && cached.title() == title && cached.tick() == tick) {
            String value = section ? cached.legacySection() : cached.legacyAmpersand();
            if (value != null) {
                return value;
            }
        }
        String value = (section ? LEGACY_SECTION : LEGACY_AMPERSAND).serialize(component);
        CACHE.computeIfPresent(title.id(), (id, frame) -> {
            if (frame.title() == title && frame.tick() == tick) {
                return section
                        ? new CachedFrame(title, tick, frame.miniMessage(), component, frame.legacyAmpersand(), value)
                        : new CachedFrame(title, tick, frame.miniMessage(), component, value, frame.legacySection());
            }
            return frame;
        });
        return value;
    }
    private static String plain(String display) {
        if (PLAIN_CACHE.size() > 4096) {
            PLAIN_CACHE.clear();
        }
        return PLAIN_CACHE.computeIfAbsent(display,
                text -> PlainTextComponentSerializer.plainText().serialize(MINI.deserialize(text)));
    }
    private static String rainbow(String plainText, double cycle) {
        int[] points = plainText.codePoints().toArray();
        StringBuilder result = new StringBuilder(points.length * 11);
        String last = null;
        for (int i = 0; i < points.length; i++) {
            float hue = (float) ((cycle + (double) (i - i % gradientCharStep) / Math.max(1, points.length)) % 1.0);
            Color color = Color.getHSBColor(hue, 0.85F, 1.0F);
            String hex = GradientColor.hex(color.getRed(), color.getGreen(), color.getBlue());
            if (!hex.equals(last)) {
                result.append('<').append(hex).append('>');
                last = hex;
            }
            result.append(MINI.escapeTags(new String(points, i, 1)));
        }
        return result.toString();
    }
    private static double cycle(long tick, int periodTicks) {
        return (double) Math.floorMod(tick, periodTicks) / (double) periodTicks;
    }
    private static int frame(long tick, int frameTicks, int frameCount) {
        return Math.floorMod(Math.floorDiv(tick, frameTicks), frameCount);
    }
    private static LegacyComponentSerializer legacy(char character) {
        return LegacyComponentSerializer.builder().character(character).hexColors().useUnusualXRepeatedCharacterHexFormat().build();
    }
    private record CachedFrame(TitleDefinition title, long tick, String miniMessage, Component component,
                               String legacyAmpersand, String legacySection) {
    }
}
