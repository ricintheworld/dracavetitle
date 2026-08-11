package com.dracave.title.model;
import java.util.List;
import java.util.Locale;
public record TitleAnimation(Type type, List<String> colors, List<String> frames, int periodTicks, GradientMode mode) {
    public TitleAnimation {
        if (type == null) {
            throw new IllegalArgumentException("animation type is required");
        }
        colors = List.copyOf(colors);
        frames = List.copyOf(frames);
        if (periodTicks < 1) {
            throw new IllegalArgumentException("periodTicks must be positive");
        }
        if ((type == Type.FLOWING_GRADIENT || type == Type.SOLID_GRADIENT || type == Type.FLASHING_COLORS) && colors.size() < 2) {
            throw new IllegalArgumentException("animation requires at least two colors");
        }
        if (type == Type.TEXT_FRAMES && frames.size() < 2) {
            throw new IllegalArgumentException("text animation requires at least two frames");
        }
        if (mode == null) {
            mode = GradientMode.CYCLE;
        }
    }
    public TitleAnimation(Type type, List<String> colors, List<String> frames, int periodTicks) {
        this(type, colors, frames, periodTicks, GradientMode.CYCLE);
    }
    public TitleAnimation(List<String> colors, int periodTicks) {
        this(Type.FLOWING_GRADIENT, colors, List.of(), periodTicks, GradientMode.CYCLE);
    }
    public TitleAnimation(List<String> colors, int periodTicks, GradientMode mode) {
        this(Type.FLOWING_GRADIENT, colors, List.of(), periodTicks, mode);
    }
    public static TitleAnimation rainbow(int periodTicks) {
        return new TitleAnimation(Type.RAINBOW, List.of(), List.of(), periodTicks);
    }
    public enum Type {
        FLOWING_GRADIENT,
        SOLID_GRADIENT,
        TEXT_FRAMES,
        RAINBOW,
        FLASHING_COLORS
    }
    public enum GradientMode {
        CYCLE,
        PINGPONG;
        public static GradientMode parse(String value) {
            if (value == null) {
                return CYCLE;
            }
            return switch (value.toLowerCase(Locale.ROOT).replace("-", "_")) {
                case "pingpong", "bounce", "cos", "回弹" -> PINGPONG;
                default -> CYCLE;
            };
        }
    }
}
