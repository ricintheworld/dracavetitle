package com.dracave.title.command;
import com.dracave.title.DraCaveTitlePlugin;
import com.dracave.title.api.TitleResult;
import com.dracave.title.config.Messages;
import com.dracave.title.gui.AdminShopGui;
import com.dracave.title.gui.CustomTitleGui;
import com.dracave.title.gui.MainMenuGui;
import com.dracave.title.gui.RewardGui;
import com.dracave.title.gui.TitleRepositoryGui;
import com.dracave.title.gui.TitleShopGui;
import com.dracave.title.gui.ViewOpenGui;
import com.dracave.title.model.CurrencyType;
import com.dracave.title.model.CustomTitleDraft;
import com.dracave.title.model.CustomTitleType;
import com.dracave.title.model.PlayerData;
import com.dracave.title.model.TitleAnimation;
import com.dracave.title.model.TitleDefinition;
import com.dracave.title.model.TitleParticle;
import com.dracave.title.model.TitlePotionEffect;
import com.dracave.title.model.TitlePurchaseOffer;
import com.dracave.title.panel.TitleAdminPanel;
import com.dracave.title.render.TitleRenderer;
import com.dracave.title.service.CustomTitleService;
import com.dracave.title.service.TitleCardService;
import com.dracave.title.storage.DataConverter;
import com.dracave.title.util.SchedulerUtil;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;
public final class TitleCommand implements CommandExecutor, TabCompleter {
    private static final Pattern VALID_ID = Pattern.compile("[a-z0-9][a-z0-9_-]{0,63}");
    private static final Pattern COLOR = Pattern.compile("#[0-9A-Fa-f]{6}");
    private final DraCaveTitlePlugin plugin;
    private final MiniMessage mini = MiniMessage.miniMessage();
    public TitleCommand(DraCaveTitlePlugin plugin) {
        this.plugin = plugin;
    }
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (plugin.service() == null) {
            plugin.messages().send(sender, "unavailable");
            return true;
        }
        String sub = args.length == 0 ? "open" : args[0].toLowerCase(Locale.ROOT);
        boolean playerCommand = sub.equals("open") || sub.equals("shop") || sub.equals("custom") || sub.equals("view")
                || sub.equals("wear") || sub.equals("clear") || sub.equals("reward") || sub.equals("ranking");
        if (!playerCommand && !sender.hasPermission("dracave.title.admin")) {
            plugin.messages().send(sender, "no-permission");
            return true;
        }
        if (playerCommand && !sender.hasPermission("dracave.title.use")) {
            plugin.messages().send(sender, "no-permission");
            return true;
        }
        try {
            switch (sub) {
                case "open" -> open(sender, args);
                case "listtitle", "list" -> listTitles(sender);
                case "shop" -> shop(sender);
                case "adminshop", "adminShop" -> adminShop(sender);
                case "create" -> create(sender, args);
                case "add" -> add(sender, args);
                case "del" -> del(sender, args);
                case "setdescription", "setDescription" -> setDescription(sender, args);
                case "addpermission", "addPermission" -> addPermission(sender, args);
                case "settitlebuff", "setTitleBuff" -> setTitleBuff(sender, args);
                case "delbuff", "delBuff" -> delBuff(sender, args);
                case "settitleparticle", "setTitleParticle" -> setTitleParticle(sender, args);
                case "removetitleparticle", "removeTitleParticle" -> removeTitleParticle(sender, args);
                case "convert" -> convert(sender, args);
                case "reload" -> reload(sender);
                case "set" -> set(sender, args);
                case "addplayertitle", "addPlayerTitle" -> addPlayerTitle(sender, args);
                case "addcoin", "addCoin" -> addCoin(sender, args);
                case "subtractcoin", "subtractCoin" -> subtractCoin(sender, args);
                case "changeitem", "changeItem" -> changeItem(sender, args);
                case "addreward", "addReward" -> addReward(sender, args);
                case "randomcard", "randomCard" -> randomCard(sender, args);
                case "setcustom", "setCustom" -> setCustom(sender, args);
                case "addcustom", "addCustom" -> addCustom(sender, args);
                case "custom" -> custom(sender, args);
                case "view" -> view(sender, args);
                case "wear" -> wear(sender, args);
                case "clear" -> clear(sender);
                case "reward" -> reward(sender);
                case "ranking" -> ranking(sender);
                case "upload" -> upload(sender, args);
                case "panel" -> panel(sender, args, false);
                case "panel-id" -> panel(sender, args, true);
                case "panel-edit" -> panelEdit(sender, args);
                case "help" -> help(sender);
                default -> help(sender);
            }
        } catch (IllegalArgumentException ex) {
            plugin.messages().send(sender, "operation-failed");
            sender.sendMessage("§c" + ex.getMessage());
        }
        return true;
    }
    private void open(CommandSender sender, String[] args) {
        if (args.length >= 2 && sender.hasPermission("dracave.title.admin")) {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null || !target.isOnline()) {
                plugin.messages().send(sender, "unknown-player", Messages.text("player", args[1]));
                return;
            }
            new TitleRepositoryGui(plugin, target, 0).open();
            return;
        }
        requirePlayer(sender, p -> new TitleRepositoryGui(plugin, p, 0).open());
    }
    private void shop(CommandSender sender) {
        requirePlayer(sender, p -> new TitleShopGui(plugin, p, 0).open());
    }
    private void adminShop(CommandSender sender) {
        requirePlayer(sender, p -> new AdminShopGui(plugin, p, 0).open());
    }
    private void reward(CommandSender sender) {
        requirePlayer(sender, p -> new RewardGui(plugin, p).open());
    }
    private void wear(CommandSender sender, String[] args) {
        requirePlayer(sender, p -> {
            if (args.length < 2) {
                new TitleRepositoryGui(plugin, p, 0).open();
                return;
            }
            String titleId = args[1];
            if (titleId.equalsIgnoreCase("none")) {
                plugin.service().clear(p.getUniqueId()).thenAccept(result -> {
                    if (result == TitleResult.SUCCESS) {
                        plugin.messages().send(p, "cleared");
                    } else if (result == TitleResult.CANCELLED) {
                        plugin.messages().send(p, "operation-failed");
                    }
                });
                return;
            }
            plugin.service().equip(p.getUniqueId(), titleId).thenAccept(result -> {
                if (result == TitleResult.SUCCESS) {
                    plugin.messages().send(p, "equipped",
                            Messages.parsed("title", renderedTitle(titleId)));
                } else if (result == TitleResult.NOT_UNLOCKED) {
                    plugin.messages().send(p, "locked");
                } else if (result == TitleResult.TITLE_NOT_FOUND) {
                    plugin.messages().send(p, "unknown-title", Messages.text("id", titleId));
                } else if (result == TitleResult.COOLDOWN) {
                } else {
                    plugin.messages().send(p, "operation-failed");
                }
            });
        });
    }
    private void clear(CommandSender sender) {
        requirePlayer(sender, p -> plugin.service().clear(p.getUniqueId()).thenAccept(result -> {
            if (result == TitleResult.SUCCESS) {
                PlayerData data = plugin.service().getCached(p.getUniqueId());
                if (data == null || data.equippedId() == null) {
                    plugin.messages().send(p, "cleared-none");
                } else {
                    plugin.messages().send(p, "cleared");
                }
            }
        }));
    }
    private void listTitles(CommandSender sender) {
        List<TitleDefinition> titles = plugin.registry().all();
        if (titles.isEmpty()) {
            sender.sendMessage("§e当前没有任何称号，请先在 titles.yml 中配置并执行 /dctitle upload。");
            return;
        }
        sender.sendMessage("§e===== 服务器称号列表（共 " + titles.size() + " 个）=====");
        for (TitleDefinition title : titles) {
            StringBuilder line = new StringBuilder();
            line.append(TitleRenderer.miniMessage(title, System.currentTimeMillis()))
                    .append(" §7(").append(title.id()).append(")");
            if (title.purchasable()) {
                line.append(" §f价格：").append(title.purchaseOffer().price().toPlainString())
                        .append(" ").append(title.purchaseOffer().currency().id());
            }
            if (title.shopHidden()) {
                line.append(" §7[商店隐藏]");
            }
            sender.sendMessage(line.toString());
        }
    }
    private void view(CommandSender sender, String[] args) {
        if (args.length >= 3) {
            if (!sender.hasPermission("dracave.title.admin")) {
                plugin.messages().send(sender, "no-permission");
                return;
            }
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
            if (!target.hasPlayedBefore() && Bukkit.getPlayerExact(args[2]) == null) {
                plugin.messages().send(sender, "unknown-player", Messages.text("player", args[2]));
                return;
            }
            plugin.service().load(target.getUniqueId()).thenAccept(data -> {
                if (data == null) {
                    plugin.messages().send(sender, "unavailable");
                    return;
                }
                sender.sendMessage("§e玩家 " + args[2] + " 的称号列表（共 " + data.unlocked().size() + " 个）：");
                for (String id : data.unlocked().stream().sorted().toList()) {
                    TitleDefinition title = plugin.registry().get(id);
                    if (title == null) {
                        continue;
                    }
                    String equipped = id.equals(data.equippedId()) ? " §a[当前穿戴]" : "";
                    sender.sendMessage(TitleRenderer.miniMessage(title, System.currentTimeMillis())
                            + " §7(" + id + ")" + equipped);
                }
            });
            return;
        }
        if (args.length == 2) {
            String type = args[1].toLowerCase(Locale.ROOT);
            switch (type) {
                case "shop" -> requirePlayer(sender, p -> new TitleShopGui(plugin, p, 0).open());
                case "reward" -> requirePlayer(sender, p -> new RewardGui(plugin, p).open());
                default -> {
                    if (!sender.hasPermission("dracave.title.admin")) {
                        plugin.messages().send(sender, "no-permission");
                        return;
                    }
                    OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                    if (!target.hasPlayedBefore() && Bukkit.getPlayerExact(args[1]) == null) {
                        plugin.messages().send(sender, "unknown-player", Messages.text("player", args[1]));
                        return;
                    }
                    if (sender instanceof Player admin) {
                        new ViewOpenGui(plugin, admin, target.getUniqueId(), args[1], 0).open();
                    } else {
                        viewPlayer(sender, target);
                    }
                }
            }
            return;
        }
        requirePlayer(sender, p -> new MainMenuGui(plugin, p).open());
    }
    private void viewPlayer(CommandSender sender, OfflinePlayer target) {
        plugin.service().load(target.getUniqueId()).thenAccept(data -> {
            if (data == null) {
                plugin.messages().send(sender, "unavailable");
                return;
            }
            sender.sendMessage("§e玩家 " + target.getName() + " 的称号列表（共 " + data.unlocked().size() + " 个）：");
            for (String id : data.unlocked().stream().sorted().toList()) {
                TitleDefinition title = plugin.registry().get(id);
                if (title == null) {
                    continue;
                }
                String equipped = id.equals(data.equippedId()) ? " §a[当前穿戴]" : "";
                sender.sendMessage(TitleRenderer.miniMessage(title, System.currentTimeMillis())
                        + " §7(" + id + ")" + equipped);
            }
        });
    }
    private void custom(CommandSender sender, String[] args) {
        requirePlayer(sender, p -> {
            if (args.length == 1) {
                new CustomTitleGui(plugin, p, 0).open();
                return;
            }
            String action = args[1].toLowerCase(Locale.ROOT);
            if (action.equals("delete") && args.length >= 3) {
                plugin.customTitles().delete(p, args[2]).thenAccept(result ->
                        SchedulerUtil.runTask(plugin, () -> sendCustomResult(p, result)));
            } else if (action.equals("create") && args.length >= 4) {
                createCustom(p, args, 2, false);
            } else if (action.equals("edit") && args.length >= 5) {
                createCustom(p, args, 3, true);
            } else if (action.equals("create") || action.equals("edit")) {
                plugin.messages().send(p, "custom.command-help");
            } else {
                createQuick(p, args[1]);
            }
        });
    }
    private void createQuick(Player player, String name) {
        plugin.customTitles().create(player, CustomTitleDraft.staticTitle(name, "#55FFFF", "NAME_TAG"))
                .thenAccept(result -> SchedulerUtil.runTask(plugin, () -> sendCustomResult(player, result)));
    }
    private void createCustom(Player player, String[] args, int typeIndex, boolean editing) {
        String typeName = args[typeIndex].toLowerCase(Locale.ROOT);
        CustomTitleType type = CustomTitleType.parse(typeName);
        if (type == null) {
            plugin.messages().send(player, "custom.result-invalid");
            return;
        }
        int base = typeIndex + 1;
        CustomTitleDraft draft = switch (type) {
            case STATIC -> {
                if (args.length < base + 2) {
                    yield null;
                }
                String color = COLOR.matcher(args[base].toUpperCase()).matches() ? args[base].toUpperCase() : "#55FFFF";
                yield CustomTitleDraft.staticTitle(args[base + 1], color, "NAME_TAG");
            }
            case FLOWING_GRADIENT -> {
                if (args.length < base + 3) {
                    yield null;
                }
                yield CustomTitleDraft.gradient(args[base + 2], parseColors(args[base + 1]), parsePeriod(args[base]), "NAME_TAG");
            }
            case RAINBOW -> {
                if (args.length < base + 2) {
                    yield null;
                }
                yield CustomTitleDraft.rainbow(args[base + 1], parsePeriod(args[base]), "NAME_TAG");
            }
            case FLASHING_COLORS -> {
                if (args.length < base + 3) {
                    yield null;
                }
                yield CustomTitleDraft.flash(args[base + 2], parseColors(args[base + 1]), parsePeriod(args[base]), "NAME_TAG");
            }
            case TEXT_FRAMES -> {
                if (args.length < base + 3) {
                    yield null;
                }
                yield CustomTitleDraft.frames(args[base + 2], Arrays.asList(args[base + 1].split("\\|")), parsePeriod(args[base]), "NAME_TAG");
            }
        };
        if (draft == null) {
            plugin.messages().send(player, "custom.command-help");
            return;
        }
        var result = editing
                ? plugin.customTitles().update(player, args[2], draft)
                : plugin.customTitles().create(player, draft);
        result.thenAccept(r -> SchedulerUtil.runTask(plugin, () -> sendCustomResult(player, r)));
    }
    private List<String> parseColors(String value) {
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .map(c -> c.toUpperCase(Locale.ROOT))
                .peek(c -> {
                    if (!COLOR.matcher(c).matches()) {
                        throw new IllegalArgumentException("颜色必须是 #RRGGBB: " + c);
                    }
                })
                .toList();
    }
    private int parsePeriod(String value) {
        int period = Integer.parseInt(value);
        if (period < 5 || period > 200) {
            throw new IllegalArgumentException("周期必须在 5-200 游戏刻之间");
        }
        return period;
    }
    private void sendCustomResult(Player player, CustomTitleService.Result result) {
        if (result == CustomTitleService.Result.SUCCESS) {
            plugin.messages().send(player, "custom.created");
        } else {
            plugin.messages().send(player, "custom.result-" + result.name().toLowerCase(Locale.ROOT).replace('_', '-'));
        }
    }
    private void add(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage("§e用法：/dctitle add <货币类型 vault|playerpoints|coin|item> <称号名称> <价格> [天数] [隐藏 true|false] [玩家名]");
            sender.sendMessage("§7物品购买（item）：手持支付物执行命令，价格 = 所需数量");
            return;
        }
        CurrencyType currency;
        try {
            currency = CurrencyType.parse(args[1]);
        } catch (IllegalArgumentException ex) {
            sender.sendMessage("§c未知货币类型：" + args[1]);
            return;
        }
        final String itemMaterial;
        if (currency == CurrencyType.ITEM) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§c物品购买需要在游戏内执行（使用主手物品作为支付物）。");
                return;
            }
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (hand == null || hand.getType() == Material.AIR) {
                sender.sendMessage("§c请手持要作为支付物的物品（如钻石）。");
                return;
            }
            itemMaterial = hand.getType().name();
        } else {
            itemMaterial = null;
        }
        String name = args[2];
        BigDecimal price;
        try {
            price = new BigDecimal(args[3]);
        } catch (NumberFormatException ex) {
            sender.sendMessage("§c价格不是合法数字。");
            return;
        }
        int days = args.length >= 5 ? parseInt(args[4], 0) : 0;
        boolean hidden = args.length >= 6 && Boolean.parseBoolean(args[5]);
        TitlePurchaseOffer offer = currency == CurrencyType.ITEM
                ? new TitlePurchaseOffer(CurrencyType.ITEM, price, itemMaterial)
                : new TitlePurchaseOffer(currency, price);
        SchedulerUtil.runTaskAsynchronously(plugin, () -> {
            try {
                TitleDefinition definition = new TitleDefinition(
                        generateId(name), mini.escapeTags(name), List.of("<gray>通过 /dctitle add 创建"),
                        "NAME_TAG", 0, false, "", new TitleAnimation(List.of("#7AFBFF", "#B97AFF"), 40),
                        offer, List.of("#7AFBFF", "#B97AFF"), hidden, List.of(), null, 0);
                plugin.definitionRepository().upsertAll(List.of(definition));
                plugin.definitionService().reload().thenRun(() -> {
                    String priceDisplay = currency == CurrencyType.ITEM
                            ? price.toPlainString() + " × " + itemMaterial
                            : price + " " + currency.id();
                    sender.sendMessage("§a已创建称号 §f" + name + " §a(ID: " + definition.id() + "，价格：" + priceDisplay + ")。");
                });
                if (args.length >= 7) {
                    grantToPlayer(sender, args[6], definition.id(), days, true);
                }
            } catch (Exception ex) {
                plugin.getLogger().severe("创建称号失败: " + ex.getMessage());
                plugin.messages().send(sender, "operation-failed");
            }
        });
    }
    private void create(CommandSender sender, String[] args) {
        if (args.length < 5) {
            sender.sendMessage("§e用法：/dctitle create <称号文本> <颜色(#或逗号分隔hex色号)> <购买方式 vault|coin|point|item> <价格> [item物品]");
            return;
        }
        String rawText = args[1];
        String colorArg = args[2];
        CurrencyType currency;
        try {
            currency = CurrencyType.parse(args[3]);
        } catch (IllegalArgumentException ex) {
            sender.sendMessage("§c未知货币类型：" + args[3]);
            return;
        }
        BigDecimal price;
        try {
            price = new BigDecimal(args[4]);
        } catch (NumberFormatException ex) {
            sender.sendMessage("§c价格不是合法数字。");
            return;
        }
        String itemMaterial = null;
        if (currency == CurrencyType.ITEM) {
            if (args.length < 6) {
                sender.sendMessage("§c物品购买需要指定物品材质（如 minecraft:diamond）。");
                return;
            }
            itemMaterial = args[5];
            String lowerItem = itemMaterial.toLowerCase(java.util.Locale.ROOT);
            boolean isCustom = lowerItem.contains(":") && !lowerItem.startsWith("minecraft:");
            if (lowerItem.startsWith("minecraft:")) {
                itemMaterial = itemMaterial.substring(10);
            }
            if (!isCustom) {
                itemMaterial = itemMaterial.toUpperCase(java.util.Locale.ROOT);
                if (Material.matchMaterial(itemMaterial) == null) {
                    sender.sendMessage("§c未知物品材质：" + args[5]);
                    return;
                }
            }
        }
        TitlePurchaseOffer offer = currency == CurrencyType.ITEM
                ? new TitlePurchaseOffer(CurrencyType.ITEM, price, itemMaterial)
                : new TitlePurchaseOffer(currency, price);
        java.util.List<String> colors = new java.util.ArrayList<>();
        String display;
        if ("#".equals(colorArg)) {
            display = mini.escapeTags(rawText);
        } else {
            for (String part : colorArg.split(",")) {
                String c = part.trim();
                if (c.startsWith("#")) {
                    colors.add(c);
                } else if (!c.isEmpty()) {
                    colors.add("#" + c);
                }
            }
            if (colors.isEmpty()) {
                display = mini.escapeTags(rawText);
            } else if (colors.size() >= 2) {
                display = "<gradient:" + colors.get(0) + ":" + colors.get(colors.size() - 1) + ">"
                        + mini.escapeTags(rawText) + "</gradient>";
            } else {
                display = "<" + colors.get(0) + ">" + mini.escapeTags(rawText) + "</" + colors.get(0) + ">";
            }
        }
        String finalItemMaterial = itemMaterial;
        SchedulerUtil.runTaskAsynchronously(plugin, () -> {
            try {
                TitleDefinition definition = new TitleDefinition(
                        generateId(rawText), display, java.util.List.of("<gray>通过 /dctitle create 创建"),
                        "NAME_TAG", 0, false, "", null,
                        offer, colors, false, java.util.List.of(), null, 0);
                java.io.File ymlFile = new java.io.File(plugin.getDataFolder(), "titles.yml");
                com.dracave.title.config.TitleYamlParser parser = new com.dracave.title.config.TitleYamlParser();
                com.dracave.title.config.TitleYamlParser.ParseResult parsed = parser.parse(ymlFile);
                java.util.List<TitleDefinition> all = new java.util.ArrayList<>(parsed.definitions());
                all.add(definition);
                new com.dracave.title.config.TitlesYamlWriter().writeAll(all, ymlFile);
                plugin.definitionService().upload().thenRun(() ->
                        SchedulerUtil.runTask(plugin, () -> {
                            String priceDisplay = currency == CurrencyType.ITEM
                                    ? price.toPlainString() + " × " + finalItemMaterial
                                    : price + " " + currency.id();
                            sender.sendMessage("§a已创建称号 §f" + rawText + " §a(ID: " + definition.id() + "，价格：" + priceDisplay + ")。");
                        }));
            } catch (Exception ex) {
                plugin.getLogger().severe("创建称号失败: " + ex.getMessage());
                plugin.messages().send(sender, "operation-failed");
            }
        });
    }
    private void ranking(CommandSender sender) {
        SchedulerUtil.runTaskAsynchronously(plugin, () -> {
            try {
                List<com.dracave.title.storage.TitleRankEntry> list = plugin.titleRepository().ranking(10);
                SchedulerUtil.runTask(plugin, () -> {
                    sender.sendMessage("§e§m-------------§f[§e称号数量排行榜§f]§e§m-------------");
                    if (list.isEmpty()) {
                        sender.sendMessage("§7暂无数据");
                        return;
                    }
                    int rank = 1;
                    for (com.dracave.title.storage.TitleRankEntry entry : list) {
                        OfflinePlayer offline = Bukkit.getOfflinePlayer(entry.playerId());
                        String name = offline.getName() == null
                                ? entry.playerId().toString().substring(0, 8) : offline.getName();
                        sender.sendMessage("§e" + rank + ". §f" + name + " §7（" + entry.count() + " 个称号）");
                        rank++;
                    }
                });
            } catch (Exception ex) {
                plugin.getLogger().warning("查询称号排行榜失败: " + ex.getMessage());
                plugin.messages().send(sender, "operation-failed");
            }
        });
    }
    private String generateId(String name) {
        String slug = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "").replaceAll("[-_]+", "-");
        if (slug.length() > 20) {
            slug = slug.substring(0, 20);
        }
        if (slug.isEmpty()) {
            slug = "title";
        }
        String id = slug;
        for (int i = 0; i < 10; i++) {
            if (plugin.registry().get(id) == null && VALID_ID.matcher(id).matches()) {
                return id;
            }
            id = slug + "_" + Integer.toHexString(ThreadLocalRandom.current().nextInt(0x1000, 0x10000));
        }
        throw new IllegalArgumentException("无法生成唯一称号 ID，请重试");
    }
    private void del(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§e用法：/dctitle del <称号ID>");
            return;
        }
        String titleId = args[1].toLowerCase(Locale.ROOT);
        SchedulerUtil.runTaskAsynchronously(plugin, () -> {
            try {
                boolean deleted = plugin.definitionRepository().delete(titleId);
                if (deleted) {
                    try {
                        int removed = plugin.titleRepository().removeTitleFromAll(titleId);
                        if (removed > 0) {
                            sender.sendMessage("§e已清理 " + removed + " 名玩家的该称号数据");
                        }
                    } catch (Exception cleanup) {
                        plugin.getLogger().warning("清理玩家称号数据失败 " + titleId + ": " + cleanup.getMessage());
                    }
                }
            } catch (Exception ex) {
                plugin.getLogger().severe("删除称号失败 " + titleId + ": " + ex.getMessage());
                plugin.messages().send(sender, "operation-failed");
                return;
            }
            plugin.service().removeCachedTitleFromAll(titleId);
            SchedulerUtil.runTask(plugin, () -> plugin.definitionService().reload().thenRun(() -> {
                plugin.messages().send(sender, "title-deleted", Messages.text("id", titleId));
            }));
        });
    }
    private void setDescription(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§e用法：/dctitle setDescription <称号ID> <描述>（多行用 \\n 分隔，支持 MiniMessage）");
            return;
        }
        String titleId = args[1].toLowerCase(Locale.ROOT);
        modifyDefinition(sender, titleId, definition -> {
            String raw = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
            List<String> lines = Arrays.stream(raw.split("\\\\n")).limit(64).toList();
            for (String line : lines) {
                if (line.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 65535) {
                    throw new IllegalArgumentException("描述单行过长");
                }
            }
            return new TitleDefinition(definition.id(), definition.display(), lines, definition.icon(),
                    definition.order(), definition.defaultUnlocked(), definition.permission(), definition.animation(),
                    definition.purchaseOffer(), definition.colors(), definition.shopHidden(),
                    definition.potionEffects(), definition.particle(), definition.revision());
        }, "description-set", Messages.text("id", titleId));
    }
    private void addPermission(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§e用法：/dctitle addPermission <称号名称或ID> <所需权限>（none 清除）");
            return;
        }
        String titleId = resolveTitleId(args[1]);
        String permission = args[2].equalsIgnoreCase("none") ? "" : args[2];
        modifyDefinition(sender, titleId, definition -> new TitleDefinition(
                definition.id(), definition.display(), definition.description(), definition.icon(),
                definition.order(), definition.defaultUnlocked(), permission, definition.animation(),
                definition.purchaseOffer(), definition.colors(), definition.shopHidden(),
                definition.potionEffects(), definition.particle(), definition.revision()),
                "permission-set", Messages.text("id", titleId), Messages.text("permission", permission.isEmpty() ? "无" : permission));
    }
    private void setTitleBuff(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage("§e用法：/dctitle setTitleBuff <称号ID> <类型: POTION_EFFECT|POTION> <效果名> [等级]");
            return;
        }
        String titleId = args[1].toLowerCase(Locale.ROOT);
        String type = args[2].toLowerCase(Locale.ROOT);
        if (!type.equals("potion_effect") && !type.equals("potion") && !type.equals("药水")) {
            sender.sendMessage("§c当前仅支持药水效果类型（POTION_EFFECT）。");
            return;
        }
        PotionEffectType effectType = PotionEffectType.getByName(args[3].toUpperCase());
        if (effectType == null) {
            sender.sendMessage("§c未知药水效果：" + args[3]);
            return;
        }
        int level = args.length >= 5 ? parseInt(args[4], 1) : 1;
        if (level < 1 || level > 255) {
            sender.sendMessage("§c等级必须为 1-255。");
            return;
        }
        int finalLevel = level;
        modifyDefinition(sender, titleId, definition -> {
            List<TitlePotionEffect> effects = new ArrayList<>(definition.potionEffects());
            effects.removeIf(e -> e.effectType().equals(effectType.getName()));
            effects.add(new TitlePotionEffect(effectType.getName(), finalLevel));
            return new TitleDefinition(definition.id(), definition.display(), definition.description(), definition.icon(),
                    definition.order(), definition.defaultUnlocked(), definition.permission(), definition.animation(),
                    definition.purchaseOffer(), definition.colors(), definition.shopHidden(),
                    effects, definition.particle(), definition.revision());
        }, "buff-added", Messages.text("id", titleId), Messages.text("effect", effectType.getName() + " " + finalLevel));
    }
    private void delBuff(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§e用法：/dctitle delBuff <称号ID> <效果名>");
            return;
        }
        String titleId = args[1].toLowerCase(Locale.ROOT);
        String effectName = args[2].toUpperCase(Locale.ROOT);
        modifyDefinition(sender, titleId, definition -> {
            List<TitlePotionEffect> effects = new ArrayList<>(definition.potionEffects());
            if (!effects.removeIf(e -> e.effectType().equals(effectName))) {
                throw new IllegalArgumentException("该称号没有 " + effectName + " 效果");
            }
            return new TitleDefinition(definition.id(), definition.display(), definition.description(), definition.icon(),
                    definition.order(), definition.defaultUnlocked(), definition.permission(), definition.animation(),
                    definition.purchaseOffer(), definition.colors(), definition.shopHidden(),
                    effects, definition.particle(), definition.revision());
        }, "buff-removed", Messages.text("buffId", effectName));
    }
    private void setTitleParticle(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§e用法：/dctitle setTitleParticle <称号ID> <粒子类型> [粒子id] (颜色) (颜色) (颜色)");
            return;
        }
        String titleId = args[1].toLowerCase(Locale.ROOT);
        String particleType = args[2].toUpperCase(Locale.ROOT);
        if (!com.dracave.title.service.TitleParticleService.validParticle(particleType)) {
            sender.sendMessage("§c未知粒子类型：" + args[2]);
            return;
        }
        String particleId = args.length >= 4 && !args[3].startsWith("#") ? args[3] : null;
        List<String> colors = new ArrayList<>();
        for (int i = particleId == null ? 3 : 4; i < args.length && colors.size() < 3; i++) {
            String color = args[i].toUpperCase(Locale.ROOT);
            if (!COLOR.matcher(color).matches()) {
                sender.sendMessage("§c颜色必须是 #RRGGBB：" + args[i]);
                return;
            }
            colors.add(color);
        }
        List<String> finalColors = colors;
        String finalParticleId = particleId;
        modifyDefinition(sender, titleId, definition -> new TitleDefinition(
                definition.id(), definition.display(), definition.description(), definition.icon(),
                definition.order(), definition.defaultUnlocked(), definition.permission(), definition.animation(),
                definition.purchaseOffer(), definition.colors(), definition.shopHidden(),
                definition.potionEffects(), new TitleParticle(particleType, finalParticleId, finalColors), definition.revision()),
                "particle-set", Messages.text("id", titleId));
    }
    private void removeTitleParticle(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§e用法：/dctitle removeTitleParticle <称号ID>");
            return;
        }
        String titleId = args[1].toLowerCase(Locale.ROOT);
        modifyDefinition(sender, titleId, definition -> new TitleDefinition(
                definition.id(), definition.display(), definition.description(), definition.icon(),
                definition.order(), definition.defaultUnlocked(), definition.permission(), definition.animation(),
                definition.purchaseOffer(), definition.colors(), definition.shopHidden(),
                definition.potionEffects(), null, definition.revision()),
                "particle-removed", Messages.text("id", titleId));
    }
    private void modifyDefinition(CommandSender sender, String titleId,
                                  java.util.function.Function<TitleDefinition, TitleDefinition> transform,
                                  String messageKey, net.kyori.adventure.text.minimessage.tag.resolver.TagResolver... resolvers) {
        TitleDefinition current = plugin.registry().get(titleId);
        if (current == null) {
            plugin.messages().send(sender, "unknown-title", Messages.text("id", titleId));
            return;
        }
        TitleDefinition changed;
        try {
            changed = transform.apply(current);
        } catch (IllegalArgumentException ex) {
            sender.sendMessage("§c" + ex.getMessage());
            return;
        }
        plugin.definitionService().update(changed, current.revision()).thenAccept(saved ->
                SchedulerUtil.runTask(plugin, () -> {
                    if (saved) {
                        plugin.messages().send(sender, messageKey, resolvers);
                    } else {
                        plugin.messages().send(sender, "custom.result-conflict");
                    }
                }));
    }
    private void convert(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§e用法：/dctitle convert <MYSQL|SQLITE>（转换到目标存储，完成后需重启服务器）");
            return;
        }
        String target = args[1];
        if (!target.equalsIgnoreCase("MYSQL") && !target.equalsIgnoreCase("SQLITE")) {
            sender.sendMessage("§c类型只能是 MYSQL 或 SQLITE。");
            return;
        }
        SchedulerUtil.runTaskAsynchronously(plugin, () -> {
            try {
                int copied = new DataConverter(plugin).convert(target);
                sender.sendMessage("§a数据转换完成，共写入 " + copied + " 行，请修改 config.yml 的 storage.type 并重启服务器。");
            } catch (IllegalArgumentException ex) {
                sender.sendMessage("§c" + ex.getMessage());
            } catch (Exception ex) {
                plugin.getLogger().severe("数据转换失败: " + ex.getMessage());
                sender.sendMessage("§c数据转换失败，请查看服务端日志。");
            }
        });
    }
    private void reload(CommandSender sender) {
        plugin.reloadFiles();
        plugin.definitionService().reload().thenRun(() -> {
            plugin.messages().send(sender, "reloaded");
        });
    }
    private void set(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§e用法：/dctitle set <玩家名> <称号ID> [天数]（0 为永久）");
            return;
        }
        String playerName = args[1];
        String titleId = args[2].toLowerCase(Locale.ROOT);
        int days = args.length >= 4 ? parseInt(args[3], 0) : 0;
        grantToPlayer(sender, playerName, titleId, days, true);
    }
    private void addPlayerTitle(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§e用法：/dctitle addPlayerTitle <玩家名> <称号名称或ID> [天数]");
            return;
        }
        String playerName = args[1];
        String titleId = resolveTitleId(args[2]);
        int days = args.length >= 4 ? parseInt(args[3], 0) : 0;
        grantToPlayer(sender, playerName, titleId, days, false);
    }
    private void grantToPlayer(CommandSender sender, String playerName, String titleId, int days, boolean equipNow) {
        if (plugin.registry().get(titleId) == null) {
            plugin.messages().send(sender, "unknown-title", Messages.text("id", titleId));
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        if (!target.hasPlayedBefore() && Bukkit.getPlayerExact(playerName) == null) {
            plugin.messages().send(sender, "unknown-player", Messages.text("player", playerName));
            return;
        }
        UUID targetId = target.getUniqueId();
        plugin.service().grant(targetId, titleId, days, equipNow).thenAccept(result ->
                SchedulerUtil.runTask(plugin, () -> {
                    if (result != TitleResult.SUCCESS) {
                        plugin.messages().send(sender, "operation-failed");
                        return;
                    }
                    plugin.messages().send(sender, equipNow ? "title-set" : "title-granted",
                            Messages.text("player", playerName),
                            Messages.parsed("title", renderedTitle(titleId)),
                            Messages.text("days", days > 0 ? Integer.toString(days) : "永久"));
                    if (equipNow) {
                        plugin.service().equip(targetId, titleId);
                    }
                }));
    }
    private void addCoin(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§e用法：/dctitle addCoin <玩家名> <金额>");
            return;
        }
        String playerName = args[1];
        long amount = parseLong(args[2]);
        if (amount <= 0) {
            sender.sendMessage("§c金额必须大于 0。");
            return;
        }
        UUID targetId = resolvePlayerId(playerName, sender);
        if (targetId == null) {
            return;
        }
        SchedulerUtil.runTaskAsynchronously(plugin, () -> {
            try {
                plugin.coinRepository().add(targetId, amount);
                long balance = plugin.coinRepository().balance(targetId);
                plugin.messages().send(sender, "coin-added", Messages.text("player", playerName),
                        Messages.text("amount", Long.toString(amount)),
                        Messages.parsed("currency", coinDisplay()),
                        Messages.text("balance", Long.toString(balance)));
            } catch (Exception ex) {
                plugin.messages().send(sender, "operation-failed");
            }
        });
    }
    private void subtractCoin(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§e用法：/dctitle subtractCoin <玩家名> <金额>");
            return;
        }
        String playerName = args[1];
        long amount = parseLong(args[2]);
        if (amount <= 0) {
            sender.sendMessage("§c金额必须大于 0。");
            return;
        }
        UUID targetId = resolvePlayerId(playerName, sender);
        if (targetId == null) {
            return;
        }
        SchedulerUtil.runTaskAsynchronously(plugin, () -> {
            try {
                if (!plugin.coinRepository().subtract(targetId, amount)) {
                    long balance = plugin.coinRepository().balance(targetId);
                    plugin.messages().send(sender, "coin-insufficient", Messages.text("player", playerName),
                            Messages.parsed("currency", coinDisplay()));
                    return;
                }
                long balance = plugin.coinRepository().balance(targetId);
                plugin.messages().send(sender, "coin-subtracted", Messages.text("player", playerName),
                        Messages.text("amount", Long.toString(amount)),
                        Messages.parsed("currency", coinDisplay()),
                        Messages.text("balance", Long.toString(balance)));
            } catch (Exception ex) {
                plugin.messages().send(sender, "operation-failed");
            }
        });
    }
    private String coinDisplay() {
        return plugin.getConfig().getString("purchase.currencies.coin.display", "称号币");
    }
    private void changeItem(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage("§e用法：/dctitle changeItem <称号ID> <天数> <数量> [玩家名]（将玩家持有的称号转换为物品卡）");
            return;
        }
        String titleId = args[1].toLowerCase(Locale.ROOT);
        int days = parseInt(args[2], 0);
        int count = parseInt(args[3], 1);
        if (count < 1 || count > 64) {
            sender.sendMessage("§c数量必须为 1-64。");
            return;
        }
        final String playerName;
        final Player onlinePlayer;
        if (args.length >= 5) {
            playerName = args[4];
            onlinePlayer = null;
        } else {
            if (!(sender instanceof Player playerSender)) {
                sender.sendMessage("§c请指定玩家名。");
                return;
            }
            onlinePlayer = playerSender;
            playerName = playerSender.getName();
        }
        UUID targetId = resolvePlayerId(playerName, sender);
        if (targetId == null) {
            return;
        }
        PlayerData data = plugin.service().getCached(targetId);
        if (data == null || !data.unlocked().contains(titleId)) {
            plugin.messages().send(sender, "not-unlocked");
            return;
        }
        String finalPlayerName = playerName;
        plugin.service().revoke(targetId, titleId).thenAccept(result ->
                SchedulerUtil.runTask(plugin, () -> {
                    if (result != TitleResult.SUCCESS) {
                        plugin.messages().send(sender, "operation-failed");
                        return;
                    }
                    Player target = onlinePlayer != null ? onlinePlayer : Bukkit.getPlayerExact(finalPlayerName);
                    if (target == null || !target.isOnline()) {
                        sender.sendMessage("§e玩家不在线，称号已收回，请上线后再领取物品。");
                        return;
                    }
                    TitleCardService cards = plugin.cardService();
                    for (int i = 0; i < count; i++) {
                        target.getInventory().addItem(cards.titleCard(titleId, days));
                    }
                    sender.sendMessage("§a已将 " + finalPlayerName + " 的称号 §f" + titleId + " §a转换为 " + count + " 张称号卡。");
                }));
    }
    private void addReward(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage("§e用法：/dctitle addReward <称号数量> <类型 vault|playerpoints|coin> <金额>");
            return;
        }
        int number = parseInt(args[1], 0);
        if (number < 1) {
            sender.sendMessage("§c称号数量必须大于 0。");
            return;
        }
        com.dracave.title.model.RewardType type;
        try {
            type = com.dracave.title.model.RewardType.parse(args[2]);
        } catch (IllegalArgumentException ex) {
            sender.sendMessage("§c未知奖励类型：" + args[2]);
            return;
        }
        long amount = parseLong(args[3]);
        if (amount <= 0) {
            sender.sendMessage("§c金额必须大于 0。");
            return;
        }
        long id = UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
        com.dracave.title.model.Reward reward = new com.dracave.title.model.Reward(id, number, type, amount);
        SchedulerUtil.runTaskAsynchronously(plugin, () -> {
            try {
                plugin.rewardRepository().add(reward);
                plugin.messages().send(sender, "reward-configured",
                        Messages.text("number", Integer.toString(number)),
                        Messages.text("amount", Long.toString(amount)),
                        Messages.text("type", type.id()));
            } catch (Exception ex) {
                plugin.messages().send(sender, "operation-failed");
            }
        });
    }
    private void randomCard(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "player-only");
            return;
        }
        if (args.length < 3) {
            sender.sendMessage("§e用法：/dctitle randomCard <货币类型 vault|playerpoints|coin|item> <天数>（0 为永久）");
            return;
        }
        CurrencyType currency;
        try {
            currency = CurrencyType.parse(args[1]);
        } catch (IllegalArgumentException ex) {
            sender.sendMessage("§c未知货币类型：" + args[1]);
            return;
        }
        int days = parseInt(args[2], 0);
        player.getInventory().addItem(plugin.cardService().randomCard(currency, days));
        plugin.messages().send(player, "card-given", Messages.text("type", currency.id()), Messages.text("days", Integer.toString(days)));
    }
    private void setCustom(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§e用法：/dctitle setCustom <玩家名> <次数>");
            return;
        }
        int quota = parseInt(args[2], -1);
        if (quota < 0) {
            sender.sendMessage("§c次数必须大于等于 0。");
            return;
        }
        UUID targetId = resolvePlayerId(args[1], sender);
        if (targetId == null) {
            return;
        }
        SchedulerUtil.runTaskAsynchronously(plugin, () -> {
            try {
                plugin.quotaRepository().setQuota(targetId, quota);
                plugin.messages().send(sender, "quota-set", Messages.text("player", args[1]),
                        Messages.text("quota", quota == 0 ? "无上限（由权限决定）" : Integer.toString(quota)));
            } catch (Exception ex) {
                plugin.messages().send(sender, "operation-failed");
            }
        });
    }
    private void addCustom(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§e用法：/dctitle addCustom <玩家名> <次数>");
            return;
        }
        int quota = parseInt(args[2], 0);
        if (quota <= 0) {
            sender.sendMessage("§c次数必须大于 0。");
            return;
        }
        UUID targetId = resolvePlayerId(args[1], sender);
        if (targetId == null) {
            return;
        }
        SchedulerUtil.runTaskAsynchronously(plugin, () -> {
            try {
                plugin.quotaRepository().addQuota(targetId, quota);
                plugin.messages().send(sender, "quota-added", Messages.text("player", args[1]),
                        Messages.text("quota", Integer.toString(quota)));
            } catch (Exception ex) {
                plugin.messages().send(sender, "operation-failed");
            }
        });
    }
    private void upload(CommandSender sender, String[] args) {
        if (!sender.hasPermission("dracave.title.admin.upload")) {
            plugin.messages().send(sender, "no-permission");
            return;
        }
        String mode = "data";
        boolean checkOnly = false;
        for (int i = 1; i < args.length; i++) {
            if (args[i].equalsIgnoreCase("--check")) {
                checkOnly = true;
            } else if (args[i].equalsIgnoreCase("all")) {
                mode = "all";
            } else if (args[i].equalsIgnoreCase("data")) {
                mode = "data";
            }
        }
        if ("all".equals(mode)) {
            final boolean check = checkOnly;
            var future = check ? plugin.definitionService().checkUpload() : plugin.definitionService().upload();
            future.thenAccept(result -> SchedulerUtil.runTask(plugin, () -> {
                if (result.valid()) {
                    if (check) {
                        sender.sendMessage("§a校验通过，共 " + result.count() + " 个称号。");
                    } else {
                        sender.sendMessage("§a上传完成：新增 " + result.inserted() + " 个，更新 " + result.updated() + " 个，已拆分为 tags/ 文件。");
                    }
                } else {
                    sender.sendMessage("§c上传/校验失败：");
                    for (String error : result.errors()) {
                        sender.sendMessage("§c- " + error);
                    }
                }
            }));
        } else {
            plugin.definitionService().sync().thenAccept(result -> SchedulerUtil.runTask(plugin, () -> {
                if (result.valid()) {
                    sender.sendMessage("§a同步完成，已从数据库加载 " + result.count() + " 个称号，已覆写 titles.yml 和 tags/ 文件。");
                } else {
                    sender.sendMessage("§c同步失败：");
                    for (String error : result.errors()) {
                        sender.sendMessage("§c- " + error);
                    }
                }
            }));
        }
    }
    private void panel(CommandSender sender, String[] args, boolean byId) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "player-only");
            return;
        }
        if (!player.hasPermission("dracave.title.admin.panel")) {
            plugin.messages().send(player, "no-permission");
            return;
        }
        List<TitleDefinition> titles = plugin.registry().configured();
        if (titles.isEmpty()) {
            plugin.messages().send(player, "gui.no-title-data");
            return;
        }
        TitleDefinition target;
        if (args.length < 2) {
            target = titles.get(0);
        } else if (byId) {
            target = plugin.registry().get(args[1].toLowerCase(Locale.ROOT));
        } else {
            String text = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            target = titles.stream()
                    .filter(t -> TitleRenderer.plain(t, System.currentTimeMillis()).equals(text))
                    .findFirst().orElse(titles.stream()
                            .filter(t -> t.display().equals(text))
                            .findFirst().orElse(null));
        }
        if (target == null) {
            plugin.messages().send(player, "unknown-title", Messages.text("id", String.join(" ", Arrays.copyOfRange(args, 1, args.length))));
            return;
        }
        plugin.adminPanel().openEditor(player, target.id(), TitleAdminPanel.EditorReturn.COMMAND, 0);
    }
    private void panelEdit(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "player-only");
            return;
        }
        if (!player.hasPermission("dracave.title.admin.panel")) {
            plugin.messages().send(player, "no-permission");
            return;
        }
        if (args.length < 3 || !plugin.adminPanel().ownsDraft(player, args[1])) {
            sender.sendMessage("§e用法：/dctitle panel-edit <称号ID> <操作> <参数...>（先 /dctitle panel-id <ID> 打开面板）");
            return;
        }
        String titleId = args[1].toLowerCase(Locale.ROOT);
        String operation = args[2].toLowerCase(Locale.ROOT);
        switch (operation) {
            case "text" -> {
                if (args.length < 4) {
                    sender.sendMessage("§e用法：panel-edit <ID> text <新文本>");
                    return;
                }
                String text = mini.escapeTags(String.join(" ", Arrays.copyOfRange(args, 3, args.length)));
                if (text.codePointCount(0, text.length()) > 64) {
                    sender.sendMessage("§c文本过长。");
                    return;
                }
                modifyDefinition(sender, titleId, definition -> new TitleDefinition(definition.id(), text,
                        definition.description(), definition.icon(), definition.order(), definition.defaultUnlocked(),
                        definition.permission(), definition.animation(), definition.purchaseOffer(), definition.colors(),
                        definition.shopHidden(), definition.potionEffects(), definition.particle(), definition.revision()),
                        "panel-updated");
            }
            case "price" -> {
                if (args.length < 4) {
                    sender.sendMessage("§e用法：panel-edit <ID> price <金额|none>");
                    return;
                }
                String priceValue = args[3];
                modifyDefinition(sender, titleId, definition -> {
                    if (priceValue.equalsIgnoreCase("none")) {
                        return new TitleDefinition(definition.id(), definition.display(), definition.description(),
                                definition.icon(), definition.order(), definition.defaultUnlocked(), definition.permission(),
                                definition.animation(), null, definition.colors(), definition.shopHidden(),
                                definition.potionEffects(), definition.particle(), definition.revision());
                    }
                    return new TitleDefinition(definition.id(), definition.display(), definition.description(),
                            definition.icon(), definition.order(), definition.defaultUnlocked(), definition.permission(),
                            definition.animation(), new TitlePurchaseOffer(
                            definition.purchaseOffer() == null ? CurrencyType.VAULT : definition.purchaseOffer().currency(),
                            new BigDecimal(priceValue)), definition.colors(), definition.shopHidden(),
                            definition.potionEffects(), definition.particle(), definition.revision());
                }, "panel-updated");
            }
            default -> sender.sendMessage("§e支持的操作：text <文本> / price <金额|none>；其他请使用管理面板 GUI。");
        }
    }
    private void help(CommandSender sender) {
        boolean admin = sender.hasPermission("dracave.title.admin");
        sender.sendMessage("§e§m-------------§f[§eDraCaveTitle§f]§e§m-------------");
        sender.sendMessage("§e/dctitle open §f打开称号仓库");
        sender.sendMessage("§e/dctitle shop §f打开称号商店");
        sender.sendMessage("§e/dctitle custom [称号名称] §f自定义称号");
        sender.sendMessage("§e/dctitle wear <ID|none> §f穿戴/卸下称号");
        sender.sendMessage("§e/dctitle clear §f卸下当前称号");
        sender.sendMessage("§e/dctitle view [类型] (玩家名) §f查看称号列表");
        sender.sendMessage("§e/dctitle reward §f奖励中心");
        sender.sendMessage("§e/dctitle ranking §f称号数量排行榜");
        if (admin) {
            sender.sendMessage("§e/dctitle adminShop §f管理称号商店");
            sender.sendMessage("§e/dctitle add <货币> <名称> <价格> [天数] [隐藏] [玩家名] §f创建称号");
            sender.sendMessage("§e/dctitle set <玩家> <ID> [天数] §f设置并穿戴称号");
            sender.sendMessage("§e/dctitle addPlayerTitle <玩家> <称号名称或ID> [天数] §f发放称号");
            sender.sendMessage("§e/dctitle del <ID> §f删除称号");
            sender.sendMessage("§e/dctitle setDescription <ID> <描述> §f设置描述");
            sender.sendMessage("§e/dctitle addPermission <称号名称或ID> <权限> §f设置购买权限");
            sender.sendMessage("§e/dctitle setTitleBuff <ID> POTION_EFFECT <效果> [等级] §f添加药水加成");
            sender.sendMessage("§e/dctitle delBuff <ID> <效果> §f删除药水加成");
            sender.sendMessage("§e/dctitle setTitleParticle <ID> <粒子> [id] (颜色)… §f设置粒子");
            sender.sendMessage("§e/dctitle removeTitleParticle <ID> §f移除粒子");
            sender.sendMessage("§e/dctitle addCoin <玩家> <金额> §f增加称号币");
            sender.sendMessage("§e/dctitle subtractCoin <玩家> <金额> §f扣除称号币");
            sender.sendMessage("§e/dctitle changeItem <ID> <天数> <数量> [玩家] §f称号转物品卡");
            sender.sendMessage("§e/dctitle addReward <数量> <类型> <金额> §f配置里程碑奖励");
            sender.sendMessage("§e/dctitle randomCard <货币> <天数> §f生成随机称号卡");
            sender.sendMessage("§e/dctitle setCustom <玩家> <次数> §f设置自定义额度");
            sender.sendMessage("§e/dctitle addCustom <玩家> <次数> §f追加自定义额度");
            sender.sendMessage("§e/dctitle panel-id <ID> §f打开编辑面板");
            sender.sendMessage("§e/dctitle panel <称号文本> §f按名称打开编辑面板");
            sender.sendMessage("§e/dctitle panel-edit <ID> <操作> <参数> §f命令行编辑称号");
            sender.sendMessage("§e/dctitle upload [data|all] [--check] §f同步数据库→titles.yml+tags(data)或上传titles.yml→数据库+tags(all)");
            sender.sendMessage("§e/dctitle convert <MYSQL|SQLITE> §f数据转换");
            sender.sendMessage("§e/dctitle reload §f重载配置");
            sender.sendMessage("§e/dctitle listTitle §f列出全部称号");
        }
    }
    private void requirePlayer(CommandSender sender, java.util.function.Consumer<Player> action) {
        if (sender instanceof Player player) {
            action.accept(player);
        } else {
            plugin.messages().send(sender, "player-only");
        }
    }
    private String resolveTitleId(String value) {
        if (plugin.registry().get(value) != null) {
            return value.toLowerCase(Locale.ROOT);
        }
        return plugin.registry().all().stream()
                .filter(t -> t.display().equals(value) || TitleRenderer.plain(t, System.currentTimeMillis()).equals(value))
                .map(TitleDefinition::id)
                .findFirst()
                .orElse(value.toLowerCase(Locale.ROOT));
    }
    private UUID resolvePlayerId(String playerName, CommandSender sender) {
        Player online = Bukkit.getPlayerExact(playerName);
        if (online != null) {
            return online.getUniqueId();
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(playerName);
        if (!offline.hasPlayedBefore()) {
            plugin.messages().send(sender, "unknown-player", Messages.text("player", playerName));
            return null;
        }
        return offline.getUniqueId();
    }
    private String renderedTitle(String titleId) {
        TitleDefinition title = plugin.registry().get(titleId);
        return title == null ? titleId : TitleRenderer.miniMessage(title, System.currentTimeMillis());
    }
    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
    private static long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return -1;
        }
    }
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        if (args.length == 0) {
            return List.of();
        }
        if (args.length == 1) {
            List<String> values = new ArrayList<>(List.of("open", "shop", "custom", "wear", "clear", "view", "reward", "ranking"));
            if (sender.hasPermission("dracave.title.admin")) {
                values.addAll(List.of("listTitle", "adminShop", "add", "create", "del", "set", "addPlayerTitle", "addCoin",
                        "subtractCoin", "changeItem", "addReward", "randomCard", "setCustom", "addCustom",
                        "setDescription", "addPermission", "setTitleBuff", "delBuff", "setTitleParticle",
                        "removeTitleParticle", "convert", "reload", "panel", "panel-id", "panel-edit", "upload"));
            }
            return filter(values, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "custom" -> {
                if (args.length == 2) {
                    return filter(List.of("create", "edit", "delete"), args[1]);
                }
                String action = args[1].toLowerCase(Locale.ROOT);
                if ((action.equals("create") || action.equals("edit")) && args.length == (action.equals("edit") ? 4 : 3)) {
                    return filter(customTypes(sender), args[args.length - 1]);
                }
                return List.of();
            }
            case "wear" -> {
                if (args.length == 2 && sender instanceof Player player) {
                    PlayerData data = plugin.service() == null ? null : plugin.service().getCached(player.getUniqueId());
                    if (data == null) {
                        return List.of();
                    }
                    List<String> values = new ArrayList<>(data.unlocked());
                    values.add("none");
                    return filter(values, args[1]);
                }
                return List.of();
            }
            case "create" -> {
                if (args.length == 2) {
                    return filter(List.of("[称号文本]"), args[1]);
                }
                if (args.length == 3) {
                    return filter(List.of("#", "#FF0000,#0000FF"), args[2]);
                }
                if (args.length == 4) {
                    return filter(List.of("vault", "coin", "point", "item"), args[3]);
                }
                if (args.length == 5) {
                    return filter(List.of("1000", "5000", "10000"), args[4]);
                }
                if (args.length == 6) {
                    java.util.List<String> items = new java.util.ArrayList<>(
                    java.util.Arrays.stream(org.bukkit.Material.values()).filter(org.bukkit.Material::isItem)
                            .map(m -> "minecraft:" + m.name().toLowerCase(java.util.Locale.ROOT)).toList());
                items.addAll(com.dracave.title.util.CustomItemProvider.allItemIds());
                return filter(items, args[5]);
                }
                return List.of();
            }
            case "add" -> {
                if (args.length == 2) {
                    return filter(List.of("vault", "playerpoints", "coin", "item"), args[1]);
                }
                if (args.length == 3) {
                    return filter(List.of("[称号名称]"), args[2]);
                }
                if (args.length == 4) {
                    return filter(List.of("1000", "5000", "10000"), args[3]);
                }
                if (args.length == 5) {
                    return filter(List.of("0", "7", "30", "365"), args[4]);
                }
                if (args.length == 6) {
                    return filter(List.of("true", "false"), args[5]);
                }
                if (args.length == 7) {
                    return filter(onlinePlayers(), args[6]);
                }
                return List.of();
            }
            case "del", "set", "addplayertitle", "setdescription", "addpermission", "settitlebuff", "delbuff",
                    "settitleparticle", "removetitleparticle", "changeitem" -> {
                if (args.length == 2) {
                    if (sub.equals("set") || sub.equals("addplayertitle")) {
                        return filter(onlinePlayers(), args[1]);
                    }
                    if (sub.equals("addpermission")) {
                        return filter(titleIdsAndNames(), args[1]);
                    }
                    return filter(titleIds(), args[1]);
                }
                if (args.length == 3 && (sub.equals("set") || sub.equals("addplayertitle"))) {
                    if (sub.equals("addplayertitle")) {
                        return filter(titleIdsAndNames(), args[2]);
                    }
                    return filter(titleIds(), args[2]);
                }
                if (args.length == 3 && (sub.equals("setdescription") || sub.equals("addpermission"))) {
                    return filter(List.of("[内容]"), args[2]);
                }
                if (args.length == 3 && sub.equals("settitlebuff")) {
                    return filter(List.of("POTION_EFFECT"), args[2]);
                }
                if (args.length == 4 && sub.equals("settitlebuff")) {
                    return filter(potionNames(), args[3]);
                }
                if (args.length == 3 && sub.equals("changeitem")) {
                    return filter(List.of("0", "7", "30"), args[2]);
                }
                if (args.length == 4 && sub.equals("changeitem")) {
                    return filter(List.of("1", "5", "10"), args[3]);
                }
                return List.of();
            }
            case "setcustom", "addcustom", "addcoin", "subtractcoin" -> {
                if (args.length == 2) {
                    return filter(onlinePlayers(), args[1]);
                }
                if (args.length == 3 && (sub.equals("setcustom") || sub.equals("addcustom"))) {
                    return filter(List.of("1", "5", "10"), args[2]);
                }
                if (args.length == 3 && (sub.equals("addcoin") || sub.equals("subtractcoin"))) {
                    return filter(List.of("1000", "5000", "10000"), args[2]);
                }
                return List.of();
            }
            case "randomcard", "addreward" -> {
                if (args.length == 2) {
                    return filter(List.of("vault", "playerpoints", "coin", "item"), args[1]);
                }
                if (args.length == 3) {
                    return filter(List.of("0", "7", "30", "365"), args[2]);
                }
                return List.of();
            }
            case "convert" -> {
                if (args.length == 2) {
                    return filter(List.of("MYSQL", "SQLITE"), args[1]);
                }
                return List.of();
            }
            case "upload" -> {
                if (args.length == 2) {
                    return filter(List.of("data", "all", "--check"), args[1]);
                }
                if (args.length == 3 && args[1].equalsIgnoreCase("all")) {
                    return filter(List.of("--check"), args[2]);
                }
                return List.of();
            }
            case "view" -> {
                if (args.length == 2) {
                    return filter(List.of("shop", "reward"), args[1]);
                }
                if (args.length == 3) {
                    return filter(onlinePlayers(), args[2]);
                }
                return List.of();
            }
            case "panel" -> {
                if (args.length == 2) {
                    return filter(plugin.registry().configured().stream()
                            .map(title -> TitleRenderer.plain(title, System.currentTimeMillis())).distinct().toList(), args[1]);
                }
                return List.of();
            }
            case "panel-id", "panel-edit" -> {
                if (args.length == 2) {
                    return filter(titleIds(), args[1]);
                }
                if (sub.equals("panel-edit") && args.length == 3) {
                    return filter(List.of("text", "price"), args[2]);
                }
                return List.of();
            }
            default -> {
            }
        }
        return List.of();
    }
    private List<String> customTypes(CommandSender sender) {
        List<String> types = new ArrayList<>();
        if (senderHasPermission(sender, "dracave.title.custom.static")) {
            types.add("static");
        }
        if (senderHasPermission(sender, "dracave.title.custom.dynamic")) {
            types.addAll(List.of("gradient", "rainbow", "flash", "frames"));
        }
        return types.isEmpty() ? List.of("static", "gradient", "rainbow", "flash", "frames") : types;
    }
    private boolean senderHasPermission(CommandSender sender, String permission) {
        return sender == null || sender.isOp() || sender.hasPermission(permission);
    }
    private List<String> potionNames() {
        return Arrays.stream(PotionEffectType.values())
                .filter(java.util.Objects::nonNull)
                .map(PotionEffectType::getName)
                .sorted()
                .toList();
    }
    private List<String> titleIds() {
        List<String> ids = plugin.registry().all().stream().map(TitleDefinition::id).toList();
        return ids.isEmpty() ? List.of("source-born") : ids;
    }
    private List<String> titleIdsAndNames() {
        List<String> result = new ArrayList<>();
        for (TitleDefinition title : plugin.registry().all()) {
            result.add(title.id());
            result.add(title.display());
            result.add(TitleRenderer.plain(title, System.currentTimeMillis()));
        }
        return new ArrayList<>(new LinkedHashSet<>(result));
    }
    private List<String> onlinePlayers() {
        List<String> players = Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        return players.isEmpty() ? List.of("玩家名") : players;
    }
    private static List<String> filter(List<String> values, String input) {
        String prefix = input.toLowerCase(Locale.ROOT);
        return new LinkedHashSet<>(values).stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix))
                .sorted()
                .toList();
    }
}
