package com.dracave.title.model;
import java.util.List;
import java.util.Objects;
public record TitleParticle(String particleType, String particleId, List<String> colors) {
    public TitleParticle {
        Objects.requireNonNull(particleType, "particleType");
        if (particleId == null || particleId.isBlank()) {
            particleId = null;
        }
        colors = List.copyOf(colors);
        if (colors.size() > 3) {
            throw new IllegalArgumentException("particle supports at most three colors");
        }
    }
    public static TitleParticle of(String particleType, String particleId, List<String> colors) {
        return new TitleParticle(particleType, particleId, colors);
    }
    public boolean hasColor() {
        return !colors.isEmpty();
    }
}
