package com.dracave.title.service;
import com.dracave.title.DraCaveTitlePlugin;
import com.dracave.title.config.TagFileService;
import com.dracave.title.config.TitleRegistry;
import com.dracave.title.config.TitleYamlParser;
import com.dracave.title.config.TitlesYamlWriter;
import com.dracave.title.model.TitleDefinition;
import com.dracave.title.storage.TitleDefinitionRepository;
import com.dracave.title.util.SchedulerUtil;
import org.bukkit.Bukkit;
import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
public final class TitleDefinitionService implements AutoCloseable {
    private final DraCaveTitlePlugin plugin;
    private final TitleDefinitionRepository repository;
    private final TitleRegistry registry;
    private final TitleYamlParser yamlParser;
    private final TitlesYamlWriter yamlWriter;
    private final TagFileService tagFileService;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "DraCaveTitle-Definitions");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean refreshing = new AtomicBoolean();
    private volatile Runnable syncPublisher = () -> {
    };
    public TitleDefinitionService(DraCaveTitlePlugin plugin, TitleDefinitionRepository repository,
                                  TitleRegistry registry, TagFileService tagFileService) {
        this.plugin = plugin;
        this.repository = repository;
        this.registry = registry;
        this.yamlParser = new TitleYamlParser();
        this.yamlWriter = new TitlesYamlWriter();
        this.tagFileService = tagFileService;
    }
    public List<TitleDefinition> loadFromTags() {
        return tagFileService.loadAll();
    }
    public CompletableFuture<UploadResult> checkUpload() {
        return CompletableFuture.supplyAsync(() -> {
            TitleYamlParser.ParseResult parsed = yamlParser.parse(new File(plugin.getDataFolder(), "titles.yml"));
            return new UploadResult(parsed.definitions().size(), 0, 0, parsed.errors());
        }, executor);
    }
    public CompletableFuture<SyncResult> sync() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<TitleDefinition> loaded = repository.loadAll();
                persistFiles(loaded);
                return new SyncWork(loaded, null);
            } catch (Exception ex) {
                plugin.getLogger().severe("同步称号定义失败: " + ex.getMessage());
                return new SyncWork(null, List.of("数据库读取失败: " + ex.getMessage()));
            }
        }, executor).thenCompose(work -> {
            if (work.errors != null) {
                return CompletableFuture.completedFuture(new SyncResult(0, work.errors));
            }
            return replaceOnMainThread(work.definitions).thenApply(ignored -> {
                syncPublisher.run();
                return new SyncResult(work.definitions.size(), List.of());
            });
        });
    }
    public CompletableFuture<UploadResult> upload() {
        return CompletableFuture.supplyAsync(() -> {
            TitleYamlParser.ParseResult parsed = yamlParser.parse(new File(plugin.getDataFolder(), "titles.yml"));
            if (!parsed.valid()) {
                return new UploadWork(new UploadResult(parsed.definitions().size(), 0, 0, parsed.errors()), null);
            }
            try {
                TitleDefinitionRepository.UpsertResult result = repository.upsertAll(parsed.definitions());
                persistFiles(result.definitions());
                return new UploadWork(new UploadResult(parsed.definitions().size(), result.inserted(), result.updated(), List.of()),
                        result.definitions());
            } catch (Exception ex) {
                plugin.getLogger().severe("上传称号定义失败: " + ex.getMessage());
                return new UploadWork(new UploadResult(parsed.definitions().size(), 0, 0,
                        List.of("数据库写入失败: " + ex.getMessage())), null);
            }
        }, executor).thenCompose(work -> work.definitions == null
                ? CompletableFuture.completedFuture(work.result)
                : replaceOnMainThread(work.definitions).thenApply(ignored -> {
                    syncPublisher.run();
                    return work.result;
                }));
    }
    public CompletableFuture<Boolean> update(TitleDefinition definition, int expectedRevision) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return !repository.update(definition, expectedRevision) ? null : repository.loadAll();
            } catch (Exception ex) {
                throw new IllegalStateException("保存称号定义失败", ex);
            }
        }, executor).thenCompose(definitions -> definitions == null
                ? CompletableFuture.completedFuture(false)
                : replaceOnMainThread(definitions).thenApply(ignored -> {
                    persistFiles(definitions);
                    syncPublisher.run();
                    return true;
                }));
    }
    public CompletableFuture<Void> reload() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<TitleDefinition> definitions = repository.loadAll();
                persistFiles(definitions);
                return definitions;
            } catch (Exception ex) {
                throw new IllegalStateException("重载称号定义失败", ex);
            }
        }, executor).thenCompose(this::replaceOnMainThread);
    }
    public void refreshIfChanged() {
        if (!refreshing.compareAndSet(false, true)) {
            return;
        }
        CompletableFuture.supplyAsync(() -> {
            try {
                List<TitleDefinition> loaded = repository.loadAll();
                return loaded.equals(registry.configured()) ? null : loaded;
            } catch (Exception ex) {
                plugin.getLogger().warning("定时校准称号定义失败: " + ex.getMessage());
                return null;
            }
        }, executor)
                .thenCompose(definitions -> definitions == null
                        ? CompletableFuture.completedFuture(null)
                        : replaceOnMainThread(definitions))
                .whenComplete((ignored, error) -> refreshing.set(false));
    }
    public void setSyncPublisher(Runnable syncPublisher) {
        this.syncPublisher = syncPublisher == null ? () -> {
        } : syncPublisher;
    }
    private void persistFiles(List<TitleDefinition> definitions) {
        try {
            yamlWriter.writeAll(definitions, new File(plugin.getDataFolder(), "titles.yml"));
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("写回 titles.yml 失败: " + ex.getMessage());
        }
        try {
            tagFileService.writeAll(definitions);
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("写回 tags/ 文件夹失败: " + ex.getMessage());
        }
    }
    private CompletableFuture<Void> replaceOnMainThread(List<TitleDefinition> definitions) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        SchedulerUtil.runTask(plugin, () -> {
            try {
                registry.replaceConfigured(definitions);
                future.complete(null);
            } catch (RuntimeException ex) {
                plugin.getLogger().severe("刷新称号定义失败: " + ex.getMessage());
                future.completeExceptionally(ex);
            }
        });
        return future;
    }
    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10L, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("称号定义任务未能在 10 秒内排空");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
    public record UploadResult(int count, int inserted, int updated, List<String> errors) {
        public UploadResult {
            errors = List.copyOf(errors);
        }
        public boolean valid() {
            return errors.isEmpty();
        }
    }
    public record SyncResult(int count, List<String> errors) {
        public SyncResult {
            errors = List.copyOf(errors);
        }
        public boolean valid() {
            return errors.isEmpty();
        }
    }
    private record UploadWork(UploadResult result, List<TitleDefinition> definitions) {
    }
    private record SyncWork(List<TitleDefinition> definitions, List<String> errors) {
    }
}
