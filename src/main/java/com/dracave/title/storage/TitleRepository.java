package com.dracave.title.storage;
import com.dracave.title.model.PlayerData;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
public final class TitleRepository {
    private final TitleDatabase database;
    public TitleRepository(TitleDatabase database) {
        this.database = database;
    }
    public Map<UUID, EquippedSnapshot> batchLoadEquipped(Collection<UUID> playerIds) throws SQLException {
        if (playerIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, EquippedSnapshot> result = new HashMap<>();
        List<UUID> ids = List.copyOf(playerIds);
        int batchSize = 500;
        for (int start = 0; start < ids.size(); start += batchSize) {
            int end = Math.min(start + batchSize, ids.size());
            List<UUID> batch = ids.subList(start, end);
            String placeholders = String.join(",", batch.stream().map(id -> "?").toList());
            String sql = "SELECT player_uuid,equipped_id,updated_at FROM `" + database.playerTable()
                    + "` WHERE player_uuid IN (" + placeholders + ")";
            try (Connection connection = database.dataSource().getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                for (int i = 0; i < batch.size(); i++) {
                    statement.setString(i + 1, batch.get(i).toString());
                }
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        result.put(UUID.fromString(rows.getString(1)),
                                new EquippedSnapshot(rows.getString(2), rows.getLong(3)));
                    }
                }
            }
        }
        return result;
    }
    public record EquippedSnapshot(String equippedId, long updatedAt) {
    }
    public PlayerData load(UUID playerId) throws SQLException {
        String equippedId = null;
        try (Connection connection = database.dataSource().getConnection();
             PreparedStatement player = connection.prepareStatement(
                     "SELECT equipped_id FROM `" + database.playerTable() + "` WHERE player_uuid=?")) {
            player.setString(1, playerId.toString());
            try (ResultSet rows = player.executeQuery()) {
                if (rows.next()) {
                    equippedId = rows.getString(1);
                }
            }
        }
        Map<String, Long> unlocked = new HashMap<>();
        try (Connection connection = database.dataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT title_id, expires_at FROM `" + database.unlockTable() + "` WHERE player_uuid=?")) {
            statement.setString(1, playerId.toString());
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    long expiresAt = rows.getLong(2);
                    unlocked.put(rows.getString(1), rows.wasNull() ? null : expiresAt);
                }
            }
        }
        return new PlayerData(playerId, unlocked.keySet(), equippedId, unlocked);
    }
    public boolean unlock(UUID playerId, String titleId, int days) throws SQLException {
        Long expiresAt = days > 0 ? System.currentTimeMillis() + days * 86400000L : null;
        String sql = (database.sqlite() ? "INSERT OR IGNORE" : "INSERT IGNORE")
                + " INTO `" + database.unlockTable()
                + "` (player_uuid,title_id,unlocked_at,expires_at) VALUES (?,?,?,?)";
        try (Connection connection = database.dataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            statement.setString(2, titleId);
            statement.setLong(3, System.currentTimeMillis());
            if (expiresAt == null) {
                statement.setNull(4, java.sql.Types.BIGINT);
            } else {
                statement.setLong(4, expiresAt);
            }
            return statement.executeUpdate() > 0;
        }
    }
    public boolean setExpiry(UUID playerId, String titleId, int days) throws SQLException {
        long now = System.currentTimeMillis();
        Long expiresAt = days > 0 ? now + days * 86400000L : null;
        String sql = database.sqlite()
                ? "INSERT INTO `" + database.unlockTable()
                + "` (player_uuid,title_id,unlocked_at,expires_at) VALUES (?,?,?,?)"
                + " ON CONFLICT(player_uuid,title_id) DO UPDATE SET expires_at=excluded.expires_at"
                : "INSERT INTO `" + database.unlockTable()
                + "` (player_uuid,title_id,unlocked_at,expires_at) VALUES (?,?,?,?)"
                + " ON DUPLICATE KEY UPDATE expires_at=VALUES(expires_at)";
        try (Connection connection = database.dataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            statement.setString(2, titleId);
            statement.setLong(3, now);
            if (expiresAt == null) {
                statement.setNull(4, java.sql.Types.BIGINT);
            } else {
                statement.setLong(4, expiresAt);
            }
            return statement.executeUpdate() > 0;
        }
    }
    public boolean extend(UUID playerId, String titleId, int days) throws SQLException {
        long now = System.currentTimeMillis();
        Long expiresAt = days > 0 ? now + days * 86400000L : null;
        String sql = database.sqlite()
                ? "INSERT INTO `" + database.unlockTable()
                + "` (player_uuid,title_id,unlocked_at,expires_at) VALUES (?,?,?,?)"
                + " ON CONFLICT(player_uuid,title_id) DO UPDATE SET expires_at=CASE WHEN expires_at IS NULL THEN expires_at ELSE excluded.expires_at END"
                : "INSERT INTO `" + database.unlockTable()
                + "` (player_uuid,title_id,unlocked_at,expires_at) VALUES (?,?,?,?)"
                + " ON DUPLICATE KEY UPDATE expires_at=IF(expires_at IS NULL, expires_at, VALUES(expires_at))";
        try (Connection connection = database.dataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            statement.setString(2, titleId);
            statement.setLong(3, now);
            if (expiresAt == null) {
                statement.setNull(4, java.sql.Types.BIGINT);
            } else {
                statement.setLong(4, expiresAt);
            }
            return statement.executeUpdate() > 0;
        }
    }
    public Map<String, Long> purgeExpired(UUID playerId) throws SQLException {
        Map<String, Long> removed = new HashMap<>();
        long now = System.currentTimeMillis();
        try (Connection connection = database.dataSource().getConnection();
             PreparedStatement select = connection.prepareStatement(
                     "SELECT title_id FROM `" + database.unlockTable()
                             + "` WHERE player_uuid=? AND expires_at IS NOT NULL AND expires_at<?")) {
            select.setString(1, playerId.toString());
            select.setLong(2, now);
            try (ResultSet rows = select.executeQuery()) {
                while (rows.next()) {
                    removed.put(rows.getString(1), now);
                }
            }
        }
        if (removed.isEmpty()) {
            return removed;
        }
        try (Connection connection = database.dataSource().getConnection();
             PreparedStatement delete = connection.prepareStatement(
                     "DELETE FROM `" + database.unlockTable()
                             + "` WHERE player_uuid=? AND expires_at IS NOT NULL AND expires_at<?")) {
            delete.setString(1, playerId.toString());
            delete.setLong(2, now);
            delete.executeUpdate();
        }
        try (Connection connection = database.dataSource().getConnection();
             PreparedStatement clear = connection.prepareStatement(
                     "UPDATE `" + database.playerTable()
                             + "` SET equipped_id=NULL,updated_at=? WHERE player_uuid=? AND equipped_id IS NOT NULL"
                             + " AND equipped_id NOT IN (SELECT title_id FROM `" + database.unlockTable()
                             + "` WHERE player_uuid=?)")) {
            clear.setLong(1, now);
            clear.setString(2, playerId.toString());
            clear.setString(3, playerId.toString());
            clear.executeUpdate();
        }
        return removed;
    }
    public boolean revoke(UUID playerId, String titleId) throws SQLException {
        try (Connection connection = database.dataSource().getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement remove = connection.prepareStatement(
                        "DELETE FROM `" + database.unlockTable() + "` WHERE player_uuid=? AND title_id=?")) {
                    remove.setString(1, playerId.toString());
                    remove.setString(2, titleId);
                    int changed = remove.executeUpdate();
                    if (changed == 0) {
                        connection.rollback();
                        return false;
                    }
                }
                try (PreparedStatement clear = connection.prepareStatement(
                        "UPDATE `" + database.playerTable()
                                + "` SET equipped_id=NULL,updated_at=? WHERE player_uuid=? AND equipped_id=?")) {
                    clear.setLong(1, System.currentTimeMillis());
                    clear.setString(2, playerId.toString());
                    clear.setString(3, titleId);
                    clear.executeUpdate();
                }
                try (PreparedStatement purchase = connection.prepareStatement(
                        "UPDATE `" + database.purchaseTable()
                                + "` SET state='REVOKED',updated_at=? WHERE player_uuid=? AND title_id=? AND state='COMPLETED'")) {
                    purchase.setLong(1, System.currentTimeMillis());
                    purchase.setString(2, playerId.toString());
                    purchase.setString(3, titleId);
                    purchase.executeUpdate();
                }
                connection.commit();
                return true;
            } catch (SQLException ex) {
                connection.rollback();
                throw ex;
            }
        }
    }
    public int removeTitleFromAll(String titleId) throws SQLException {
        try (Connection connection = database.dataSource().getConnection()) {
            connection.setAutoCommit(false);
            try {
                int removed;
                try (PreparedStatement remove = connection.prepareStatement(
                        "DELETE FROM `" + database.unlockTable() + "` WHERE title_id=?")) {
                    remove.setString(1, titleId);
                    removed = remove.executeUpdate();
                }
                try (PreparedStatement clear = connection.prepareStatement(
                        "UPDATE `" + database.playerTable()
                                + "` SET equipped_id=NULL,updated_at=? WHERE equipped_id=?")) {
                    clear.setLong(1, System.currentTimeMillis());
                    clear.setString(2, titleId);
                    clear.executeUpdate();
                }
                connection.commit();
                return removed;
            } catch (SQLException ex) {
                connection.rollback();
                throw ex;
            }
        }
    }
    public boolean equip(UUID playerId, String titleId) throws SQLException {
        String sql = database.sqlite()
                ? "INSERT INTO `" + database.playerTable()
                + "` (player_uuid,equipped_id,updated_at) VALUES (?,?,?)"
                + " ON CONFLICT(player_uuid) DO UPDATE SET equipped_id=excluded.equipped_id,updated_at=excluded.updated_at"
                : "INSERT INTO `" + database.playerTable()
                + "` (player_uuid,equipped_id,updated_at) VALUES (?,?,?)"
                + " ON DUPLICATE KEY UPDATE equipped_id=VALUES(equipped_id),updated_at=VALUES(updated_at)";
        try (Connection connection = database.dataSource().getConnection()) {
            if (titleId != null) {
                try (PreparedStatement owned = connection.prepareStatement(
                        "SELECT 1 FROM `" + database.unlockTable() + "` WHERE player_uuid=? AND title_id=?")) {
                    owned.setString(1, playerId.toString());
                    owned.setString(2, titleId);
                    try (ResultSet rows = owned.executeQuery()) {
                        if (!rows.next()) {
                            return false;
                        }
                    }
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, playerId.toString());
                statement.setString(2, titleId);
                statement.setLong(3, System.currentTimeMillis());
                statement.executeUpdate();
            }
            return true;
        }
    }
    public boolean reservePurchase(UUID playerId, String titleId, UUID operationId, String currency, java.math.BigDecimal amount)
            throws SQLException {
        String sql = (database.sqlite() ? "INSERT OR IGNORE" : "INSERT IGNORE")
                + " INTO `" + database.purchaseTable()
                + "` (operation_id,player_uuid,title_id,currency,amount,state,created_at,updated_at) VALUES (?,?,?,?,?,'PENDING',?,?)";
        try (Connection connection = database.dataSource().getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement owned = connection.prepareStatement(
                        "SELECT 1 FROM `" + database.unlockTable() + "` WHERE player_uuid=? AND title_id=?")) {
                    owned.setString(1, playerId.toString());
                    owned.setString(2, titleId);
                    try (ResultSet result = owned.executeQuery()) {
                        if (result.next()) {
                            connection.rollback();
                            return false;
                        }
                    }
                }
                try (PreparedStatement cleanup = connection.prepareStatement(
                        "DELETE FROM `" + database.purchaseTable()
                                + "` WHERE player_uuid=? AND title_id=? AND state IN ('FAILED','REFUNDED','REVOKED')")) {
                    cleanup.setString(1, playerId.toString());
                    cleanup.setString(2, titleId);
                    cleanup.executeUpdate();
                }
                long now = System.currentTimeMillis();
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, operationId.toString());
                    statement.setString(2, playerId.toString());
                    statement.setString(3, titleId);
                    statement.setString(4, currency);
                    statement.setBigDecimal(5, amount);
                    statement.setLong(6, now);
                    statement.setLong(7, now);
                    boolean inserted = statement.executeUpdate() > 0;
                    connection.commit();
                    return inserted;
                }
            } catch (SQLException ex) {
                connection.rollback();
                throw ex;
            }
        }
    }
    public boolean transitionPurchase(UUID operationId, String expected, String next, String reason) throws SQLException {
        try (Connection connection = database.dataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE `" + database.purchaseTable()
                             + "` SET state=?,failure_reason=?,updated_at=? WHERE operation_id=? AND state=?")) {
            statement.setString(1, next);
            statement.setString(2, reason);
            statement.setLong(3, System.currentTimeMillis());
            statement.setString(4, operationId.toString());
            statement.setString(5, expected);
            return statement.executeUpdate() > 0;
        }
    }
    public boolean forcePurchaseState(UUID operationId, String next, String reason) throws SQLException {
        try (Connection connection = database.dataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE `" + database.purchaseTable()
                             + "` SET state=?,failure_reason=?,updated_at=? WHERE operation_id=?")) {
            statement.setString(1, next);
            statement.setString(2, reason);
            statement.setLong(3, System.currentTimeMillis());
            statement.setString(4, operationId.toString());
            return statement.executeUpdate() > 0;
        }
    }
    public boolean markRefunded(UUID operationId, boolean refunded) throws SQLException {
        try (Connection connection = database.dataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE `" + database.purchaseTable() + "` SET refunded=?,updated_at=? WHERE operation_id=?")) {
            statement.setBoolean(1, refunded);
            statement.setLong(2, System.currentTimeMillis());
            statement.setString(3, operationId.toString());
            return statement.executeUpdate() > 0;
        }
    }
    public java.util.List<PurchaseRecord> findStalePurchases(long olderThan) throws SQLException {
        java.util.List<PurchaseRecord> records = new java.util.ArrayList<>();
        try (Connection connection = database.dataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT player_uuid,title_id,operation_id,currency,amount,state,updated_at FROM `"
                             + database.purchaseTable() + "` WHERE state IN ('PENDING','CHARGING','CHARGED') AND updated_at<?")) {
            statement.setLong(1, olderThan);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    records.add(new PurchaseRecord(
                            UUID.fromString(result.getString(1)),
                            result.getString(2),
                            UUID.fromString(result.getString(3)),
                            result.getString(4),
                            result.getBigDecimal(5),
                            result.getString(6),
                            result.getLong(7)
                    ));
                }
            }
        }
        return records;
    }
    public boolean completePurchase(UUID playerId, String titleId, UUID operationId) throws SQLException {
        try (Connection connection = database.dataSource().getConnection()) {
            connection.setAutoCommit(false);
            try {
                long now = System.currentTimeMillis();
                String sql = (database.sqlite() ? "INSERT OR IGNORE" : "INSERT IGNORE")
                        + " INTO `" + database.unlockTable()
                        + "` (player_uuid,title_id,unlocked_at,expires_at) VALUES (?,?,?,NULL)";
                try (PreparedStatement unlock = connection.prepareStatement(sql)) {
                    unlock.setString(1, playerId.toString());
                    unlock.setString(2, titleId);
                    unlock.setLong(3, now);
                    unlock.executeUpdate();
                }
                try (PreparedStatement complete = connection.prepareStatement(
                        "UPDATE `" + database.purchaseTable()
                                + "` SET state='COMPLETED',updated_at=?,completed_at=? WHERE operation_id=? AND state='CHARGED'")) {
                    complete.setLong(1, now);
                    complete.setLong(2, now);
                    complete.setString(3, operationId.toString());
                    if (complete.executeUpdate() == 0) {
                        connection.rollback();
                        return false;
                    }
                }
                connection.commit();
                return true;
            } catch (SQLException ex) {
                connection.rollback();
                throw ex;
            }
        }
    }
    public int countCompletedPurchases(UUID playerId, String titleId) throws SQLException {
        try (Connection connection = database.dataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM `" + database.purchaseTable()
                             + "` WHERE player_uuid=? AND title_id=? AND state='COMPLETED'")) {
            statement.setString(1, playerId.toString());
            statement.setString(2, titleId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getInt(1) : 0;
            }
        }
    }
    public java.util.List<TitleRankEntry> ranking(int limit) throws SQLException {
        java.util.List<TitleRankEntry> result = new java.util.ArrayList<>();
        try (Connection connection = database.dataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT player_uuid, COUNT(*) AS c FROM `" + database.unlockTable()
                             + "` GROUP BY player_uuid ORDER BY c DESC LIMIT ?")) {
            statement.setInt(1, limit);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.add(new TitleRankEntry(
                            UUID.fromString(rows.getString(1)), rows.getInt(2)));
                }
            }
        }
        return result;
    }
}
