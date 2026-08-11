package com.dracave.title.service;
import com.dracave.title.DraCaveTitlePlugin;
import com.dracave.title.currency.CurrencyProvider;
import com.dracave.title.currency.CurrencyRegistry;
import com.dracave.title.model.PlayerData;
import com.dracave.title.model.Reward;
import com.dracave.title.model.RewardType;
import com.dracave.title.storage.RewardRepository;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
public final class RewardService implements AutoCloseable {
    private final DraCaveTitlePlugin plugin;
    private final TitleService titles;
    private final RewardRepository repository;
    private final CurrencyRegistry currencies;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "DraCaveTitle-Reward");
        t.setDaemon(true);
        return t;
    });
    public RewardService(DraCaveTitlePlugin plugin, TitleService titles, RewardRepository repository, CurrencyRegistry currencies) {
        this.plugin = plugin;
        this.titles = titles;
        this.repository = repository;
        this.currencies = currencies;
    }
    public void check(UUID playerId) {
        CompletableFuture.runAsync(() -> {
            try {
                PlayerData data = titles.getCached(playerId);
                if (data == null) {
                    return;
                }
                int count = data.unlocked().size();
                Player player = Bukkit.getPlayer(playerId);
                for (Reward reward : repository.findAll()) {
                    if (reward.number() <= count && !repository.isClaimed(playerId, reward.id())
                            && player != null && player.isOnline()) {
                        plugin.messages().send(player, "gui.reward-count",
                                com.dracave.title.config.Messages.text("number", Integer.toString(reward.number())));
                    }
                }
            } catch (Exception ex) {
                plugin.getLogger().warning("检查奖励达成失败: " + ex.getMessage());
            }
        }, executor);
    }
    public CompletableFuture<ClaimResult> claim(Player player, long rewardId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Reward reward = repository.findById(rewardId);
                if (reward == null) {
                    return ClaimResult.NOT_FOUND;
                }
                PlayerData data = titles.getCached(player.getUniqueId());
                int count = data == null ? 0 : data.unlocked().size();
                if (reward.number() > count) {
                    return ClaimResult.NOT_MET;
                }
                if (repository.isClaimed(player.getUniqueId(), rewardId)) {
                    return ClaimResult.ALREADY_CLAIMED;
                }
                if (!grant(player, reward)) {
                    return ClaimResult.UNAVAILABLE;
                }
                repository.claim(player.getUniqueId(), rewardId);
                return ClaimResult.SUCCESS;
            } catch (Exception ex) {
                plugin.getLogger().warning("领取奖励失败: " + ex.getMessage());
                return ClaimResult.FAILED;
            }
        }, executor);
    }
    public List<Reward> all() {
        try {
            return repository.findAll();
        } catch (Exception ex) {
            return List.of();
        }
    }
    public boolean isClaimed(UUID playerId, long rewardId) {
        try {
            return repository.isClaimed(playerId, rewardId);
        } catch (Exception ex) {
            return false;
        }
    }
    private boolean grant(Player player, Reward reward) {
        CurrencyProvider provider = switch (reward.type()) {
            case VAULT -> currencies.get(com.dracave.title.model.CurrencyType.VAULT);
            case PLAYER_POINTS -> currencies.get(com.dracave.title.model.CurrencyType.PLAYER_POINTS);
            case COIN -> currencies.get(com.dracave.title.model.CurrencyType.COIN);
        };
        if (provider == null || !provider.available()) {
            return false;
        }
        return provider.refund(player.getUniqueId(), BigDecimal.valueOf(reward.amount()));
    }
    public static String rewardTypeDisplay(RewardType type, DraCaveTitlePlugin plugin) {
        return switch (type) {
            case VAULT -> plugin.getConfig().getString("purchase.currencies.vault.display", "金币");
            case PLAYER_POINTS -> plugin.getConfig().getString("purchase.currencies.playerpoints.display", "点券");
            case COIN -> plugin.getConfig().getString("purchase.currencies.coin.display", "称号币");
        };
    }
    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5L, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("奖励任务未能在 5 秒内排空");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
    public enum ClaimResult {
        SUCCESS,
        NOT_FOUND,
        NOT_MET,
        ALREADY_CLAIMED,
        UNAVAILABLE,
        FAILED
    }
}
