package com.dracave.title.model;
public record Reward(long id, int number, RewardType type, long amount) {
    public Reward {
        if (id <= 0) {
            throw new IllegalArgumentException("reward id must be positive");
        }
        if (number < 1) {
            throw new IllegalArgumentException("reward number must be >= 1");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("reward amount must be positive");
        }
    }
}
