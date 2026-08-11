package com.dracave.migrator.command;
import com.dracave.title.DraCaveTitlePlugin;
import com.dracave.title.util.SchedulerUtil;
import com.dracave.migrator.core.MigrateConfig;
import com.dracave.migrator.core.Migrator;
import com.dracave.migrator.core.TitleData;
import com.dracave.migrator.core.UuidResolver;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import java.io.File;
import java.util.List;
import java.util.Locale;
public final class MigrateCommand implements CommandExecutor, TabCompleter {
    private final DraCaveTitlePlugin plugin;
    public MigrateCommand(DraCaveTitlePlugin plugin) {
        this.plugin = plugin;
    }
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("ttt.use")) {
            sender.sendMessage("§c你没有权限使用此命令");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage("§e/ttt title null [源库] —— 静态迁移（渐变/粗体/乱码直写数据库）");
            sender.sendMessage("§e/ttt title color [源库] —— 动态渐变迁移（生成 titles.yml 可 upload）");
            sender.sendMessage("§e/ttt db [源库] —— 迁移玩家数据（UUID 本地获取，不联网）");
            return true;
        }
        final MigrateConfig.Scope scope;
        final MigrateConfig.Mode mode;
        String modeArg = "";
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "title" -> {
                scope = MigrateConfig.Scope.TITLES;
                if (args.length < 2) {
                    sender.sendMessage("§e用法: /ttt title <null|color> [源库]");
                    return true;
                }
                modeArg = args[1].toLowerCase(Locale.ROOT);
                mode = "color".equals(modeArg) || "dynamic".equals(modeArg)
                        ? MigrateConfig.Mode.DYNAMIC : MigrateConfig.Mode.STATIC;
            }
            case "db" -> {
                scope = MigrateConfig.Scope.DATA;
                mode = MigrateConfig.Mode.STATIC;
            }
            default -> {
                sender.sendMessage("§c未知命令: " + args[0] + "（用 title 或 db）");
                return true;
            }
        }
        int pathIndex = scope == MigrateConfig.Scope.TITLES ? 2 : 1;
        String sourcePath = "plugins/PlayerTitle/PlayerTitle.db";
        for (int i = pathIndex; i < args.length; i++) {
            String a = args[i];
            if (!a.startsWith("--")) {
                sourcePath = a;
            }
        }
        File sourceDb = new File(sourcePath);
        if (!sourceDb.isFile()) {
            sender.sendMessage("§c源库不存在: " + sourceDb.getAbsolutePath());
            return true;
        }
        File serverFolder = plugin.getDataFolder().getParentFile().getParentFile();
        UuidResolver resolver = new UuidResolver();
        UuidResolver.loadLocal(resolver, serverFolder);
        if (scope == MigrateConfig.Scope.DATA && resolver.isEmpty()) {
            sender.sendMessage("§e外部 UUID 源不可用，将尝试从源库 player_uuid 列获取…");
        }
        File dataFolder = plugin.getDataFolder();
        MigrateConfig.TargetDb target = MigrateConfig.fromDraCaveConfig(
                new File(dataFolder, "config.yml").getAbsolutePath(), dataFolder);
        MigrateConfig config = MigrateConfig.builder()
                .source("jdbc:sqlite:" + sourceDb.getAbsolutePath(), "", "")
                .target(target.url(), target.user(), target.password(), target.prefix())
                .dryRun(false)
                .scope(scope)
                .mode(mode)
                .backup(new File(plugin.getDataFolder(), "backup"), target.sqliteFile())
                .titlesYml(new File(dataFolder, "titles.yml"))
                .build();
        String what = scope == MigrateConfig.Scope.TITLES
                ? (mode == MigrateConfig.Mode.DYNAMIC ? "动态渐变定义（color）" : "静态定义（null）")
                : "玩家数据";
        sender.sendMessage("§e开始迁移" + what + "，UUID 本地获取不联网…");
        SchedulerUtil.runTaskAsynchronously(plugin, () -> {
            TitleData.MigrationReport report = null;
            String fail = null;
            try {
                Migrator migrator = new Migrator(config);
                migrator.setResolver(resolver);
                report = migrator.run();
            } catch (Exception ex) {
                fail = ex.getClass().getSimpleName() + ": " + ex.getMessage();
            }
            final TitleData.MigrationReport result = report;
            final String error = fail;
            SchedulerUtil.runTask(plugin, () -> {
                if (error != null) {
                    sender.sendMessage("§c迁移失败: " + error);
                    return;
                }
                sender.sendMessage("§a迁移完成: " + result.summary());
                plugin.getLogger().info("迁移报告: " + result.summary());
                if (scope == MigrateConfig.Scope.TITLES) {
                    sender.sendMessage("§e已生成 titles.yml，执行 /dctitle upload 导入定义（静态/动态按所选模式生效）");
                }
            });
        });
        return true;
    }
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("title", "db");
        }
        if (args.length == 2 && "title".equalsIgnoreCase(args[0])) {
            return List.of("null", "color");
        }
        return List.of();
    }
}
