package com.dracave.title.storage;
import java.math.BigDecimal;
import java.util.UUID;
public record PurchaseRecord(
        UUID playerId,
        String titleId,
        UUID operationId,
        String currency,
        BigDecimal amount,
        String state,
        long updatedAt
) {
}
