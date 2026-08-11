package com.dracave.title.storage;
import com.dracave.title.model.Reward;
import com.dracave.title.model.RewardType;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
public final class RewardRepository {
    private final TitleDatabase database;
    public RewardRepository(TitleDatabase database) {
        this.database = database;
    }
    public List<Reward> findAll() throws SQLException {
        List<Reward> rewards = new ArrayList<>();
        try (Connection connection = database.dataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT id,number,reward_type,amount FROM `" + database.rewardTable() + "` ORDER BY number")) {
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    rewards.add(new Reward(rows.getLong(1), rows.getInt(2), RewardType.parse(rows.getString(3)), rows.getLong(4)));
                }
            }
        }
        return rewards;
    }
    public Reward findById(long id) throws SQLException {
        try (Connection connection = database.dataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT id,number,reward_type,amount FROM `" + database.rewardTable() + "` WHERE id=?")) {
            statement.setLong(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next()
                        ? new Reward(rows.getLong(1), rows.getInt(2), RewardType.parse(rows.getString(3)), rows.getLong(4))
                        : null;
            }
        }
    }
    public Reward add(Reward reward) throws SQLException {
        try (Connection connection = database.dataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO `" + database.rewardTable() + "` (id,number,reward_type,amount) VALUES (?,?,?,?)")) {
            statement.setLong(1, reward.id());
            statement.setInt(2, reward.number());
            statement.setString(3, reward.type().id());
            statement.setLong(4, reward.amount());
            statement.executeUpdate();
        }
        return reward;
    }
    public boolean isClaimed(UUID playerId, long rewardId) throws SQLException {
        try (Connection connection = database.dataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT 1 FROM `" + database.rewardLogTable() + "` WHERE player_uuid=? AND reward_id=?")) {
            statement.setString(1, playerId.toString());
            statement.setLong(2, rewardId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }
    public boolean claim(UUID playerId, long rewardId) throws SQLException {
        long id = UUID.randomUUID().getLeastSignificantBits() & Long.MAX_VALUE;
        try (Connection connection = database.dataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO `" + database.rewardLogTable() + "` (id,player_uuid,reward_id,claimed_at) VALUES (?,?,?,?)")) {
            statement.setLong(1, id);
            statement.setString(2, playerId.toString());
            statement.setLong(3, rewardId);
            statement.setLong(4, System.currentTimeMillis());
            return statement.executeUpdate() > 0;
        }
    }
}
