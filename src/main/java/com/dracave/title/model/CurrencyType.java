package com.dracave.title.model;
import java.util.Locale;
public enum CurrencyType {
    VAULT("vault"),
    PLAYER_POINTS("playerpoints"),
    COIN("coin"),
    ITEM("item");
    private final String id;
    CurrencyType(String id) {
        this.id = id;
    }
    public String id() {
        return id;
    }
    public static CurrencyType parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("purchase.currency is required");
        }
        String normalized = value.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
        return switch (normalized) {
            case "vault" -> VAULT;
            case "playerpoints", "points", "pp" -> PLAYER_POINTS;
            case "coin", "titlecoin" -> COIN;
            case "item", "itemstack", "物品" -> ITEM;
            default -> throw new IllegalArgumentException("unknown purchase currency: " + value);
        };
    }
}
