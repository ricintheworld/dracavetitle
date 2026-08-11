package com.dracave.title.storage;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
public final class CoinRepository {
    private final TitleDatabase database;
    public CoinRepository(TitleDatabase database) {
        this.database = database;
    }
    public long balance(java.util.UUID playerId) throws SQLException {
        try (Connection connection = database.dataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT amount FROM `" + database.coinTable() + "` WHERE player_uuid=?")) {
            statement.setString(1, playerId.toString());
            try (var rows = statement.executeQuery()) {
                return rows.next() ? rows.getLong(1) : 0L;
            }
        }
    }
    public boolean add(java.util.UUID playerId, long amount) throws SQLException {
        if (amount <= 0) {
            return false;
        }
        String sql = database.sqlite()
                ? "INSERT INTO `" + database.coinTable() + "` (player_uuid,amount,updated_at) VALUES (?,?,?)"
                + " ON CONFLICT(player_uuid) DO UPDATE SET amount=amount+excluded.amount,updated_at=excluded.updated_at"
                : "INSERT INTO `" + database.coinTable() + "` (player_uuid,amount,updated_at) VALUES (?,?,?)"
                + " ON DUPLICATE KEY UPDATE amount=amount+VALUES(amount),updated_at=VALUES(updated_at)";
        try (Connection connection = database.dataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            statement.setLong(2, amount);
            statement.setLong(3, System.currentTimeMillis());
            return statement.executeUpdate() > 0;
        }
    }
    public boolean subtract(java.util.UUID playerId, long amount) throws SQLException {
        if (amount <= 0) {
            return false;
        }
        // 单条条件更新：WHERE amount>=? 保证原子扣除，余额不足时影响 0 行、返回 false，
        // 不会因并发 SELECT/UPDATE 之间的窗口而把余额扣成负数（原数被超额刷走）。
        String sql = "UPDATE `" + database.coinTable()
                + "` SET amount=amount-?,updated_at=? WHERE player_uuid=? AND amount>=?";
        try (Connection connection = database.dataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, amount);
            statement.setLong(2, System.currentTimeMillis());
            statement.setString(3, playerId.toString());
            statement.setLong(4, amount);
            return statement.executeUpdate() > 0;
        }
    }
}
