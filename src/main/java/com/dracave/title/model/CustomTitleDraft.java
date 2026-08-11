package com.dracave.title.model;
import java.util.List;
public record CustomTitleDraft(
        String text,
        CustomTitleType type,
        List<String> colors,
        List<String> frames,
        int periodTicks,
        String icon
) {
    public CustomTitleDraft {
        colors = List.copyOf(colors);
        frames = List.copyOf(frames);
    }
    public static CustomTitleDraft staticTitle(String text, String color, String icon) {
        return new CustomTitleDraft(text, CustomTitleType.STATIC,
                color == null || color.isBlank() ? List.of() : List.of(color), List.of(), 40, icon);
    }
    public static CustomTitleDraft gradient(String text, List<String> colors, int periodTicks, String icon) {
        return new CustomTitleDraft(text, CustomTitleType.FLOWING_GRADIENT, colors, List.of(), periodTicks, icon);
    }
    public static CustomTitleDraft rainbow(String text, int periodTicks, String icon) {
        return new CustomTitleDraft(text, CustomTitleType.RAINBOW, List.of(), List.of(), periodTicks, icon);
    }
    public static CustomTitleDraft flash(String text, List<String> colors, int periodTicks, String icon) {
        return new CustomTitleDraft(text, CustomTitleType.FLASHING_COLORS, colors, List.of(), periodTicks, icon);
    }
    public static CustomTitleDraft frames(String text, List<String> frames, int periodTicks, String icon) {
        return new CustomTitleDraft(text, CustomTitleType.TEXT_FRAMES, List.of(), frames, periodTicks, icon);
    }
}
