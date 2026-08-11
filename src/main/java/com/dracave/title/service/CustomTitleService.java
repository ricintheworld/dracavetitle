package com.dracave.title.service;
import com.dracave.title.DraCaveTitlePlugin;
import com.dracave.title.api.event.CustomTitleCreateEvent;
import com.dracave.title.api.event.CustomTitleCreatedEvent;
import com.dracave.title.api.event.CustomTitleDeletedEvent;
import com.dracave.title.config.TitleRegistry;
import com.dracave.title.model.CustomTitle;
import com.dracave.title.model.CustomTitleDraft;
import com.dracave.title.model.CustomTitleType;
import com.dracave.title.model.TitleAnimation;
import com.dracave.title.model.TitleDefinition;
import com.dracave.title.storage.CustomTitleRepository;
import com.dracave.title.storage.QuotaRepository;
import com.dracave.title.util.ItemResolver;
import com.dracave.title.util.SchedulerUtil;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.permissions.PermissionAttachmentInfo;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.regex.Pattern;
public final class CustomTitleService {
    private static final Pattern COLOR = Pattern.compile("#[0-9A-Fa-f]{6}");
    private final DraCaveTitlePlugin plugin;
    private final CustomTitleRepository repository;
    private final QuotaRepository quotaRepository;
    private final TitleRegistry registry;
    private final TitleService titles;
    private final Map<String, CustomTitle> definitions = new ConcurrentHashMap<>();
    // 逐玩家锁：把「限额复核 + DB 写入 + publish」包成原子，杜绝并发创建绕过限额
    private final ConcurrentHashMap<UUID, Object> createLocks = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "DraCaveTitle-Custom");
        t.setDaemon(true);
        return t;
    });
    public CustomTitleService(DraCaveTitlePlugin plugin, CustomTitleRepository repository,
                              QuotaRepository quotaRepository, TitleRegistry registry, TitleService titles) {
        this.plugin = plugin;
        this.repository = repository;
        this.quotaRepository = quotaRepository;
        this.registry = registry;
        this.titles = titles;
    }
    public void loadAll() throws Exception {
        for (CustomTitle title : repository.loadActive()) {
            publish(title);
        }
    }
    public List<CustomTitle> ownedBy(UUID owner) {
        return definitions.values().stream()
                .filter(t -> t.ownerId().equals(owner))
                .sorted(Comparator.comparingLong(CustomTitle::createdAt))
                .toList();
    }
    public TitleDefinition rendered(String id) {
        return registry.get(id);
    }
    public int limit(Player player) {
        if (player.hasPermission("dracave.title.custom.limit.unlimited")) {
            return Integer.MAX_VALUE;
        }
        int max = 0;
        for (PermissionAttachmentInfo permission : player.getEffectivePermissions()) {
            String node = permission.getPermission();
            if (permission.getValue() && node.startsWith("dracave.title.custom.limit.")) {
                try {
                    max = Math.max(max, Integer.parseInt(node.substring("dracave.title.custom.limit.".length())));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        int adminQuota = 0;
        try {
            adminQuota = quotaRepository.quota(player.getUniqueId());
        } catch (Exception ignored) {
        }
        return Math.max(max, adminQuota);
    }
    public CompletableFuture<Result> create(Player player, CustomTitleDraft raw) {
        if (!Bukkit.isPrimaryThread()) {
            return onMainThread(() -> create(player, raw));
        }
        if (raw == null || raw.type() == null) {
            return done(Result.INVALID);
        }
        if (!plugin.getConfig().getBoolean("custom-titles.enabled", true)) {
            return done(Result.DISABLED);
        }
        boolean dynamic = raw.type().dynamic();
        if (!player.hasPermission(dynamic ? "dracave.title.custom.dynamic" : "dracave.title.custom.static")) {
            return done(Result.NO_PERMISSION);
        }
        UUID playerId = player.getUniqueId();
        int playerLimit = limit(player);
        if (ownedBy(playerId).size() >= playerLimit) {
            return done(Result.LIMIT_REACHED);
        }
        CustomTitleDraft draft;
        try {
            draft = validate(raw);
        } catch (IllegalArgumentException ex) {
            return done(Result.INVALID);
        }
        CustomTitleCreateEvent event = new CustomTitleCreateEvent(player, draft);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return done(Result.INVALID);
        }
        long now = System.currentTimeMillis();
        CustomTitle title = new CustomTitle(
                "custom_" + UUID.randomUUID().toString().replace("-", ""),
                playerId, draft.text(), draft.type(), draft.colors(), draft.frames(),
                draft.periodTicks(), draft.icon(), 1, now, now);
        return CompletableFuture.supplyAsync(() -> {
            // 在主线程已做过一次限额快检，这里在写入前用逐玩家锁再做权威复核 + 写入，
            // 即便将来执行线程变化也不会出现两个并发创建都通过检查而超额。
            Object lock = createLocks.computeIfAbsent(playerId, k -> new Object());
            try {
                synchronized (lock) {
                    if (ownedBy(playerId).size() >= playerLimit) {
                        return Result.LIMIT_REACHED;
                    }
                    try {
                        repository.create(title);
                    } catch (Exception ex) {
                        plugin.getLogger().severe("创建自定义称号失败: " + ex.getMessage());
                        return Result.DATABASE_ERROR;
                    }
                    publish(title);
                    titles.cacheUnlock(playerId, title.id());
                    scheduleEvent(() -> new CustomTitleCreatedEvent(title));
                    return Result.SUCCESS;
                }
            } finally {
                createLocks.remove(playerId, lock);
            }
        }, executor);
    }
    public CompletableFuture<Result> update(Player player, String id, CustomTitleDraft raw) {
        if (!Bukkit.isPrimaryThread()) {
            return onMainThread(() -> update(player, id, raw));
        }
        if (raw == null || raw.type() == null) {
            return done(Result.INVALID);
        }
        CustomTitle old = definitions.get(id);
        if (old == null || !old.ownerId().equals(player.getUniqueId())) {
            return done(Result.NOT_FOUND);
        }
        if (!player.hasPermission(raw.type().dynamic() ? "dracave.title.custom.dynamic" : "dracave.title.custom.static")) {
            return done(Result.NO_PERMISSION);
        }
        CustomTitleDraft draft;
        try {
            draft = validate(raw);
        } catch (IllegalArgumentException ex) {
            return done(Result.INVALID);
        }
        CustomTitle changed = new CustomTitle(old.id(), old.ownerId(), draft.text(), draft.type(), draft.colors(),
                draft.frames(), draft.periodTicks(), draft.icon(), old.revision() + 1, old.createdAt(), System.currentTimeMillis());
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!repository.update(changed, old.revision())) {
                    return Result.CONFLICT;
                }
                publish(changed);
                return Result.SUCCESS;
            } catch (Exception ex) {
                return Result.DATABASE_ERROR;
            }
        }, executor);
    }
    public CompletableFuture<Result> delete(Player player, String id) {
        if (!Bukkit.isPrimaryThread()) {
            return onMainThread(() -> delete(player, id));
        }
        UUID playerId = player.getUniqueId();
        CustomTitle title = definitions.get(id);
        if (title == null || !title.ownerId().equals(playerId)) {
            return done(Result.NOT_FOUND);
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!repository.delete(playerId, id)) {
                    return Result.NOT_FOUND;
                }
                definitions.remove(id);
                registry.unregisterCustom(id);
                titles.removeCachedTitleFromAll(id);
                scheduleEvent(() -> new CustomTitleDeletedEvent(playerId, id));
                return Result.SUCCESS;
            } catch (Exception ex) {
                return Result.DATABASE_ERROR;
            }
        }, executor);
    }
    private CustomTitleDraft validate(CustomTitleDraft input) {
        String text = clean(input.text());
        List<String> frames = input.frames().stream().map(this::clean).toList();
        List<String> colors = input.colors().stream().map(c -> {
            if (!COLOR.matcher(c).matches()) {
                throw new IllegalArgumentException("invalid color");
            }
            return c.toUpperCase(Locale.ROOT);
        }).toList();
        int maxColors = plugin.getConfig().getInt("custom-titles.dynamic.max-colors", 5);
        if (colors.size() > maxColors) {
            throw new IllegalArgumentException("too many colors");
        }
        if ((input.type() == CustomTitleType.FLOWING_GRADIENT || input.type() == CustomTitleType.FLASHING_COLORS) && colors.size() < 2) {
            throw new IllegalArgumentException("too few colors");
        }
        if (input.type() == CustomTitleType.TEXT_FRAMES) {
            int maxFrames = plugin.getConfig().getInt("custom-titles.dynamic.max-text-frames", 10);
            if (frames.size() < 2 || frames.size() > maxFrames) {
                throw new IllegalArgumentException("invalid frames");
            }
        }
        int period = input.periodTicks();
        if (input.type().dynamic()) {
            int min = plugin.getConfig().getInt("custom-titles.dynamic.min-period-ticks", 5);
            int max = plugin.getConfig().getInt("custom-titles.dynamic.max-period-ticks", 200);
            if (period < min || period > max) {
                throw new IllegalArgumentException("invalid period");
            }
        }
        String icon = ItemResolver.isValid(input.icon()) ? input.icon() : "NAME_TAG";
        return new CustomTitleDraft(text, input.type(), colors, frames, period, icon);
    }
    private String clean(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("missing text");
        }
        String text = java.text.Normalizer.normalize(raw.trim(), java.text.Normalizer.Form.NFC);
        int max = plugin.getConfig().getInt("custom-titles.text.max-length", 16);
        if (text.isEmpty() || text.codePointCount(0, text.length()) > max || text.matches(".*[<>§\\p{Cntrl}\\p{Cf}].*")) {
            throw new IllegalArgumentException("invalid text");
        }
        for (String word : plugin.getConfig().getStringList("custom-titles.filter.blocked-words")) {
            if (!word.isBlank() && text.toLowerCase(Locale.ROOT).contains(word.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("blocked text");
            }
        }
        return text;
    }
    private void publish(CustomTitle custom) {
        definitions.put(custom.id(), custom);
        MiniMessage mini = MiniMessage.miniMessage();
        String escaped = mini.escapeTags(custom.text());
        List<String> escapedFrames = custom.frames().stream().map(mini::escapeTags).toList();
        TitleAnimation animation = switch (custom.type()) {
            case STATIC -> null;
            case FLOWING_GRADIENT -> new TitleAnimation(TitleAnimation.Type.FLOWING_GRADIENT, custom.colors(), List.of(), custom.periodTicks());
            case TEXT_FRAMES -> new TitleAnimation(TitleAnimation.Type.TEXT_FRAMES, List.of(), escapedFrames, custom.periodTicks());
            case RAINBOW -> TitleAnimation.rainbow(custom.periodTicks());
            case FLASHING_COLORS -> new TitleAnimation(TitleAnimation.Type.FLASHING_COLORS, custom.colors(), List.of(), custom.periodTicks());
        };
        String display = custom.type() == CustomTitleType.STATIC && !custom.colors().isEmpty()
                ? "<" + custom.colors().get(0) + ">" + escaped + "</" + custom.colors().get(0) + ">"
                : escaped;
        registry.registerCustom(new TitleDefinition(custom.id(), display, List.of("<gray>玩家自定义称号"),
                custom.icon(), 0, false, "", animation), custom.ownerId());
    }
    private <T> CompletableFuture<T> onMainThread(Supplier<CompletableFuture<T>> action) {
        CompletableFuture<T> result = new CompletableFuture<>();
        SchedulerUtil.runTask(plugin, () -> {
            try {
                action.get().whenComplete((value, error) -> {
                    if (error == null) {
                        result.complete(value);
                    } else {
                        result.completeExceptionally(error);
                    }
                });
            } catch (RuntimeException ex) {
                result.completeExceptionally(ex);
            }
        });
        return result;
    }
    private void scheduleEvent(java.util.function.Supplier<Event> eventSupplier) {
        try {
            // 必须在主线程派发（callEvent 与事件构造都在主线程），否则监听者触碰 Bukkit API 会触发
            // async-only 错误。原先 runTaskAsynchronously 会把事件推到异步线程，破坏事件契约。
            SchedulerUtil.runTask(plugin, () -> {
                Event event = eventSupplier.get();
                Bukkit.getPluginManager().callEvent(event);
            });
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("自定义称号事件投递失败: " + ex.getMessage());
        }
    }
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10L, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("自定义称号数据库任务未能在 10 秒内排空");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
    private static CompletableFuture<Result> done(Result result) {
        return CompletableFuture.completedFuture(result);
    }
    public enum Result {
        SUCCESS,
        DISABLED,
        NO_PERMISSION,
        LIMIT_REACHED,
        INVALID,
        NOT_FOUND,
        CONFLICT,
        DATABASE_ERROR
    }
}
