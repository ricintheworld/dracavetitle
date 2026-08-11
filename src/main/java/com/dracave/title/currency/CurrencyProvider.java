package com.dracave.title.currency;
import com.dracave.title.model.CurrencyType;
import java.math.BigDecimal;
import java.util.UUID;
public interface CurrencyProvider {
    CurrencyType type();
    boolean available();
    BigDecimal balance(UUID playerId);
    boolean withdraw(UUID playerId, BigDecimal amount);
    boolean refund(UUID playerId, BigDecimal amount);
}
