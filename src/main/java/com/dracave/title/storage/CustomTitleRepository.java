package com.dracave.title.storage;
import com.dracave.title.model.CustomTitle;
import com.dracave.title.model.CustomTitleType;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
public final class CustomTitleRepository {
    private final TitleDatabase database;
    public CustomTitleRepository(TitleDatabase database) {
        this.database = database;
    }
    public List<CustomTitle> loadActive() throws SQLException {
        List<CustomTitle> result = new ArrayList<>();
        try (Connection connection = database.dataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT title_id,owner_uuid,title_text,type,colors,frames,period_ticks,icon,revision,"
                             + "created_at,updated_at FROM `" + database.customTitleTable() + "` WHERE status='ACTIVE'")) {
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.add(read(rows));
                }
            }
        }
        return result;
    }
    public boolean create(CustomTitle title) throws SQLException {
        try (Connection connection = database.dataSource().getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO `" + database.customTitleTable()
                                + "` (title_id,owner_uuid,title_text,type,colors,frames,period_ticks,icon,status,"
                                + "revision,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,'ACTIVE',?,?,?)")) {
                    write(insert, title);
                    insert.executeUpdate();
                }
                String sql = (database.sqlite() ? "INSERT OR IGNORE" : "INSERT IGNORE")
                        + " INTO `" + database.unlockTable()
                        + "` (player_uuid,title_id,unlocked_at,expires_at) VALUES (?,?,?,NULL)";
                try (PreparedStatement unlock = connection.prepareStatement(sql)) {
                    unlock.setString(1, title.ownerId().toString());
                    unlock.setString(2, title.id());
                    unlock.setLong(3, title.createdAt());
                    unlock.executeUpdate();
                }
                connection.commit();
                return true;
            } catch (SQLException ex) {
                connection.rollback();
                throw ex;
            }
        }
    }
    public boolean update(CustomTitle title, int expectedRevision) throws SQLException {
        try (Connection connection = database.dataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE `" + database.customTitleTable()
                             + "` SET title_text=?,type=?,colors=?,frames=?,period_ticks=?,icon=?,"
                             + "revision=revision+1,updated_at=? WHERE title_id=? AND owner_uuid=? AND revision=?"
                             + " AND status='ACTIVE'")) {
            statement.setString(1, title.text());
            statement.setString(2, title.type().name());
            statement.setString(3, encode(title.colors()));
            statement.setString(4, encode(title.frames()));
            statement.setInt(5, title.periodTicks());
            statement.setString(6, title.icon());
            statement.setLong(7, title.updatedAt());
            statement.setString(8, title.id());
            statement.setString(9, title.ownerId().toString());
            statement.setInt(10, expectedRevision);
            return statement.executeUpdate() > 0;
        }
    }
    public boolean delete(UUID ownerId, String titleId) throws SQLException {
        try (Connection connection = database.dataSource().getConnection()) {
            connection.setAutoCommit(false);
            try {
                long now = System.currentTimeMillis();
                try (PreparedStatement mark = connection.prepareStatement(
                        "UPDATE `" + database.customTitleTable()
                                + "` SET status='DELETED',deleted_at=?,updated_at=? WHERE title_id=? AND owner_uuid=? AND status='ACTIVE'")) {
                    mark.setLong(1, now);
                    mark.setLong(2, now);
                    mark.setString(3, titleId);
                    mark.setString(4, ownerId.toString());
                    if (mark.executeUpdate() == 0) {
                        connection.rollback();
                        return false;
                    }
                }
                try (PreparedStatement unlock = connection.prepareStatement(
                        "DELETE FROM `" + database.unlockTable() + "` WHERE title_id=?")) {
                    unlock.setString(1, titleId);
                    unlock.executeUpdate();
                }
                try (PreparedStatement clear = connection.prepareStatement(
                        "UPDATE `" + database.playerTable() + "` SET equipped_id=NULL,updated_at=? WHERE equipped_id=?")) {
                    clear.setLong(1, now);
                    clear.setString(2, titleId);
                    clear.executeUpdate();
                }
                connection.commit();
                return true;
            } catch (SQLException ex) {
                connection.rollback();
                throw ex;
            }
        }
    }
    private static CustomTitle read(ResultSet row) throws SQLException {
        return new CustomTitle(
                row.getString(1),
                UUID.fromString(row.getString(2)),
                row.getString(3),
                CustomTitleType.valueOf(row.getString(4)),
                decode(row.getString(5)),
                decode(row.getString(6)),
                row.getInt(7),
                row.getString(8),
                row.getInt(9),
                row.getLong(10),
                row.getLong(11)
        );
    }
    private static void write(PreparedStatement statement, CustomTitle title) throws SQLException {
        statement.setString(1, title.id());
        statement.setString(2, title.ownerId().toString());
        statement.setString(3, title.text());
        statement.setString(4, title.type().name());
        statement.setString(5, encode(title.colors()));
        statement.setString(6, encode(title.frames()));
        statement.setInt(7, title.periodTicks());
        statement.setString(8, title.icon());
        statement.setInt(9, title.revision());
        statement.setLong(10, title.createdAt());
        statement.setLong(11, title.updatedAt());
    }
    private static String encode(List<String> values) {
        return values.stream()
                .map(value -> Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8)))
                .reduce((a, b) -> a + "," + b)
                .orElse("");
    }
    private static List<String> decode(String value) {
        if (value == null || value.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(part -> new String(Base64.getUrlDecoder().decode(part), StandardCharsets.UTF_8))
                .toList();
    }
}
