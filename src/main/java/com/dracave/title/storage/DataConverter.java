package com.dracave.title.storage;
import com.dracave.title.DraCaveTitlePlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
public final class DataConverter {
    private final DraCaveTitlePlugin plugin;
    public DataConverter(DraCaveTitlePlugin plugin) {
        this.plugin = plugin;
    }
    public int convert(String targetMethod) throws Exception {
        FileConfiguration config = plugin.getConfig();
        String current = config.getString("storage.type", "MYSQL");
        String normalized = targetMethod.equalsIgnoreCase("SQLITE") ? "SQLITE" : "MYSQL";
        if (normalized.equals(current.toUpperCase())) {
            throw new IllegalArgumentException("当前存储方式已经是 " + current + "，无需转换");
        }
        FileConfiguration targetConfig = new YamlConfiguration();
        targetConfig.set("storage.type", normalized);
        targetConfig.set("storage.mysql.host", config.getString("storage.mysql.host", "127.0.0.1"));
        targetConfig.set("storage.mysql.port", config.getInt("storage.mysql.port", 3306));
        targetConfig.set("storage.mysql.database", config.getString("storage.mysql.database", "minecraft"));
        targetConfig.set("storage.mysql.user", config.getString("storage.mysql.user", "root"));
        targetConfig.set("storage.mysql.password", config.getString("storage.mysql.password", ""));
        targetConfig.set("storage.mysql.params", config.getString("storage.mysql.params",
                "?useSSL=false&characterEncoding=utf8&serverTimezone=UTC&allowPublicKeyRetrieval=true"));
        targetConfig.set("storage.mysql.table-prefix", config.getString("storage.mysql.table-prefix", "dracavetitle_"));
        targetConfig.set("storage.mysql.pool-size", config.getInt("storage.mysql.pool-size", 4));
        targetConfig.set("storage.sqlite.file", config.getString("storage.sqlite.file", "data.db"));
        int copied = 0;
        TitleDatabase source = plugin.database();
        try (TitleDatabase target = new TitleDatabase(targetConfig, plugin.getDataFolder())) {
            List<String> tables = tableNames(source);
            for (String table : tables) {
                copied += copyTable(source, target, table);
            }
        }
        return copied;
    }
    private List<String> tableNames(TitleDatabase database) throws SQLException {
        List<String> tables = new ArrayList<>();
        tables.add(database.playerTable());
        tables.add(database.unlockTable());
        tables.add(database.purchaseTable());
        tables.add(database.customTitleTable());
        tables.add(database.definitionTable());
        tables.add(database.descriptionTable());
        tables.add(database.colorTable());
        tables.add(database.effectTable());
        tables.add(database.frameTable());
        tables.add(database.coinTable());
        tables.add(database.quotaTable());
        tables.add(database.rewardTable());
        tables.add(database.rewardLogTable());
        return tables;
    }
    private int copyTable(TitleDatabase source, TitleDatabase target, String table) throws SQLException {
        try (Connection targetConnection = target.dataSource().getConnection();
             Statement clear = targetConnection.createStatement()) {
            clear.executeUpdate("DELETE FROM `" + table + "`");
        }
        List<String> columns;
        List<List<Object>> rows = new ArrayList<>();
        try (Connection sourceConnection = source.dataSource().getConnection();
             Statement statement = sourceConnection.createStatement();
             ResultSet result = statement.executeQuery("SELECT * FROM `" + table + "`")) {
            ResultSetMetaData meta = result.getMetaData();
            int columnCount = meta.getColumnCount();
            columns = new ArrayList<>();
            for (int i = 1; i <= columnCount; i++) {
                columns.add(meta.getColumnLabel(i));
            }
            while (result.next()) {
                List<Object> row = new ArrayList<>();
                for (int i = 1; i <= columnCount; i++) {
                    row.add(result.getObject(i));
                }
                rows.add(row);
            }
        }
        if (rows.isEmpty()) {
            return 0;
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(columns.size(), "?"));
        String sql = "INSERT INTO `" + table + "` (`" + String.join("`,`", columns) + "`) VALUES (" + placeholders + ")";
        try (Connection targetConnection = target.dataSource().getConnection();
             PreparedStatement statement = targetConnection.prepareStatement(sql)) {
            for (List<Object> row : rows) {
                for (int i = 0; i < row.size(); i++) {
                    statement.setObject(i + 1, row.get(i));
                }
                statement.addBatch();
            }
            statement.executeBatch();
        }
        return rows.size();
    }
}
