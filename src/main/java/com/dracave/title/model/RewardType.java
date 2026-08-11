package com.dracave.title.model;
import java.util.Locale;
public enum RewardType {
    VAULT("vault"),
    PLAYER_POINTS("playerpoints"),
    COIN("coin");
    private final String id;
    RewardType(String id) {
        this.id = id;
    }
    public String id() {
        return id;
    }
    public static RewardType parse(String value) {
        if (value == null) {
            throw new IllegalArgumentException("reward type is required");
        }
        return switch (value.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "")) {
            case "vault" -> VAULT;
            case "playerpoints", "points", "pp" -> PLAYER_POINTS;
            case "coin", "titlecoin" -> COIN;
            default -> throw new IllegalArgumentException("unknown reward type: " + value);
        };
    }
}
