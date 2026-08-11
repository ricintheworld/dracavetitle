package com.dracave.title.currency;
import com.dracave.title.model.CurrencyType;
import com.dracave.title.storage.CoinRepository;
import java.math.BigDecimal;
import java.util.UUID;
public final class TitleCoinCurrencyProvider implements CurrencyProvider {
    private final CoinRepository repository;
    public TitleCoinCurrencyProvider(CoinRepository repository) {
        this.repository = repository;
    }
    @Override
    public CurrencyType type() {
        return CurrencyType.COIN;
    }
    @Override
    public boolean available() {
        return repository != null;
    }
    @Override
    public BigDecimal balance(UUID playerId) {
        if (repository == null) {
            return BigDecimal.ZERO;
        }
        try {
            return BigDecimal.valueOf(repository.balance(playerId));
        } catch (Exception ex) {
            return BigDecimal.ZERO;
        }
    }
    @Override
    public boolean withdraw(UUID playerId, BigDecimal amount) {
        if (repository == null || amount.scale() > 0 || amount.signum() <= 0) {
            return false;
        }
        try {
            return repository.subtract(playerId, amount.longValueExact());
        } catch (Exception ex) {
            return false;
        }
    }
    @Override
    public boolean refund(UUID playerId, BigDecimal amount) {
        if (repository == null || amount.scale() > 0 || amount.signum() <= 0) {
            return false;
        }
        try {
            return repository.add(playerId, amount.longValueExact());
        } catch (Exception ex) {
            return false;
        }
    }
}
