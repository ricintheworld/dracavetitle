package com.dracave.title.storage;
import com.dracave.title.model.CurrencyType;
import com.dracave.title.model.TitleAnimation;
import com.dracave.title.model.TitleDefinition;
import com.dracave.title.model.TitleParticle;
import com.dracave.title.model.TitlePotionEffect;
import com.dracave.title.model.TitlePurchaseOffer;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
public final class TitleDefinitionRepository {
    private final TitleDatabase database;
    public TitleDefinitionRepository(TitleDatabase database) {
        this.database = database;
    }
    public List<TitleDefinition> loadAll() throws SQLException {
        Map<String, TitleDefinition> byId = new HashMap<>();
        Map<String, List<String>> descriptions = new HashMap<>();
        Map<String, List<String>> colors = new HashMap<>();
        Map<String, List<String>> frames = new HashMap<>();
        Map<String, List<TitlePotionEffect>> effects = new HashMap<>();
        Map<String, String> animationTypes = new HashMap<>();
        Map<String, Integer> periodTicksMap = new HashMap<>();
        try (Connection connection = database.dataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT title_id,title_text,icon,sort_order,default_unlocked,permission_node,shop_hidden,"
                             + "purchase_currency,purchase_price,gradient_period_ticks,animation_type,"
                             + "particle_type,particle_id,particle_colors,revision FROM `" + database.definitionTable() + "`")) {
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    String id = rows.getString(1);
                    String currencyName = rows.getString(8);
                    TitlePurchaseOffer offer = null;
                    if (currencyName != null) {
                        try {
                            offer = TitlePurchaseOffer.parseDb(currencyName, rows.getBigDecimal(9));
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                    int periodTicks = rows.getInt(10);
                    if (!rows.wasNull() && periodTicks > 0) {
                        periodTicksMap.put(id, periodTicks);
                    }
                    String animationType = rows.getString(11);
                    if (animationType != null && !animationType.isBlank()) {
                        animationTypes.put(id, animationType);
                    }
                    String particleType = rows.getString(12);
                    TitleParticle particle = null;
                    if (particleType != null) {
                        String particleId = rows.getString(13);
                        String particleColors = rows.getString(14);
                        List<String> particleColorList = particleColors == null || particleColors.isBlank()
                                ? List.of() : List.of(particleColors.split(","));
                        particle = new TitleParticle(particleType, particleId, particleColorList);
                    }
                    byId.put(id, new TitleDefinition(
                            id, rows.getString(2), List.of(), rows.getString(3), rows.getInt(4),
                            rows.getBoolean(5), rows.getString(6), null, offer,
                            List.of(), rows.getBoolean(7), List.of(), particle, rows.getInt(15)));
                }
            }
        }
        loadStrings(descriptions, database.descriptionTable(), "description_text");
        loadStrings(colors, database.colorTable(), "color");
        loadStrings(frames, database.frameTable(), "frame_text");
        try (Connection connection = database.dataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT title_id,effect_type,effect_level FROM `" + database.effectTable() + "` ORDER BY position")) {
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    effects.computeIfAbsent(rows.getString(1), ignored -> new ArrayList<>())
                            .add(new TitlePotionEffect(rows.getString(2), rows.getInt(3)));
                }
            }
        }
        List<TitleDefinition> result = new ArrayList<>();
        for (TitleDefinition definition : byId.values()) {
            List<String> titleColors = colors.getOrDefault(definition.id(), List.of());
            TitleAnimation animation = rebuildAnimation(titleColors,
                    animationTypes.get(definition.id()),
                    periodTicksMap.getOrDefault(definition.id(), 0),
                    frames.getOrDefault(definition.id(), List.of()));
            result.add(new TitleDefinition(
                    definition.id(), definition.display(),
                    descriptions.getOrDefault(definition.id(), List.of()),
                    definition.icon(), definition.order(), definition.defaultUnlocked(), definition.permission(),
                    animation, definition.purchaseOffer(),
                    titleColors, definition.shopHidden(),
                    effects.getOrDefault(definition.id(), List.of()), definition.particle(), definition.revision()));
        }
        result.sort(Comparator.comparingInt(TitleDefinition::order).thenComparing(TitleDefinition::id));
        return result;
    }
    private TitleAnimation rebuildAnimation(List<String> colors, String animationType, int periodTicks, List<String> frames) {
        if (periodTicks <= 0) {
            return null;
        }
        TitleAnimation.GradientMode mode = TitleAnimation.GradientMode.CYCLE;
        String baseType = animationType == null ? "" : animationType;
        if (baseType.endsWith(":PINGPONG")) {
            mode = TitleAnimation.GradientMode.PINGPONG;
            baseType = baseType.substring(0, baseType.length() - ":PINGPONG".length());
        }
        return switch (baseType) {
            case "RAINBOW" -> TitleAnimation.rainbow(periodTicks);
            case "SOLID_GRADIENT" -> colors.size() >= 2
                    ? new TitleAnimation(TitleAnimation.Type.SOLID_GRADIENT, colors, List.of(), periodTicks, mode) : null;
            case "FLASHING_COLORS" -> colors.size() >= 2
                    ? new TitleAnimation(TitleAnimation.Type.FLASHING_COLORS, colors, List.of(), periodTicks) : null;
            case "TEXT_FRAMES" -> frames.size() >= 2
                    ? new TitleAnimation(TitleAnimation.Type.TEXT_FRAMES, List.of(), frames, periodTicks) : null;
            default -> colors.size() >= 2 ? new TitleAnimation(colors, periodTicks, mode) : null;
        };
    }
    private void loadStrings(Map<String, List<String>> target, String table, String column) throws SQLException {
        try (Connection connection = database.dataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT title_id," + column + " FROM `" + table + "` ORDER BY position")) {
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    target.computeIfAbsent(rows.getString(1), ignored -> new ArrayList<>()).add(rows.getString(2));
                }
            }
        }
    }
    public UpsertResult upsertAll(List<TitleDefinition> definitions) throws SQLException {
        int inserted = 0;
        int updated = 0;
        try (Connection connection = database.dataSource().getConnection()) {
            connection.setAutoCommit(false);
            try {
                for (TitleDefinition definition : definitions) {
                    boolean exists;
                    try (PreparedStatement check = connection.prepareStatement(
                            "SELECT 1 FROM `" + database.definitionTable() + "` WHERE title_id=?")) {
                        check.setString(1, definition.id());
                        try (ResultSet rows = check.executeQuery()) {
                            exists = rows.next();
                        }
                    }
                    if (exists) {
                        update(connection, definition);
                        updated++;
                    } else {
                        insert(connection, definition);
                        inserted++;
                    }
                }
                connection.commit();
            } catch (SQLException ex) {
                connection.rollback();
                throw ex;
            }
        }
        return new UpsertResult(inserted, updated, definitions);
    }
    private void insert(Connection connection, TitleDefinition definition) throws SQLException {
        long now = System.currentTimeMillis();
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO `" + database.definitionTable()
                        + "` (title_id,title_text,icon,sort_order,default_unlocked,permission_node,shop_hidden,"
                        + "purchase_currency,purchase_price,gradient_period_ticks,animation_type,"
                        + "particle_type,particle_id,particle_colors,revision,created_at,updated_at)"
                        + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            statement.setString(1, definition.id());
            bindMutable(statement, definition, 2);
            statement.setInt(15, 0);
            statement.setLong(16, now);
            statement.setLong(17, now);
            statement.executeUpdate();
        }
        replaceChildren(connection, definition);
    }
    private void update(Connection connection, TitleDefinition definition) throws SQLException {
        long now = System.currentTimeMillis();
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE `" + database.definitionTable()
                        + "` SET title_text=?,icon=?,sort_order=?,default_unlocked=?,permission_node=?,shop_hidden=?,"
                        + "purchase_currency=?,purchase_price=?,gradient_period_ticks=?,animation_type=?,"
                        + "particle_type=?,particle_id=?,particle_colors=?,revision=revision+1,updated_at=?"
                        + " WHERE title_id=?")) {
            bindMutable(statement, definition, 1);
            statement.setLong(14, now);
            statement.setString(15, definition.id());
            statement.executeUpdate();
        }
        replaceChildren(connection, definition);
    }
    private void bindMutable(PreparedStatement statement, TitleDefinition definition, int offset) throws SQLException {
        statement.setString(offset, definition.display());
        statement.setString(offset + 1, definition.icon());
        statement.setInt(offset + 2, definition.order());
        statement.setBoolean(offset + 3, definition.defaultUnlocked());
        statement.setString(offset + 4, definition.permission());
        statement.setBoolean(offset + 5, definition.shopHidden());
        if (definition.purchaseOffer() == null) {
            statement.setNull(offset + 6, java.sql.Types.VARCHAR);
            statement.setNull(offset + 7, java.sql.Types.DECIMAL);
        } else {
            statement.setString(offset + 6, definition.purchaseOffer().dbCurrency());
            statement.setBigDecimal(offset + 7, definition.purchaseOffer().price());
        }
        TitleAnimation animation = definition.animation();
        if (animation == null) {
            statement.setNull(offset + 8, java.sql.Types.INTEGER);
            statement.setNull(offset + 9, java.sql.Types.VARCHAR);
        } else {
            statement.setInt(offset + 8, animation.periodTicks());
            String animType = animation.type().name();
            if (animation.mode() == TitleAnimation.GradientMode.PINGPONG) {
                animType += ":PINGPONG";
            }
            statement.setString(offset + 9, animType);
        }
        if (definition.particle() == null) {
            statement.setNull(offset + 10, java.sql.Types.VARCHAR);
            statement.setNull(offset + 11, java.sql.Types.VARCHAR);
            statement.setNull(offset + 12, java.sql.Types.VARCHAR);
        } else {
            statement.setString(offset + 10, definition.particle().particleType());
            statement.setString(offset + 11, definition.particle().particleId());
            statement.setString(offset + 12, String.join(",", definition.particle().colors()));
        }
    }
    private void replaceChildren(Connection connection, TitleDefinition definition) throws SQLException {
        deleteChildren(connection, database.descriptionTable(), definition.id());
        deleteChildren(connection, database.colorTable(), definition.id());
        deleteChildren(connection, database.effectTable(), definition.id());
        deleteChildren(connection, database.frameTable(), definition.id());
        insertStrings(connection, database.descriptionTable(), "description_text", definition.id(), definition.description());
        insertStrings(connection, database.colorTable(), "color", definition.id(), definition.colors());
        TitleAnimation animation = definition.animation();
        if (animation != null && animation.type() == TitleAnimation.Type.TEXT_FRAMES) {
            insertStrings(connection, database.frameTable(), "frame_text", definition.id(), animation.frames());
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO `" + database.effectTable()
                        + "` (title_id,position,effect_type,effect_level) VALUES (?,?,?,?)")) {
            for (int i = 0; i < definition.potionEffects().size(); i++) {
                TitlePotionEffect effect = definition.potionEffects().get(i);
                statement.setString(1, definition.id());
                statement.setInt(2, i);
                statement.setString(3, effect.effectType());
                statement.setInt(4, effect.level());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }
    private void deleteChildren(Connection connection, String table, String id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM `" + table + "` WHERE title_id=?")) {
            statement.setString(1, id);
            statement.executeUpdate();
        }
    }
    private void insertStrings(Connection connection, String table, String column, String id, List<String> values) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO `" + table + "` (title_id,position," + column + ") VALUES (?,?,?)")) {
            for (int i = 0; i < values.size(); i++) {
                statement.setString(1, id);
                statement.setInt(2, i);
                statement.setString(3, values.get(i));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }
    public boolean update(TitleDefinition definition, int expectedRevision) throws SQLException {
        try (Connection connection = database.dataSource().getConnection()) {
            connection.setAutoCommit(false);
            try {
                long now = System.currentTimeMillis();
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE `" + database.definitionTable()
                                + "` SET title_text=?,icon=?,sort_order=?,default_unlocked=?,permission_node=?,"
                                + "shop_hidden=?,purchase_currency=?,purchase_price=?,gradient_period_ticks=?,"
                                + "animation_type=?,particle_type=?,particle_id=?,particle_colors=?,"
                                + "revision=revision+1,updated_at=? WHERE title_id=? AND revision=?")) {
                    bindMutable(statement, definition, 1);
                    statement.setLong(14, now);
                    statement.setString(15, definition.id());
                    statement.setInt(16, expectedRevision);
                    boolean changed = statement.executeUpdate() > 0;
                    if (changed) {
                        replaceChildren(connection, definition);
                    }
                    connection.commit();
                    return changed;
                }
            } catch (SQLException ex) {
                connection.rollback();
                throw ex;
            }
        }
    }
    public boolean delete(String titleId) throws SQLException {
        try (Connection connection = database.dataSource().getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM `" + database.definitionTable() + "` WHERE title_id=?")) {
                    statement.setString(1, titleId);
                    statement.executeUpdate();
                }
                deleteChildren(connection, database.descriptionTable(), titleId);
                deleteChildren(connection, database.colorTable(), titleId);
                deleteChildren(connection, database.effectTable(), titleId);
                deleteChildren(connection, database.frameTable(), titleId);
                connection.commit();
                return true;
            } catch (SQLException ex) {
                connection.rollback();
                throw ex;
            }
        }
    }
    public record UpsertResult(int inserted, int updated, List<TitleDefinition> definitions) {
        public UpsertResult {
            definitions = List.copyOf(definitions);
        }
    }
}
