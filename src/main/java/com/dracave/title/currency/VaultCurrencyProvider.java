package com.dracave.title.currency;
import com.dracave.title.model.CurrencyType;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import java.math.BigDecimal;
import java.util.UUID;
public final class VaultCurrencyProvider implements CurrencyProvider {
    @Override
    public CurrencyType type() {
        return CurrencyType.VAULT;
    }
    @Override
    public boolean available() {
        return economy() != null;
    }
    @Override
    public BigDecimal balance(UUID playerId) {
        Economy provider = economy();
        return provider == null ? BigDecimal.ZERO : BigDecimal.valueOf(provider.getBalance(Bukkit.getOfflinePlayer(playerId)));
    }
    @Override
    public boolean withdraw(UUID playerId, BigDecimal amount) {
        Economy provider = economy();
        double value = amount.doubleValue();
        return provider != null && Double.isFinite(value) && value > 0
                && provider.withdrawPlayer(Bukkit.getOfflinePlayer(playerId), value).transactionSuccess();
    }
    @Override
    public boolean refund(UUID playerId, BigDecimal amount) {
        Economy provider = economy();
        double value = amount.doubleValue();
        return provider != null && Double.isFinite(value) && value > 0
                && provider.depositPlayer(Bukkit.getOfflinePlayer(playerId), value).transactionSuccess();
    }
    private Economy economy() {
        if (!Bukkit.getPluginManager().isPluginEnabled("Vault")) {
            return null;
        }
        RegisteredServiceProvider<Economy> registration = Bukkit.getServicesManager().getRegistration(Economy.class);
        return registration == null ? null : registration.getProvider();
    }
}
