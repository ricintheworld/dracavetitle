package com.dracave.title.model;
import java.math.BigDecimal;
import java.util.Objects;
public record TitlePurchaseOffer(CurrencyType currency, BigDecimal price, String itemMaterial) {
    public TitlePurchaseOffer {
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(price, "price");
        price = price.stripTrailingZeros();
        if (price.signum() <= 0) {
            throw new IllegalArgumentException("purchase.price must be greater than zero");
        }
        if (price.scale() > 4 || price.precision() - price.scale() > 15) {
            throw new IllegalArgumentException("purchase.price must fit DECIMAL(19,4)");
        }
        if (currency == CurrencyType.PLAYER_POINTS || currency == CurrencyType.COIN || currency == CurrencyType.ITEM) {
            if (price.scale() > 0) {
                throw new IllegalArgumentException(currency.id() + " price must be a whole number");
            }
            if (price.compareTo(BigDecimal.valueOf(2147483647L)) > 0) {
                throw new IllegalArgumentException(currency.id() + " price exceeds 2147483647");
            }
        }
        if (currency == CurrencyType.ITEM && (itemMaterial == null || itemMaterial.isBlank())) {
            throw new IllegalArgumentException("item purchase requires itemMaterial");
        }
    }
    public TitlePurchaseOffer(CurrencyType currency, BigDecimal price) {
        this(currency, price, null);
    }
    public static TitlePurchaseOffer item(String material, int amount) {
        return new TitlePurchaseOffer(CurrencyType.ITEM, BigDecimal.valueOf(amount), material);
    }
    public String dbCurrency() {
        return currency == CurrencyType.ITEM ? "item:" + itemMaterial : currency.id();
    }
    public static TitlePurchaseOffer parseDb(String currencyName, BigDecimal price) {
        if (currencyName != null && currencyName.toLowerCase(java.util.Locale.ROOT).startsWith("item:")) {
            String material = currencyName.substring("item:".length());
            return new TitlePurchaseOffer(CurrencyType.ITEM, price, material);
        }
        return new TitlePurchaseOffer(CurrencyType.parse(currencyName), price);
    }
}
