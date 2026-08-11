package com.dracave.title.api;
import com.dracave.title.config.TitleRegistry;
import com.dracave.title.model.CustomTitle;
import com.dracave.title.model.CustomTitleDraft;
import com.dracave.title.model.PlayerData;
import com.dracave.title.model.TitleDefinition;
import com.dracave.title.render.TitleRenderer;
import com.dracave.title.service.CustomTitleService;
import com.dracave.title.service.TitlePurchaseService;
import com.dracave.title.service.TitleService;
import com.dracave.title.storage.CoinRepository;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
public final class DraCaveTitleAPI {
    private static volatile TitleService service;
    private static volatile TitleRegistry registry;
    private static volatile TitlePurchaseService purchases;
    private static volatile CustomTitleService customTitles;
    private static volatile CoinRepository coinRepository;
    private DraCaveTitleAPI() {
    }
    public static void bind(TitleService titleService, TitleRegistry titleRegistry, TitlePurchaseService purchaseService) {
        service = titleService;
        registry = titleRegistry;
        purchases = purchaseService;
    }
    public static void bindCoin(CoinRepository repo) {
        coinRepository = repo;
    }
    public static void bindCustomTitles(CustomTitleService customTitleService) {
        customTitles = customTitleService;
    }
    public static void unbind() {
        service = null;
        registry = null;
        purchases = null;
        customTitles = null;
        coinRepository = null;
    }
    public static Optional<TitleDefinition> getTitle(String id) {
        TitleRegistry current = registry;
        return Optional.ofNullable(current == null ? null : current.get(id));
    }
    public static List<TitleDefinition> getTitles() {
        TitleRegistry current = registry;
        return current == null ? List.of() : current.all();
    }
    public static Set<String> getUnlockedTitles(UUID playerId) {
        TitleService current = service;
        PlayerData data = current == null ? null : current.getCached(playerId);
        return data == null ? Set.of() : data.unlocked();
    }
    public static boolean isUnlocked(UUID playerId, String titleId) {
        return getUnlockedTitles(playerId).contains(TitleRegistry.normalizeId(titleId));
    }
    public static Optional<TitleDefinition> getEquippedTitle(UUID playerId) {
        TitleService current = service;
        return Optional.ofNullable(current == null ? null : current.equipped(playerId));
    }
    public static String getMiniMessage(UUID playerId) {
        return getEquippedTitle(playerId).map(title -> TitleRenderer.miniMessage(title, System.currentTimeMillis())).orElse("");
    }
    public static String getPlainText(UUID playerId) {
        return getEquippedTitle(playerId).map(title -> TitleRenderer.plain(title, System.currentTimeMillis())).orElse("");
    }
    public static String getLegacyAmpersand(UUID playerId) {
        return getEquippedTitle(playerId).map(title -> TitleRenderer.legacyAmpersand(title, System.currentTimeMillis())).orElse("");
    }
    public static String getLegacySection(UUID playerId) {
        return getEquippedTitle(playerId).map(title -> TitleRenderer.legacySection(title, System.currentTimeMillis())).orElse("");
    }
    public static Component getComponent(UUID playerId) {
        return getEquippedTitle(playerId).map(title -> TitleRenderer.component(title, System.currentTimeMillis())).orElse(Component.empty());
    }
    public static CompletableFuture<TitleResult> unlock(UUID playerId, String titleId) {
        TitleService current = service;
        return current == null ? unavailable() : current.unlock(playerId, titleId, 0);
    }
    public static CompletableFuture<TitleResult> unlock(UUID playerId, String titleId, int days) {
        TitleService current = service;
        return current == null ? unavailable() : current.unlock(playerId, titleId, days);
    }
    public static CompletableFuture<TitleResult> revoke(UUID playerId, String titleId) {
        TitleService current = service;
        return current == null ? unavailable() : current.revoke(playerId, titleId);
    }
    public static CompletableFuture<TitleResult> equip(UUID playerId, String titleId) {
        TitleService current = service;
        return current == null ? unavailable() : current.equip(playerId, titleId);
    }
    public static CompletableFuture<TitleResult> unequip(UUID playerId) {
        TitleService current = service;
        return current == null ? unavailable() : current.clear(playerId);
    }
    public static CompletableFuture<PurchaseResult> purchase(UUID playerId, String titleId) {
        TitlePurchaseService current = purchases;
        return current == null
                ? CompletableFuture.completedFuture(new PurchaseResult(PurchaseStatus.SERVICE_UNAVAILABLE, null, titleId,
                "", BigDecimal.ZERO, "unavailable"))
                : current.purchase(playerId, titleId);
    }
    public static boolean registerRuntimeTitle(TitleDefinition title) {
        TitleRegistry current = registry;
        return current != null && current.register(title);
    }
    public static boolean unregisterRuntimeTitle(String titleId) {
        TitleRegistry current = registry;
        return current != null && current.unregister(titleId);
    }
    public static List<CustomTitle> getCustomTitles(UUID ownerId) {
        CustomTitleService current = customTitles;
        return current == null ? List.of() : current.ownedBy(ownerId);
    }
    public static CompletableFuture<CustomTitleService.Result> createCustomTitle(Player player, CustomTitleDraft draft) {
        CustomTitleService current = customTitles;
        return current == null ? CompletableFuture.completedFuture(CustomTitleService.Result.DISABLED) : current.create(player, draft);
    }
    public static CompletableFuture<CustomTitleService.Result> updateCustomTitle(Player player, String id, CustomTitleDraft draft) {
        CustomTitleService current = customTitles;
        return current == null ? CompletableFuture.completedFuture(CustomTitleService.Result.DISABLED) : current.update(player, id, draft);
    }
    public static CompletableFuture<CustomTitleService.Result> deleteCustomTitle(Player player, String id) {
        CustomTitleService current = customTitles;
        return current == null ? CompletableFuture.completedFuture(CustomTitleService.Result.DISABLED) : current.delete(player, id);
    }
    public static long getCoinBalance(UUID playerId) {
        CoinRepository repo = coinRepository;
        if (repo == null) {
            return 0L;
        }
        try {
            return repo.balance(playerId);
        } catch (SQLException ex) {
            return 0L;
        }
    }
    private static CompletableFuture<TitleResult> unavailable() {
        return CompletableFuture.completedFuture(TitleResult.SERVICE_UNAVAILABLE);
    }
}
