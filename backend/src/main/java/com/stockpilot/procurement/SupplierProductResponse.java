package com.stockpilot.procurement;

import java.math.BigDecimal;
import java.util.UUID;

public record SupplierProductResponse(
        UUID id,
        UUID supplierId,
        String supplierName,
        UUID productId,
        String productName,
        String productSku,
        String supplierSku,
        BigDecimal unitCost,
        int minimumOrderQuantity,
        Integer leadTimeDays,
        boolean preferredSupplier) {
}
