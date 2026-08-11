package com.dracave.title.model;
import java.util.List;
public record TitleDefinition(
        String id,
        String display,
        List<String> description,
        String icon,
        int order,
        boolean defaultUnlocked,
        String permission,
        TitleAnimation animation,
        TitlePurchaseOffer purchaseOffer,
        List<String> colors,
        boolean shopHidden,
        List<TitlePotionEffect> potionEffects,
        TitleParticle particle,
        int revision
) {
    public TitleDefinition {
        description = List.copyOf(description);
        colors = List.copyOf(colors);
        potionEffects = List.copyOf(potionEffects);
        permission = permission == null ? "" : permission;
        if (revision < 0) {
            throw new IllegalArgumentException("revision must not be negative");
        }
    }
    public TitleDefinition(String id, String display, List<String> description, String icon, int order,
                           boolean defaultUnlocked, String permission, TitleAnimation animation,
                           TitlePurchaseOffer purchaseOffer, List<String> colors, boolean shopHidden,
                           List<TitlePotionEffect> potionEffects, TitleParticle particle) {
        this(id, display, description, icon, order, defaultUnlocked, permission, animation, purchaseOffer,
                colors, shopHidden, potionEffects, particle, 0);
    }
    public TitleDefinition(String id, String display, List<String> description, String icon, int order,
                           boolean defaultUnlocked, String permission, TitleAnimation animation, TitlePurchaseOffer purchaseOffer) {
        this(id, display, description, icon, order, defaultUnlocked, permission, animation, purchaseOffer,
                animation == null ? List.of() : animation.colors(),
                purchaseOffer == null, List.of(), null, 0);
    }
    public TitleDefinition(String id, String display, List<String> description, String icon, int order,
                           boolean defaultUnlocked, String permission, TitleAnimation animation) {
        this(id, display, description, icon, order, defaultUnlocked, permission, animation, null);
    }
    public boolean animated() {
        return animation != null;
    }
    public boolean purchasable() {
        return purchaseOffer != null;
    }
    public TitleDefinition withRevision(int revision) {
        return new TitleDefinition(id, display, description, icon, order, defaultUnlocked, permission, animation,
                purchaseOffer, colors, shopHidden, potionEffects, particle, revision);
    }
    public TitleDefinition withOffer(TitlePurchaseOffer offer, boolean hidden) {
        return new TitleDefinition(id, display, description, icon, order, defaultUnlocked, permission, animation,
                offer, colors, hidden, potionEffects, particle, revision);
    }
}
