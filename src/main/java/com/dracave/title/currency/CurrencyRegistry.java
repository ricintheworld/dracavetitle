package com.dracave.title.currency;
import com.dracave.title.model.CurrencyType;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
public final class CurrencyRegistry {
    private final Map<CurrencyType, CurrencyProvider> providers = new ConcurrentHashMap<>();
    public void register(CurrencyProvider provider) {
        providers.put(provider.type(), provider);
    }
    public CurrencyProvider get(CurrencyType type) {
        return providers.get(type);
    }
}
