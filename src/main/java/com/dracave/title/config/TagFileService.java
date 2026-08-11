package com.dracave.title.config;
import com.dracave.title.model.TitleDefinition;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
/**
 * 管理 tags/ 文件夹下的标签文件读写。
 * <p>
 * 运行时从 tags/ 文件夹加载称号定义；upload 时将定义拆分为单独的 .yml 文件写入。
 * 标签文件不会被实时修改，仅在管理员执行 upload 命令时由系统统一覆写。
 */
public final class TagFileService {
    private static final String YML_EXT = ".yml";
    private final JavaPlugin plugin;
    private final TitleYamlParser parser;
    private final TitlesYamlWriter writer;
    private final File tagsFolder;
    public TagFileService(JavaPlugin plugin, TitleYamlParser parser, TitlesYamlWriter writer) {
        this.plugin = plugin;
        this.parser = parser;
        this.writer = writer;
        this.tagsFolder = new File(plugin.getDataFolder(), "tags");
    }
    public File tagsFolder() {
        return tagsFolder;
    }
    public boolean hasTags() {
        File[] files = listYmlFiles();
        return files != null && files.length > 0;
    }
    public List<TitleDefinition> loadAll() {
        if (!tagsFolder.exists()) {
            return List.of();
        }
        File[] files = listYmlFiles();
        if (files == null || files.length == 0) {
            return List.of();
        }
        Arrays.sort(files, Comparator.comparing(File::getName));
        List<TitleDefinition> definitions = new ArrayList<>();
        for (File file : files) {
            String id = file.getName().substring(0, file.getName().length() - YML_EXT.length());
            TitleYamlParser.ParseResult result = parser.parseSingle(id, file);
            definitions.addAll(result.definitions());
            for (String error : result.errors()) {
                plugin.getLogger().warning("标签文件解析错误: " + error);
            }
        }
        return definitions;
    }
    public void writeAll(List<TitleDefinition> definitions) {
        if (!tagsFolder.exists()) {
            tagsFolder.mkdirs();
        }
        File[] oldFiles = listYmlFiles();
        if (oldFiles != null) {
            for (File file : oldFiles) {
                if (!file.delete()) {
                    plugin.getLogger().warning("无法删除旧标签文件: " + file.getName());
                }
            }
        }
        for (TitleDefinition def : definitions) {
            String fileName = def.id().toLowerCase(Locale.ROOT) + YML_EXT;
            File file = new File(tagsFolder, fileName);
            try {
                writer.writeSingle(def, file);
            } catch (RuntimeException ex) {
                plugin.getLogger().severe("写入标签文件失败 " + fileName + ": " + ex.getMessage());
            }
        }
        plugin.getLogger().info("已拆分 " + definitions.size() + " 个称号到 tags/ 文件夹");
    }
    private File[] listYmlFiles() {
        return tagsFolder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(YML_EXT));
    }
}
