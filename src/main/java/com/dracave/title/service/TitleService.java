package com.dracave.title.service;
import com.dracave.title.DraCaveTitlePlugin;
import com.dracave.title.api.TitleResult;
import com.dracave.title.api.event.TitleEquipEvent;
import com.dracave.title.api.event.TitleRevokeEvent;
import com.dracave.title.api.event.TitleUnequipEvent;
import com.dracave.title.api.event.TitleUnlockEvent;
import com.dracave.title.config.TitleRegistry;
import com.dracave.title.model.PlayerData;
import com.dracave.title.model.TitleDefinition;
import com.dracave.title.storage.TitleRepository;
import com.dracave.title.util.SchedulerUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
public final class TitleService {
    private final DraCaveTitlePlugin plugin;
    private final TitleRegistry registry;
    private final TitleRepository repository;
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();
    private final Set<UUID> loading = ConcurrentHashMap.newKeySet();
    private final Map<UUID, CompletableFuture<PlayerData>> loadingFutures = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastSyncedUpdatedAt = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastLocalWriteAt = new ConcurrentHashMap<>();
    private volatile Consumer<UUID> syncPublisher = ignored -> {
    };
    private volatile Consumer<UUID> effectReconciler = ignored -> {
    };
    private volatile Consumer<UUID> rewardChecker = ignored -> {
    };
    private final Map<UUID, Long> lastEquipAt = new ConcurrentHashMap<>();
    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "DraCaveTitle-Database");
        thread.setDaemon(true);
        return thread;
    });
    public TitleService(DraCaveTitlePlugin plugin, TitleRegistry registry, TitleRepository repository) {
        this.plugin = plugin;
        this.registry = registry;
        this.repository = repository;
    }
    public CompletableFuture<PlayerData> load(UUID playerId) {
        PlayerData current = cache.get(playerId);
        if (current != null) {
            return CompletableFuture.completedFuture(current);
        }
        CompletableFuture<PlayerData> inFlight = loadingFutures.get(playerId);
        if (inFlight != null) {
            return inFlight;
        }
        CompletableFuture<PlayerData> future = new CompletableFuture<>();
        CompletableFuture<PlayerData> existing = loadingFutures.putIfAbsent(playerId, future);
        if (existing != null) {
            return existing;
        }
        loading.add(playerId);
        try {
            CompletableFuture.supplyAsync(() -> {
                try {
                    PlayerData ready = loadReady(playerId);
                    cache.put(playerId, ready);
                    effectReconciler.accept(playerId);
                    return ready;
                } catch (SQLException ex) {
                    plugin.getLogger().severe("加载玩家称号数据失败 " + playerId + ": " + ex.getMessage());
                    return null;
                } finally {
                    loading.remove(playerId);
                }
            }, databaseExecutor).whenComplete((ready, error) -> {
                loadingFutures.remove(playerId);
                future.complete(ready);
            });
        } catch (java.util.concurrent.RejectedExecutionException ex) {
            loadingFutures.remove(playerId);
            loading.remove(playerId);
            future.complete(null);
        }
        return future;
    }
    public PlayerData getCached(UUID playerId) {
        return cache.get(playerId);
    }
    public void reconcileEffects(UUID playerId) {
        effectReconciler.accept(playerId);
    }
    public boolean isLoading(UUID playerId) {
        return loading.contains(playerId);
    }
    public void unload(UUID playerId) {
        cache.remove(playerId);
        lastSyncedUpdatedAt.remove(playerId);
        lastEquipAt.remove(playerId);
        lastLocalWriteAt.remove(playerId);
    }
    public void setSyncPublisher(Consumer<UUID> syncPublisher) {
        this.syncPublisher = syncPublisher == null ? ignored -> {
        } : syncPublisher;
    }
    public void setEffectReconciler(Consumer<UUID> effectReconciler) {
        this.effectReconciler = effectReconciler == null ? ignored -> {
        } : effectReconciler;
    }
    public void setRewardChecker(Consumer<UUID> rewardChecker) {
        this.rewardChecker = rewardChecker == null ? ignored -> {
        } : rewardChecker;
    }
    public void synchronizeEquipped(Collection<UUID> playerIds) {
        if (playerIds.isEmpty()) {
            return;
        }
        try {
            Map<UUID, TitleRepository.EquippedSnapshot> snapshots = repository.batchLoadEquipped(playerIds);
            for (UUID playerId : playerIds) {
                TitleRepository.EquippedSnapshot snapshot = snapshots.get(playerId);
                if (snapshot == null) {
                    continue;
                }
                PlayerData cached = cache.get(playerId);
                if (cached == null) {
                    continue;
                }
                boolean equippedChanged = !Objects.equals(cached.equippedId(), snapshot.equippedId());
                Long lastSynced = lastSyncedUpdatedAt.get(playerId);
                boolean timestampChanged = lastSynced == null || snapshot.updatedAt() != lastSynced;
                if (!equippedChanged && !timestampChanged) {
                    continue;
                }
                Long localWrite = lastLocalWriteAt.get(playerId);
                if (localWrite != null && System.currentTimeMillis() - localWrite < 2000L) {
                    continue;
                }
                PlayerData refreshed = loadReady(playerId);
                cache.put(playerId, refreshed);
                lastSyncedUpdatedAt.put(playerId, snapshot.updatedAt());
                if (equippedChanged) {
                    effectReconciler.accept(playerId);
                }
            }
        } catch (SQLException ex) {
            plugin.getLogger().warning("同步跨服称号穿戴状态失败: " + ex.getMessage());
        }
    }
    public CompletableFuture<TitleResult> unlock(UUID playerId, String rawTitleId, int days) {
        String titleId = TitleRegistry.normalizeId(rawTitleId);
        if (registry.get(titleId) == null || !registry.availableTo(titleId, playerId)) {
            return completed(TitleResult.TITLE_NOT_FOUND);
        }
        return withData(playerId).thenCompose(data -> {
            if (data == null) {
                return completed(TitleResult.DATABASE_ERROR);
            }
            if (data.unlocked().contains(titleId)) {
                return completed(TitleResult.ALREADY_UNLOCKED);
            }
            Player player = Bukkit.getPlayer(playerId);
            return fire(new TitleUnlockEvent(player, titleId)).thenCompose(allowed ->
                    !allowed ? completed(TitleResult.CANCELLED) : database(() -> {
                        if (!repository.unlock(playerId, titleId, days)) {
                            return TitleResult.ALREADY_UNLOCKED;
                        }
                        cache.computeIfPresent(playerId, (id, old) -> {
                            HashSet<String> ids = new HashSet<>(old.unlocked());
                            ids.add(titleId);
                            return old.withUnlocked(ids);
                        });
                        syncPublisher.accept(playerId);
                        rewardChecker.accept(playerId);
                        return TitleResult.SUCCESS;
                    }));
        });
    }
    public CompletableFuture<TitleResult> grant(UUID playerId, String rawTitleId, int days) {
        return grant(playerId, rawTitleId, days, false);
    }

    public CompletableFuture<TitleResult> grant(UUID playerId, String rawTitleId, int days, boolean force) {
        String titleId = TitleRegistry.normalizeId(rawTitleId);
        if (registry.get(titleId) == null || !registry.availableTo(titleId, playerId)) {
            return completed(TitleResult.TITLE_NOT_FOUND);
        }
        return withData(playerId).thenCompose(data -> {
            if (data == null) {
                return completed(TitleResult.DATABASE_ERROR);
            }
            return database(() -> {
                boolean extended = data.unlocked().contains(titleId)
                        ? (force ? repository.setExpiry(playerId, titleId, days)
                                 : repository.extend(playerId, titleId, days))
                        : repository.unlock(playerId, titleId, days);
                if (extended) {
                    cache.computeIfPresent(playerId, (id, old) -> {
                        HashSet<String> ids = new HashSet<>(old.unlocked());
                        ids.add(titleId);
                        return old.withUnlocked(ids);
                    });
                    syncPublisher.accept(playerId);
                    rewardChecker.accept(playerId);
                }
                return extended ? TitleResult.SUCCESS : TitleResult.DATABASE_ERROR;
            });
        });
    }
    public CompletableFuture<TitleResult> revoke(UUID playerId, String rawTitleId) {
        String titleId = TitleRegistry.normalizeId(rawTitleId);
        if (registry.get(titleId) == null || !registry.availableTo(titleId, playerId)) {
            return completed(TitleResult.TITLE_NOT_FOUND);
        }
        return withData(playerId).thenCompose(data -> {
            if (data == null) {
                return completed(TitleResult.DATABASE_ERROR);
            }
            if (!data.unlocked().contains(titleId)) {
                return completed(TitleResult.NOT_UNLOCKED);
            }
            return fire(new TitleRevokeEvent(playerId, titleId)).thenCompose(allowed ->
                    !allowed ? completed(TitleResult.CANCELLED) : database(() -> {
                        if (!repository.revoke(playerId, titleId)) {
                            return TitleResult.NOT_UNLOCKED;
                        }
                        cache.computeIfPresent(playerId, (id, old) -> {
                            HashSet<String> ids = new HashSet<>(old.unlocked());
                            ids.remove(titleId);
                            return new PlayerData(id, ids, titleId.equals(old.equippedId()) ? null : old.equippedId());
                        });
                        syncPublisher.accept(playerId);
                        effectReconciler.accept(playerId);
                        return TitleResult.SUCCESS;
                    }));
        });
    }
    public CompletableFuture<TitleResult> equip(UUID playerId, String rawTitleId) {
        String titleId = TitleRegistry.normalizeId(rawTitleId);
        if (registry.get(titleId) == null || !registry.availableTo(titleId, playerId)) {
            return completed(TitleResult.TITLE_NOT_FOUND);
        }
        int cooldownSeconds = plugin.getConfig().getInt("display.toggles-cooldown", 0);
        if (cooldownSeconds > 0) {
            long now = System.currentTimeMillis();
            Long last = lastEquipAt.get(playerId);
            if (last != null && now - last < cooldownSeconds * 1000L) {
                long remaining = (cooldownSeconds * 1000L - (now - last) + 999L) / 1000L;
                Player cooldownPlayer = Bukkit.getPlayer(playerId);
                if (cooldownPlayer != null && cooldownPlayer.isOnline()) {
                    plugin.messages().send(cooldownPlayer, "cooldown",
                            com.dracave.title.config.Messages.text("seconds", Long.toString(remaining)));
                }
                return completed(TitleResult.COOLDOWN);
            }
        }
        return withData(playerId).thenCompose(data -> {
            if (data == null) {
                return completed(TitleResult.DATABASE_ERROR);
            }
            if (!data.unlocked().contains(titleId)) {
                return completed(TitleResult.NOT_UNLOCKED);
            }
            if (titleId.equals(data.equippedId())) {
                return completed(TitleResult.ALREADY_EQUIPPED);
            }
            Player player = Bukkit.getPlayer(playerId);
            return fire(new TitleEquipEvent(player, data.equippedId(), titleId)).thenCompose(allowed ->
                    !allowed ? completed(TitleResult.CANCELLED) : database(() -> {
                        if (!repository.equip(playerId, titleId)) {
                            return TitleResult.NOT_UNLOCKED;
                        }
                        cache.computeIfPresent(playerId, (id, old) -> old.withEquipped(titleId));
                        lastEquipAt.put(playerId, System.currentTimeMillis());
                        lastLocalWriteAt.put(playerId, System.currentTimeMillis());
                        syncPublisher.accept(playerId);
                        effectReconciler.accept(playerId);
                        return TitleResult.SUCCESS;
                    }));
        });
    }
    public CompletableFuture<TitleResult> clear(UUID playerId) {
        return withData(playerId).thenCompose(data -> {
            if (data == null) {
                return completed(TitleResult.DATABASE_ERROR);
            }
            if (data.equippedId() == null) {
                return completed(TitleResult.SUCCESS);
            }
            Player player = Bukkit.getPlayer(playerId);
            return fire(new TitleUnequipEvent(player, data.equippedId())).thenCompose(allowed ->
                    !allowed ? completed(TitleResult.CANCELLED) : database(() -> {
                        repository.equip(playerId, null);
                        cache.computeIfPresent(playerId, (id, old) -> old.withEquipped(null));
                        lastLocalWriteAt.put(playerId, System.currentTimeMillis());
                        syncPublisher.accept(playerId);
                        effectReconciler.accept(playerId);
                        return TitleResult.SUCCESS;
                    }));
        });
    }
    public TitleDefinition equipped(UUID playerId) {
        PlayerData data = cache.get(playerId);
        return data == null ? null : registry.get(data.equippedId());
    }
    public void cacheUnlock(UUID playerId, String titleId) {
        cache.computeIfPresent(playerId, (id, old) -> {
            HashSet<String> ids = new HashSet<>(old.unlocked());
            ids.add(titleId);
            return old.withUnlocked(ids);
        });
        syncPublisher.accept(playerId);
        rewardChecker.accept(playerId);
    }
    public void removeCachedTitle(UUID playerId, String titleId) {
        cache.computeIfPresent(playerId, (id, old) -> {
            HashSet<String> ids = new HashSet<>(old.unlocked());
            ids.remove(titleId);
            return new PlayerData(id, ids, titleId.equals(old.equippedId()) ? null : old.equippedId());
        });
        syncPublisher.accept(playerId);
        effectReconciler.accept(playerId);
    }
    public void removeCachedTitleFromAll(String titleId) {
        cache.keySet().forEach(playerId -> removeCachedTitle(playerId, titleId));
    }
    public void purgeExpired(UUID playerId) {
        PlayerData data = cache.get(playerId);
        if (data == null || data.unlocked().isEmpty()) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                Map<String, Long> expired = repository.purgeExpired(playerId);
                if (expired.isEmpty()) {
                    return;
                }
                cache.computeIfPresent(playerId, (id, old) -> {
                    HashSet<String> ids = new HashSet<>(old.unlocked());
                    ids.removeAll(expired.keySet());
                    String equipped = old.equippedId();
                    if (equipped != null && expired.containsKey(equipped)) {
                        equipped = null;
                    }
                    return new PlayerData(id, ids, equipped);
                });
                syncPublisher.accept(playerId);
                effectReconciler.accept(playerId);
                Player player = Bukkit.getPlayer(playerId);
                if (player != null && player.isOnline()) {
                    for (String titleId : expired.keySet()) {
                        TitleDefinition title = registry.get(titleId);
                        if (title != null) {
                            plugin.messages().send(player, "overdue",
                                    com.dracave.title.config.Messages.parsed("title", renderMini(title)));
                        }
                    }
                }
            } catch (SQLException ex) {
                plugin.getLogger().warning("清理过期称号失败 " + playerId + ": " + ex.getMessage());
            }
        }, databaseExecutor);
    }
    private String renderMini(TitleDefinition title) {
        return com.dracave.title.render.TitleRenderer.miniMessage(title, System.currentTimeMillis());
    }
    private CompletableFuture<PlayerData> withData(UUID playerId) {
        PlayerData data = cache.get(playerId);
        return data == null ? load(playerId) : CompletableFuture.completedFuture(data);
    }
    private PlayerData loadReady(UUID playerId) throws SQLException {
        PlayerData loaded = repository.load(playerId);
        HashSet<String> defaults = new HashSet<>(registry.defaultIds());
        defaults.removeAll(loaded.unlocked());
        for (String titleId : defaults) {
            repository.unlock(playerId, titleId, 0);
        }
        HashSet<String> all = new HashSet<>(loaded.unlocked());
        all.addAll(defaults);
        return new PlayerData(playerId, all, loaded.equippedId(), loaded.expirations());
    }
    private CompletableFuture<Boolean> fire(Cancellable event) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        Runnable action = () -> {
            Bukkit.getPluginManager().callEvent((Event) event);
            future.complete(!event.isCancelled());
        };
        if (Bukkit.isPrimaryThread()) {
            action.run();
        } else {
            SchedulerUtil.runTask(plugin, action);
        }
        return future;
    }
    private CompletableFuture<TitleResult> database(SqlOperation operation) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return operation.run();
            } catch (SQLException ex) {
                plugin.getLogger().severe("称号数据库操作失败: " + ex.getMessage());
                return TitleResult.DATABASE_ERROR;
            }
        }, databaseExecutor);
    }
    private static CompletableFuture<TitleResult> completed(TitleResult result) {
        return CompletableFuture.completedFuture(result);
    }
    public void close() {
        databaseExecutor.shutdown();
        try {
            if (!databaseExecutor.awaitTermination(10L, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("数据库任务未能在 10 秒内排空");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
    @FunctionalInterface
    private interface SqlOperation {
        TitleResult run() throws SQLException;
    }
}
