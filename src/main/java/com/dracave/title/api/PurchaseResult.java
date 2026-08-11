package com.dracave.title.api;
import java.math.BigDecimal;
import java.util.UUID;
public record PurchaseResult(
        PurchaseStatus status,
        UUID operationId,
        String titleId,
        String currency,
        BigDecimal price,
        String detail
) {
}
