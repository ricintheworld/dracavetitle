package com.dracave.title.model;
import java.util.List;
import java.util.UUID;
public record CustomTitle(
        String id,
        UUID ownerId,
        String text,
        CustomTitleType type,
        List<String> colors,
        List<String> frames,
        int periodTicks,
        String icon,
        int revision,
        long createdAt,
        long updatedAt
) {
    public CustomTitle {
        colors = List.copyOf(colors);
        frames = List.copyOf(frames);
    }
}
