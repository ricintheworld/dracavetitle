package com.dracave.title.service;
import com.dracave.title.DraCaveTitlePlugin;
import com.dracave.title.api.PurchaseResult;
import com.dracave.title.api.PurchaseStatus;
import com.dracave.title.api.event.TitlePurchaseEvent;
import com.dracave.title.api.event.TitlePurchasedEvent;
import com.dracave.title.api.event.TitleUnlockEvent;
import com.dracave.title.config.TitleRegistry;
import com.dracave.title.currency.CurrencyProvider;
import com.dracave.title.currency.CurrencyRegistry;
import com.dracave.title.model.PlayerData;
import com.dracave.title.model.TitleDefinition;
import com.dracave.title.model.TitlePurchaseOffer;
import com.dracave.title.storage.PurchaseRecord;
import com.dracave.title.storage.TitleRepository;
import com.dracave.title.util.SchedulerUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
public final class TitlePurchaseService {
    private final DraCaveTitlePlugin plugin;
    private final TitleRegistry titles;
    private final TitleService titleService;
    private final TitleRepository repository;
    private final CurrencyRegistry currencies;
    private final Set<String> active = ConcurrentHashMap.newKeySet();
    // 逐玩家锁：保证「余额检查 + 扣款」原子，杜绝并发购买时的 TOCTOU 超额扣币
    private final ConcurrentHashMap<UUID, Object> chargeLocks = new ConcurrentHashMap<>();
    private final long serviceStartedAt = System.currentTimeMillis();
    private final ExecutorService sqlExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "DraCaveTitle-Purchase");
        thread.setDaemon(true);
        return thread;
    });
    private volatile boolean closed;
    public TitlePurchaseService(DraCaveTitlePlugin plugin, TitleRegistry titles, TitleService titleService,
                                TitleRepository repository, CurrencyRegistry currencies) {
        this.plugin = plugin;
        this.titles = titles;
        this.titleService = titleService;
        this.repository = repository;
        this.currencies = currencies;
    }
    public CompletableFuture<PurchaseResult> purchase(UUID playerId, String rawTitleId) {
        if (!Bukkit.isPrimaryThread()) {
            CompletableFuture<PurchaseResult> future = new CompletableFuture<>();
            SchedulerUtil.runTask(plugin, () -> purchase(playerId, rawTitleId).whenComplete((result, error) -> {
                if (error == null) {
                    future.complete(result);
                } else {
                    future.completeExceptionally(error);
                }
            }));
            return future;
        }
        String titleId = TitleRegistry.normalizeId(rawTitleId);
        TitleDefinition title = titles.get(titleId);
        TitlePurchaseOffer offer = title == null ? null : title.purchaseOffer();
        UUID operationId = UUID.randomUUID();
        if (closed || !plugin.getConfig().getBoolean("purchase.enabled", true)) {
            return done(PurchaseStatus.SERVICE_UNAVAILABLE, operationId, titleId, offer, "disabled");
        }
        if (title == null) {
            return done(PurchaseStatus.TITLE_NOT_FOUND, operationId, titleId, null, "unknown title");
        }
        if (offer == null) {
            return done(PurchaseStatus.NOT_PURCHASABLE, operationId, titleId, null, "no offer");
        }
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return done(PurchaseStatus.PLAYER_OFFLINE, operationId, titleId, offer, "offline");
        }
        if (!title.permission().isEmpty() && !player.hasPermission(title.permission())) {
            return done(PurchaseStatus.PERMISSION_DENIED, operationId, titleId, offer, title.permission());
        }
        PlayerData data = titleService.getCached(playerId);
        if (data == null) {
            return done(PurchaseStatus.SERVICE_UNAVAILABLE, operationId, titleId, offer, "data not loaded");
        }
        if (data.unlocked().contains(titleId)) {
            return done(PurchaseStatus.ALREADY_UNLOCKED, operationId, titleId, offer, "owned");
        }
        CurrencyProvider currency = providerFor(offer);
        if (currency == null || !currency.available()) {
            return done(PurchaseStatus.CURRENCY_UNAVAILABLE, operationId, titleId, offer, "provider unavailable");
        }
        String key = playerId + ":" + titleId;
        if (!active.add(key)) {
            return done(PurchaseStatus.PURCHASE_IN_PROGRESS, operationId, titleId, offer, "duplicate");
        }
        CompletableFuture<PurchaseResult> result = fire(new TitlePurchaseEvent(player, titleId, offer.currency(), offer.price(), operationId))
                .thenCompose(allowed -> allowed
                        ? fire(new TitleUnlockEvent(player, titleId))
                        : CompletableFuture.completedFuture(false))
                .thenCompose(allowed -> allowed
                        ? executePurchase(playerId, title, offer, operationId, currency)
                        : done(PurchaseStatus.CANCELLED, operationId, titleId, offer, "event cancelled"))
                .exceptionally(exception -> {
                    plugin.getLogger().severe("购买异常 " + operationId + ": " + exception.getMessage());
                    return result(PurchaseStatus.DATABASE_ERROR, operationId, titleId, offer, exception.getMessage());
                });
        return result.whenComplete((ignored, exception) -> active.remove(key));
    }
    private CompletableFuture<PurchaseResult> executePurchase(UUID playerId, TitleDefinition title,
                                                              TitlePurchaseOffer offer, UUID operationId, CurrencyProvider currency) {
        String titleId = title.id();
        return sql(() -> repository.reservePurchase(playerId, titleId, operationId, offer.dbCurrency(), offer.price()))
                .thenCompose(reserved -> !reserved
                        ? done(PurchaseStatus.ALREADY_UNLOCKED, operationId, titleId, offer, "already reserved or purchased")
                        : sql(() -> repository.transitionPurchase(operationId, "PENDING", "CHARGING", null))
                        .thenCompose(marked -> marked
                                ? main(() -> charge(currency, playerId, offer.price()))
                                : CompletableFuture.completedFuture(ChargeResult.FAILED))
                        .thenCompose(charge -> {
                            if (charge == ChargeResult.INSUFFICIENT) {
                                return sql(() -> repository.transitionPurchase(operationId, "CHARGING", "FAILED", "insufficient funds"))
                                        .thenCompose(ignored -> done(PurchaseStatus.INSUFFICIENT_FUNDS, operationId, titleId, offer, "insufficient funds"));
                            }
                            if (charge != ChargeResult.SUCCESS) {
                                return sql(() -> repository.transitionPurchase(operationId, "CHARGING", "FAILED", "payment failed"))
                                        .thenCompose(ignored -> done(PurchaseStatus.PAYMENT_FAILED, operationId, titleId, offer, "withdraw failed"));
                            }
                            return sql(() -> repository.transitionPurchase(operationId, "CHARGING", "CHARGED", null))
                                    .handle((marked, error) -> error == null && marked)
                                    .thenCompose(marked -> marked
                                            ? sql(() -> repository.completePurchase(playerId, titleId, operationId))
                                            .handle((completed, error) -> error == null && completed)
                                            .thenCompose(completed -> completed
                                                    ? complete(playerId, title, offer, operationId)
                                                    : refund(currency, playerId, titleId, offer, operationId, "CHARGED"))
                                            : refund(currency, playerId, titleId, offer, operationId, "CHARGING"));
                        }));
    }
    public BigDecimal balance(UUID playerId, TitlePurchaseOffer offer) {
        CurrencyProvider provider = providerFor(offer);
        return provider != null && provider.available() ? provider.balance(playerId) : null;
    }
    public boolean currencyAvailable(TitlePurchaseOffer offer) {
        CurrencyProvider provider = providerFor(offer);
        return provider != null && provider.available();
    }
    private CurrencyProvider providerFor(TitlePurchaseOffer offer) {
        if (offer.currency() == com.dracave.title.model.CurrencyType.ITEM) {
            return new com.dracave.title.currency.ItemCurrencyProvider(offer.itemMaterial());
        }
        return currencies.get(offer.currency());
    }
    private ChargeResult charge(CurrencyProvider provider, UUID playerId, BigDecimal amount) {
        // 同一玩家的「余额检查 + 扣款」必须原子：扣款底层（CoinRepository.subtract）自身已原子，
        // 这里再用逐玩家锁彻底堵住「检查够 → 另一笔先把钱扣光 → 本笔仍以为够」的窗口。
        Object lock = chargeLocks.computeIfAbsent(playerId, k -> new Object());
        try {
            synchronized (lock) {
                if (provider.balance(playerId).compareTo(amount) < 0) {
                    return ChargeResult.INSUFFICIENT;
                }
                return provider.withdraw(playerId, amount) ? ChargeResult.SUCCESS : ChargeResult.FAILED;
            }
        } finally {
            chargeLocks.remove(playerId, lock);
        }
    }
    private CompletableFuture<PurchaseResult> complete(UUID playerId, TitleDefinition title, TitlePurchaseOffer offer, UUID operationId) {
        titleService.cacheUnlock(playerId, title.id());
        CompletableFuture<PurchaseResult> future = main(() -> {
            Bukkit.getPluginManager().callEvent(new TitlePurchasedEvent(
                    Bukkit.getPlayer(playerId), title.id(), offer.currency(), offer.price(), operationId));
            return result(PurchaseStatus.SUCCESS, operationId, title.id(), offer, "completed");
        });
        if (plugin.getConfig().getBoolean("purchase.auto-equip", true)) {
            future = future.thenCompose(success -> titleService.equip(playerId, title.id()).thenApply(equip -> success));
        }
        return future;
    }
    private CompletableFuture<PurchaseResult> refund(CurrencyProvider provider, UUID playerId, String titleId,
                                                     TitlePurchaseOffer offer, UUID operationId, String expectedState) {
        return main(() -> provider.refund(playerId, offer.price()))
                .thenCompose(refunded -> sql(() -> repository.transitionPurchase(
                        operationId, expectedState, refunded ? "REFUNDED" : "REFUND_PENDING", "unlock persistence failed"))
                        .exceptionally(error -> false)
                        .thenCompose(recorded -> {
                            if (refunded) {
                                sql(() -> repository.markRefunded(operationId, true)).exceptionally(error -> false);
                            }
                            if (!recorded) {
                                plugin.getLogger().severe("购买退款状态未能落库，需人工核对: " + operationId);
                            }
                            PurchaseStatus status = refunded && recorded ? PurchaseStatus.REFUNDED : PurchaseStatus.REFUND_PENDING;
                            return done(status, operationId, titleId, offer, "unlock persistence failed");
                        }));
    }
    public void recoverInterruptedPurchases() {
        CompletableFuture.runAsync(() -> {
            try {
                for (PurchaseRecord record : repository.findStalePurchases(serviceStartedAt + 1L)) {
                    switch (record.state()) {
                        case "PENDING" -> repository.forcePurchaseState(record.operationId(), "FAILED", "interrupted before charge");
                        case "CHARGING" -> {
                            repository.forcePurchaseState(record.operationId(), "UNKNOWN", "charge result unknown after interruption");
                            warnRecovery(record, "扣款结果不确定，禁止自动重试");
                        }
                        case "CHARGED" -> {
                            repository.forcePurchaseState(record.operationId(), "REFUND_PENDING", "charged before interruption");
                            warnRecovery(record, "已记录扣款成功但未完成解锁，需要人工退款核对");
                        }
                        default -> {
                        }
                    }
                }
            } catch (SQLException ex) {
                plugin.getLogger().severe("扫描中断购买失败: " + ex.getMessage());
            }
        }, sqlExecutor);
    }
    private void warnRecovery(PurchaseRecord record, String message) {
        plugin.getLogger().severe("购买恢复警告 operation=" + record.operationId()
                + " player=" + record.playerId() + " title=" + record.titleId()
                + " currency=" + record.currency() + " amount=" + record.amount() + " - " + message);
    }
    private CompletableFuture<Boolean> fire(Cancellable event) {
        return main(() -> {
            Bukkit.getPluginManager().callEvent((Event) event);
            return !event.isCancelled();
        });
    }
    private <T> CompletableFuture<T> main(Supplier<T> supplier) {
        if (Bukkit.isPrimaryThread()) {
            try {
                return CompletableFuture.completedFuture(supplier.get());
            } catch (RuntimeException ex) {
                return CompletableFuture.failedFuture(ex);
            }
        }
        CompletableFuture<T> future = new CompletableFuture<>();
        SchedulerUtil.runTask(plugin, () -> {
            try {
                future.complete(supplier.get());
            } catch (RuntimeException ex) {
                future.completeExceptionally(ex);
            }
        });
        return future;
    }
    private CompletableFuture<Boolean> sql(SqlBoolean operation) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return operation.run();
            } catch (SQLException ex) {
                throw new RuntimeException(ex);
            }
        }, sqlExecutor);
    }
    private static CompletableFuture<PurchaseResult> done(PurchaseStatus status, UUID operation, String title,
                                                          TitlePurchaseOffer offer, String detail) {
        return CompletableFuture.completedFuture(result(status, operation, title, offer, detail));
    }
    private static PurchaseResult result(PurchaseStatus status, UUID operation, String title,
                                         TitlePurchaseOffer offer, String detail) {
        return new PurchaseResult(status, operation, title,
                offer == null ? "" : offer.currency().id(),
                offer == null ? BigDecimal.ZERO : offer.price(),
                detail == null ? "" : detail);
    }
    public void close() {
        closed = true;
        sqlExecutor.shutdown();
        try {
            // 给在途的数据库步骤（reserve / transition / completePurchase / refund）留出更多时间落库，
            // 降低关服瞬间购买卡在中间态、下次启动需人工退款的概率。
            if (!sqlExecutor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)) {
                sqlExecutor.shutdownNow();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            sqlExecutor.shutdownNow();
        }
    }
    private enum ChargeResult {
        SUCCESS,
        INSUFFICIENT,
        FAILED
    }
    @FunctionalInterface
    private interface SqlBoolean {
        boolean run() throws SQLException;
    }
}
