package com.dracave.title;
import com.dracave.title.api.DraCaveTitleAPI;
import com.dracave.title.command.TitleCommand;
import com.dracave.title.config.Messages;
import com.dracave.title.config.TagFileService;
import com.dracave.title.config.TitleRegistry;
import com.dracave.title.config.TitleYamlParser;
import com.dracave.title.config.TitlesYamlWriter;
import com.dracave.title.currency.CurrencyProvider;
import com.dracave.title.currency.CurrencyRegistry;
import com.dracave.title.currency.PlayerPointsCurrencyProvider;
import com.dracave.title.currency.TitleCoinCurrencyProvider;
import com.dracave.title.currency.VaultCurrencyProvider;
import com.dracave.title.gui.GuiListener;
import com.dracave.title.gui.GuiSound;
import com.dracave.title.gui.GuiSound;
import com.dracave.title.hook.DraCaveTitleExpansion;
import com.dracave.title.listener.ChatListener;
import com.dracave.title.listener.TitleCardListener;
import com.dracave.title.listener.TitleListener;
import com.dracave.title.model.CurrencyType;
import com.dracave.title.model.TitleDefinition;
import com.dracave.title.panel.TitleAdminPanel;
import com.dracave.title.render.TitleRenderer;
import com.dracave.title.service.ChatPromptManager;
import com.dracave.title.service.CustomTitleService;
import com.dracave.title.service.RewardService;
import com.dracave.title.service.TitleCardService;
import com.dracave.title.service.TitleDefinitionService;
import com.dracave.title.service.TitleParticleService;
import com.dracave.title.service.TitlePotionService;
import com.dracave.title.service.TitlePurchaseService;
import com.dracave.title.service.TitleService;
import com.dracave.title.storage.CoinRepository;
import com.dracave.title.storage.CustomTitleRepository;
import com.dracave.title.storage.QuotaRepository;
import com.dracave.title.storage.RewardRepository;
import com.dracave.title.storage.TitleDatabase;
import com.dracave.title.storage.TitleDefinitionRepository;
import com.dracave.title.storage.TitleRepository;
import com.dracave.title.util.SchedulerUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
public final class DraCaveTitlePlugin extends JavaPlugin {
    private TitleRegistry registry;
    private Messages messages;
    private TitleDatabase database;
    private TitleService service;
    private TitlePurchaseService purchaseService;
    private CurrencyRegistry currencies;
    private CustomTitleService customTitles;
    private DraCaveTitleExpansion expansion;
    private TitleDefinitionRepository definitionRepository;
    private TitleDefinitionService definitionService;
    private TitleAdminPanel adminPanel;
    private TitlePotionService potionService;
    private TitleParticleService particleService;
    private RewardService rewards;
    private CoinRepository coinRepository;
    private QuotaRepository quotaRepository;
    private RewardRepository rewardRepository;
    private TitleCardService cardService;
    private ChatPromptManager chatPrompts;
    private TitleRepository titleRepository;
    private TagFileService tagFileService;
    private GuiSound guiSound;
    private final java.util.Map<java.util.UUID, Long> lastPurgeAt = new java.util.concurrent.ConcurrentHashMap<>();
    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResourceIfMissing("titles.yml");
        saveResourceIfMissing("messages.yml");
        File tagsDir = new File(getDataFolder(), "tags");
        if (!tagsDir.exists()) {
            tagsDir.mkdirs();
        }
        messages = new Messages(this);
        guiSound = new GuiSound(getConfig());
        applyRenderSettings();
        registry = new TitleRegistry(this);
        chatPrompts = new ChatPromptManager(this);
        try {
            database = new TitleDatabase(getConfig(), getDataFolder());
            getLogger().info("数据库连接成功（" + (database.sqlite() ? "SQLite" : "MySQL") + "）");
            definitionRepository = new TitleDefinitionRepository(database);
            tagFileService = new TagFileService(this, new TitleYamlParser(), new TitlesYamlWriter());
            List<TitleDefinition> definitions = tagFileService.loadAll();
            if (definitions.isEmpty()) {
                definitions = definitionRepository.loadAll();
                if (!definitions.isEmpty()) {
                    tagFileService.writeAll(definitions);
                    getLogger().info("已从数据库加载 " + definitions.size() + " 个称号并生成标签文件");
                } else {
                    getLogger().warning("尚无全局称号，请确认 titles.yml 后执行 /dctitle upload all");
                }
            } else {
                getLogger().info("已从标签文件加载 " + definitions.size() + " 个称号");
            }
            registry.replaceConfigured(definitions);
            definitionService = new TitleDefinitionService(this, definitionRepository, registry, tagFileService);
            titleRepository = new TitleRepository(database);
            service = new TitleService(this, registry, titleRepository);
            potionService = new TitlePotionService(this, service);
            particleService = new TitleParticleService(this, service);
            service.setEffectReconciler(playerId -> {
                potionService.reconcile(playerId);
                particleService.reconcile(playerId);
            });
            coinRepository = new CoinRepository(database);
            DraCaveTitleAPI.bindCoin(coinRepository);
            quotaRepository = new QuotaRepository(database);
            rewardRepository = new RewardRepository(database);
            customTitles = new CustomTitleService(this,
                    new CustomTitleRepository(database), quotaRepository, registry, service);
            customTitles.loadAll();
            DraCaveTitleAPI.bindCustomTitles(customTitles);
            currencies = new CurrencyRegistry();
            currencies.register(new VaultCurrencyProvider());
            currencies.register(new PlayerPointsCurrencyProvider());
            currencies.register(new TitleCoinCurrencyProvider(coinRepository));
            purchaseService = new TitlePurchaseService(this, registry, service, titleRepository, currencies);
            logCurrencyStatus();
            rewards = new RewardService(this, service, rewardRepository, currencies);
            service.setRewardChecker(rewards::check);
            cardService = new TitleCardService(this);
            adminPanel = new TitleAdminPanel(this);
            DraCaveTitleAPI.bind(service, registry, purchaseService);
        } catch (Exception ex) {
            getLogger().severe("称号服务初始化失败，本次以降级模式运行（命令仅提示服务不可用）: " + ex.getMessage());
            getLogger().severe("请检查 config.yml 的 storage 配置后重启服务器");
            shutdownServices();
        }
        registerCommand();
        getServer().getPluginManager().registerEvents(new GuiListener(this), this);
        getServer().getPluginManager().registerEvents(new TitleListener(this), this);
        getServer().getPluginManager().registerEvents(new TitleCardListener(this), this);
        getServer().getPluginManager().registerEvents(chatPrompts, this);
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        if (service != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                service.load(player.getUniqueId());
            }
            startCrossServerSynchronization();
            purchaseService.recoverInterruptedPurchases();
            particleService.start();
            registerPlaceholderApi();
            getLogger().info("DraCaveTitle 已启用（称号仓库/商店/自定义称号，兼容 Paper 1.21+）");
        } else {
            getLogger().warning("DraCaveTitle 处于降级模式：修复数据库配置后重启服务器即可恢复全部功能");
        }
    }
    private void logCurrencyStatus() {
        for (CurrencyType type : CurrencyType.values()) {
            CurrencyProvider provider = currencies.get(type);
            if (provider != null) {
                getLogger().info("货币 " + type.id() + "：" + (provider.available() ? "可用" : "不可用"));
            }
        }
        Map<CurrencyType, List<String>> broken = new EnumMap<>(CurrencyType.class);
        for (TitleDefinition title : registry.all()) {
            if (title.purchasable() && !purchaseService.currencyAvailable(title.purchaseOffer())) {
                broken.computeIfAbsent(title.purchaseOffer().currency(), key -> new ArrayList<>()).add(title.id());
            }
        }
        broken.forEach((type, ids) -> getLogger().warning(
                "货币 " + type.id() + " 不可用，以下称号无法购买：" + String.join("、", ids)));
    }
    private void registerCommand() {
        TitleCommand command = new TitleCommand(this);
        if (getCommand("dracavetitle") != null) {
            getCommand("dracavetitle").setExecutor(command);
            getCommand("dracavetitle").setTabCompleter(command);
        } else {
            getLogger().severe("未找到 dracavetitle 命令定义，请检查 plugin.yml");
        }
        if (getCommand("ttt") != null) {
            com.dracave.migrator.command.MigrateCommand migrate = new com.dracave.migrator.command.MigrateCommand(this);
            getCommand("ttt").setExecutor(migrate);
            getCommand("ttt").setTabCompleter(migrate);
        }
    }
    private void startCrossServerSynchronization() {
        long interval = Math.max(20L, getConfig().getLong("storage.sync-interval-ticks", 40L));
        SchedulerUtil.runTaskTimerAsynchronously(this, () -> {
            if (service == null) {
                return;
            }
            List<UUID> playerIds = Bukkit.getOnlinePlayers().stream().map(Player::getUniqueId).toList();
            service.synchronizeEquipped(playerIds);
        }, interval, interval);
        SchedulerUtil.runTaskTimer(this, () -> {
            if (definitionService != null) {
                definitionService.refreshIfChanged();
            }
        }, 200L, 200L);
        SchedulerUtil.runTaskTimerAsynchronously(this, () -> {
            if (service == null) {
                return;
            }
            long now = System.currentTimeMillis();
            for (Player player : Bukkit.getOnlinePlayers()) {
                java.util.UUID pid = player.getUniqueId();
                Long last = lastPurgeAt.get(pid);
                if (last != null && now - last < 10000L) {
                    continue;
                }
                lastPurgeAt.put(pid, now);
                service.purgeExpired(pid);
            }
        }, 200L, 200L);
    }
    private void registerPlaceholderApi() {
        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            expansion = new DraCaveTitleExpansion(this);
            if (expansion.register()) {
                getLogger().info("已注册 PlaceholderAPI 扩展 %dracavetitle_*%");
            } else {
                getLogger().warning("PlaceholderAPI 扩展注册失败");
            }
        }
    }
    public void reloadFiles() {
        reloadConfig();
        messages.reload();
        applyRenderSettings();
    }
    private void applyRenderSettings() {
        TitleRenderer.configure(
                getConfig().getInt("animation.frame-step-ticks", 2),
                getConfig().getInt("animation.gradient-char-step", 1));
    }
    private void saveResourceIfMissing(String name) {
        File file = new File(getDataFolder(), name);
        if (!file.exists()) {
            saveResource(name, false);
        }
    }
    private void shutdownServices() {
        DraCaveTitleAPI.unbind();
        if (expansion != null) {
            expansion.unregister();
        }
        if (purchaseService != null) {
            purchaseService.close();
        }
        if (definitionService != null) {
            definitionService.close();
        }
        if (adminPanel != null) {
            adminPanel.close();
        }
        if (potionService != null) {
            potionService.close();
        }
        if (particleService != null) {
            particleService.stop();
        }
        if (rewards != null) {
            rewards.close();
        }
        if (customTitles != null) {
            customTitles.close();
        }
        if (service != null) {
            service.close();
        }
        if (database != null) {
            database.close();
        }
        purchaseService = null;
        service = null;
        database = null;
    }
    @Override
    public void onDisable() {
        shutdownServices();
    }
    public TitleRegistry registry() {
        return registry;
    }
    public Messages messages() {
        return messages;
    }
    public TitleService service() {
        return service;
    }
    public TitlePurchaseService purchaseService() {
        return purchaseService;
    }
    public CustomTitleService customTitles() {
        return customTitles;
    }
    public TitleDefinitionRepository definitionRepository() {
        return definitionRepository;
    }
    public TitleDefinitionService definitionService() {
        return definitionService;
    }
    public TitleAdminPanel adminPanel() {
        return adminPanel;
    }
    public TitlePotionService potionService() {
        return potionService;
    }
    public TitleParticleService particleService() {
        return particleService;
    }
    public RewardService rewards() {
        return rewards;
    }
    public CoinRepository coinRepository() {
        return coinRepository;
    }
    public QuotaRepository quotaRepository() {
        return quotaRepository;
    }
    public RewardRepository rewardRepository() {
        return rewardRepository;
    }
    public TitleCardService cardService() {
        return cardService;
    }
    public ChatPromptManager chatPrompts() {
        return chatPrompts;
    }
    public TitleDatabase database() {
        return database;
    }
    public TitleRepository titleRepository() {
        return titleRepository;
    }
    public TagFileService tagFileService() {
        return tagFileService;
    }
    public GuiSound guiSound() {
        return guiSound;
    }
}
