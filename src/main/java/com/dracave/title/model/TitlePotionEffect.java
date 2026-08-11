package com.dracave.title.model;
public record TitlePotionEffect(String effectType, int level) {
    public TitlePotionEffect {
        if (effectType == null || effectType.isBlank()) {
            throw new IllegalArgumentException("effect type is required");
        }
        if (level < 1) {
            throw new IllegalArgumentException("effect level must be >= 1");
        }
    }
}
