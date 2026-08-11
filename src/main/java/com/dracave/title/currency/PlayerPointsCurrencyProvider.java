package com.dracave.title.currency;
import com.dracave.title.model.CurrencyType;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.UUID;
public final class PlayerPointsCurrencyProvider implements CurrencyProvider {
    private Object api;
    private Method look;
    private Method take;
    private Method give;
    @Override
    public CurrencyType type() {
        return CurrencyType.PLAYER_POINTS;
    }
    @Override
    public boolean available() {
        return resolve();
    }
    @Override
    public BigDecimal balance(UUID playerId) {
        if (!resolve()) {
            return BigDecimal.ZERO;
        }
        try {
            return BigDecimal.valueOf(((Number) look.invoke(api, playerId)).longValue());
        } catch (ReflectiveOperationException ex) {
            return BigDecimal.ZERO;
        }
    }
    @Override
    public boolean withdraw(UUID playerId, BigDecimal amount) {
        return invoke(take, playerId, amount);
    }
    @Override
    public boolean refund(UUID playerId, BigDecimal amount) {
        return invoke(give, playerId, amount);
    }
    private boolean invoke(Method method, UUID playerId, BigDecimal amount) {
        if (resolve() && amount.scale() <= 0 && amount.signum() > 0 && amount.compareTo(BigDecimal.valueOf(2147483647L)) <= 0) {
            try {
                return (Boolean) method.invoke(api, playerId, amount.intValueExact());
            } catch (ReflectiveOperationException | ArithmeticException ex) {
                return false;
            }
        }
        return false;
    }
    private boolean resolve() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("PlayerPoints");
        if (plugin == null || !plugin.isEnabled()) {
            api = null;
            return false;
        }
        if (api != null) {
            return true;
        }
        try {
            Object resolved = plugin.getClass().getMethod("getAPI").invoke(plugin);
            look = resolved.getClass().getMethod("look", UUID.class);
            take = resolved.getClass().getMethod("take", UUID.class, int.class);
            give = resolved.getClass().getMethod("give", UUID.class, int.class);
            api = resolved;
            return true;
        } catch (ReflectiveOperationException ex) {
            return false;
        }
    }
}
