package com.dracave.title.storage;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.file.FileConfiguration;
import java.io.File;
import java.sql.Connection;
import java.sql.Statement;
public final class TitleDatabase implements AutoCloseable {
    private final HikariDataSource dataSource;
    private final boolean sqlite;
    private final String playerTable;
    private final String unlockTable;
    private final String purchaseTable;
    private final String customTitleTable;
    private final String schemaVersionTable;
    private final String definitionTable;
    private final String descriptionTable;
    private final String colorTable;
    private final String effectTable;
    private final String frameTable;
    private final String coinTable;
    private final String quotaTable;
    private final String rewardTable;
    private final String rewardLogTable;
    public TitleDatabase(FileConfiguration config, File dataFolder) throws Exception {
        String prefix = sanitize(config.getString("storage.mysql.table-prefix", "dracavetitle_"));
        playerTable = prefix + "player";
        unlockTable = prefix + "unlock";
        purchaseTable = prefix + "purchase";
        customTitleTable = prefix + "custom_title";
        schemaVersionTable = prefix + "schema_version";
        definitionTable = prefix + "title_definition";
        descriptionTable = prefix + "title_description";
        colorTable = prefix + "title_color";
        effectTable = prefix + "title_effect";
        frameTable = prefix + "title_frame";
        coinTable = prefix + "coin";
        quotaTable = prefix + "quota";
        rewardTable = prefix + "reward";
        rewardLogTable = prefix + "reward_log";
        sqlite = config.getString("storage.type", "MYSQL").equalsIgnoreCase("SQLITE");
        HikariConfig hikari = new HikariConfig();
        if (sqlite) {
            File file = new File(dataFolder, config.getString("storage.sqlite.file", "data.db"));
            hikari.setJdbcUrl("jdbc:sqlite:" + file.getAbsolutePath() + "?busy_timeout=5000");
            hikari.setMaximumPoolSize(1);
        } else {
            String host = config.getString("storage.mysql.host", "127.0.0.1");
            int port = config.getInt("storage.mysql.port", 3306);
            String database = config.getString("storage.mysql.database", "minecraft");
            String params = config.getString("storage.mysql.params",
                    "?useSSL=false&characterEncoding=utf8&serverTimezone=UTC&allowPublicKeyRetrieval=true");
            hikari.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database + params);
            hikari.setUsername(config.getString("storage.mysql.user", "root"));
            hikari.setPassword(config.getString("storage.mysql.password", ""));
            hikari.setMaximumPoolSize(Math.max(1, config.getInt("storage.mysql.pool-size", 4)));
        }
        String driverClass = sqlite ? "org.sqlite.JDBC" : "com.mysql.cj.jdbc.Driver";
        String[] candidates = sqlite
                ? new String[]{driverClass, "com.dracave.title.libs.sqlite.JDBC"}
                : new String[]{driverClass, "com.dracave.title.libs.mysql.cj.jdbc.Driver"};
        for (String candidate : candidates) {
            try {
                Class.forName(candidate);
                hikari.setDriverClassName(candidate);
                break;
            } catch (ClassNotFoundException ignored) {
            }
        }
        hikari.setMinimumIdle(1);
        hikari.setConnectionTimeout(10000L);
        hikari.setPoolName(sqlite ? "DraCaveTitle-SQLite" : "DraCaveTitle-MySQL");
        dataSource = new HikariDataSource(hikari);
        try {
            createTables();
        } catch (Exception ex) {
            dataSource.close();
            throw ex;
        }
    }
    public HikariDataSource dataSource() {
        return dataSource;
    }
    public boolean sqlite() {
        return sqlite;
    }
    public String playerTable() {
        return playerTable;
    }
    public String unlockTable() {
        return unlockTable;
    }
    public String purchaseTable() {
        return purchaseTable;
    }
    public String customTitleTable() {
        return customTitleTable;
    }
    public String schemaVersionTable() {
        return schemaVersionTable;
    }
    public String definitionTable() {
        return definitionTable;
    }
    public String descriptionTable() {
        return descriptionTable;
    }
    public String colorTable() {
        return colorTable;
    }
    public String effectTable() {
        return effectTable;
    }
    public String frameTable() {
        return frameTable;
    }
    public String coinTable() {
        return coinTable;
    }
    public String quotaTable() {
        return quotaTable;
    }
    public String rewardTable() {
        return rewardTable;
    }
    public String rewardLogTable() {
        return rewardLogTable;
    }
    public String suffix() {
        return sqlite ? "" : " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
    }
    private void createTables() throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS `" + schemaVersionTable
                    + "` (version INT NOT NULL PRIMARY KEY, applied_at BIGINT NOT NULL)" + suffix());
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS `" + definitionTable
                    + "` (title_id VARCHAR(64) NOT NULL PRIMARY KEY, title_text VARCHAR(128) NOT NULL,"
                    + " icon VARCHAR(64) NOT NULL, sort_order INT NOT NULL, default_unlocked BOOLEAN NOT NULL,"
                    + " permission_node VARCHAR(255) NULL, shop_hidden BOOLEAN NOT NULL DEFAULT TRUE,"
                    + " purchase_currency VARCHAR(32) NULL, purchase_price DECIMAL(19,4) NULL,"
                    + " gradient_period_ticks INT NULL, animation_type VARCHAR(24) NULL,"
                    + " particle_type VARCHAR(64) NULL, particle_id VARCHAR(64) NULL,"
                    + " particle_colors VARCHAR(255) NULL, revision INT NOT NULL, created_at BIGINT NOT NULL,"
                    + " updated_at BIGINT NOT NULL)" + suffix());
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS `" + descriptionTable
                    + "` (title_id VARCHAR(64) NOT NULL, position INT NOT NULL, description_text TEXT NOT NULL,"
                    + " PRIMARY KEY(title_id,position))" + suffix());
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS `" + colorTable
                    + "` (title_id VARCHAR(64) NOT NULL, position INT NOT NULL, color VARCHAR(7) NOT NULL,"
                    + " PRIMARY KEY(title_id,position))" + suffix());
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS `" + effectTable
                    + "` (title_id VARCHAR(64) NOT NULL, position INT NOT NULL, effect_type VARCHAR(64) NOT NULL,"
                    + " effect_level INT NOT NULL, PRIMARY KEY(title_id,position), UNIQUE(title_id,effect_type))" + suffix());
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS `" + frameTable
                    + "` (title_id VARCHAR(64) NOT NULL, position INT NOT NULL, frame_text VARCHAR(128) NOT NULL,"
                    + " PRIMARY KEY(title_id,position))" + suffix());
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS `" + playerTable
                    + "` (player_uuid VARCHAR(36) NOT NULL PRIMARY KEY, equipped_id VARCHAR(64) NULL,"
                    + " updated_at BIGINT NOT NULL)" + suffix());
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS `" + unlockTable
                    + "` (player_uuid VARCHAR(36) NOT NULL, title_id VARCHAR(64) NOT NULL,"
                    + " unlocked_at BIGINT NOT NULL, expires_at BIGINT NULL,"
                    + " PRIMARY KEY(player_uuid,title_id))" + suffix());
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS `" + purchaseTable
                    + "` (operation_id VARCHAR(36) NOT NULL PRIMARY KEY, player_uuid VARCHAR(36) NOT NULL,"
                    + " title_id VARCHAR(64) NOT NULL, currency VARCHAR(32) NOT NULL, amount DECIMAL(19,4) NOT NULL,"
                    + " state VARCHAR(24) NOT NULL, failure_reason VARCHAR(255) NULL, refunded BOOLEAN NOT NULL DEFAULT FALSE,"
                    + " created_at BIGINT NOT NULL, updated_at BIGINT NOT NULL, completed_at BIGINT NULL)" + suffix());
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS `" + customTitleTable
                    + "` (title_id VARCHAR(64) NOT NULL PRIMARY KEY, owner_uuid VARCHAR(36) NOT NULL,"
                    + " title_text VARCHAR(128) NOT NULL, type VARCHAR(24) NOT NULL, colors VARCHAR(2048) NOT NULL,"
                    + " frames VARCHAR(4096) NOT NULL, period_ticks INT NOT NULL, icon VARCHAR(64) NOT NULL,"
                    + " status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE', revision INT NOT NULL,"
                    + " created_at BIGINT NOT NULL, updated_at BIGINT NOT NULL, deleted_at BIGINT NULL)" + suffix());
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS `" + coinTable
                    + "` (player_uuid VARCHAR(36) NOT NULL PRIMARY KEY, amount BIGINT NOT NULL DEFAULT 0,"
                    + " updated_at BIGINT NOT NULL)" + suffix());
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS `" + quotaTable
                    + "` (player_uuid VARCHAR(36) NOT NULL PRIMARY KEY, quota INT NOT NULL DEFAULT 0,"
                    + " updated_at BIGINT NOT NULL)" + suffix());
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS `" + rewardTable
                    + "` (id BIGINT NOT NULL PRIMARY KEY, number INT NOT NULL, reward_type VARCHAR(32) NOT NULL,"
                    + " amount BIGINT NOT NULL)" + suffix());
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS `" + rewardLogTable
                    + "` (id BIGINT NOT NULL PRIMARY KEY, player_uuid VARCHAR(36) NOT NULL,"
                    + " reward_id BIGINT NOT NULL, claimed_at BIGINT NOT NULL)" + suffix());
        }
    }
    private static String sanitize(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return "dracavetitle_";
        }
        return prefix.replaceAll("[^A-Za-z0-9_]", "");
    }
    @Override
    public void close() {
        dataSource.close();
    }
}
