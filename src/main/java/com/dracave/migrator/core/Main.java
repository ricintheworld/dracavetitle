package com.dracave.migrator.core;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
public final class Main {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("用法: DraCaveTitle 迁移 [PlayerTitle.db] [--config <config.yml>]"
                    + " [--data-folder <dir>] [--scope titles|data] [--server-folder <服务器根目录>]"
                    + " [--mode static|dynamic] [--report <out>]");
            System.exit(2);
            return;
        }
        File sourceDb = new File(args[0]);
        if (!sourceDb.isFile()) {
            System.err.println("源库不存在: " + sourceDb.getAbsolutePath());
            System.exit(2);
            return;
        }
        File configFile = null;
        File dataFolder = null;
        File serverFolder = null;
        File reportFile = null;
        File titlesYml = null;
        MigrateConfig.Mode mode = MigrateConfig.Mode.STATIC;
        MigrateConfig.Scope scope = MigrateConfig.Scope.TITLES;
        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--config" -> configFile = new File(args[++i]);
                case "--data-folder" -> dataFolder = new File(args[++i]);
                case "--titles" -> titlesYml = new File(args[++i]);
                case "--server-folder" -> serverFolder = new File(args[++i]);
                case "--scope" -> {
                    String s = args[++i];
                    scope = "data".equalsIgnoreCase(s) || "db".equalsIgnoreCase(s)
                            ? MigrateConfig.Scope.DATA : MigrateConfig.Scope.TITLES;
                }
                case "--mode" -> {
                    String m = args[++i];
                    mode = "dynamic".equalsIgnoreCase(m) || "color".equalsIgnoreCase(m)
                            ? MigrateConfig.Mode.DYNAMIC : MigrateConfig.Mode.STATIC;
                }
                case "--report" -> reportFile = new File(args[++i]);
                default -> {
                    System.err.println("未知参数: " + args[i]);
                    System.exit(2);
                }
            }
        }
        String sourceUrl = "jdbc:sqlite:" + sourceDb.getAbsolutePath();
        MigrateConfig.TargetDb target = resolveTarget(configFile, dataFolder);
        File resolvedDataFolder = dataFolder != null ? dataFolder : new File("plugins/DraCaveTitle");
        if (titlesYml == null) {
            titlesYml = new File(resolvedDataFolder, "titles.yml");
        }
        UuidResolver resolver = new UuidResolver();
        if (serverFolder != null) {
            UuidResolver.loadLocal(resolver, serverFolder);
            System.out.println("本地 UUID 源: " + resolver.size() + " 个玩家（usercache/XConomy/LuckPerms，不联网）");
        }
        if (scope == MigrateConfig.Scope.DATA && resolver.isEmpty()) {
            System.out.println("外部 UUID 源不可用，将尝试从源库 player_uuid 列获取…");
        }
        System.out.println("源库: " + sourceUrl);
        System.out.println("目标: " + target.url() + "  前缀: " + target.prefix());
        System.out.println("执行范围: " + (scope == MigrateConfig.Scope.TITLES ? "titles（定义）" : "data（玩家数据）")
                + " | 渐变: " + (mode == MigrateConfig.Mode.DYNAMIC ? "动态" : "静态"));
        MigrateConfig config = MigrateConfig.builder()
                .source(sourceUrl, "", "")
                .target(target.url(), target.user(), target.password(), target.prefix())
                .dryRun(false)
                .mode(mode)
                .scope(scope)
                .backup(new File(sourceDb.getParentFile(), "migrator-backup"), target.sqliteFile())
                .titlesYml(titlesYml)
                .build();
        Migrator migrator = new Migrator(config);
        migrator.setResolver(resolver);
        TitleData.MigrationReport report = migrator.run();
        StringBuilder sb = new StringBuilder();
        sb.append("=== 迁移结果 ===\n").append(report.summary()).append('\n');
        if (!migrator.warnings().isEmpty()) {
            sb.append("--- 注意 ---\n");
            for (String w : migrator.warnings()) {
                sb.append("  ! ").append(w).append('\n');
            }
        }
        System.out.println(sb);
        if (reportFile != null) {
            Files.writeString(reportFile.toPath(), sb.toString(), StandardCharsets.UTF_8);
            System.out.println("报告已写入: " + reportFile.getAbsolutePath());
        }
    }
    private static MigrateConfig.TargetDb resolveTarget(File configFile, File dataFolder) {
        File config = configFile != null ? configFile
                : new File("plugins/DraCaveTitle/config.yml");
        File folder = dataFolder != null ? dataFolder
                : new File("plugins/DraCaveTitle");
        if (config.isFile()) {
            return MigrateConfig.fromDraCaveConfig(config.getAbsolutePath(), folder);
        }
        File db = new File(folder, "data.db");
        return new MigrateConfig.TargetDb("jdbc:sqlite:" + db.getAbsolutePath(), "", "", "dracavetitle_", db, true);
    }
}
