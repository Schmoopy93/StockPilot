package com.stockpilot.procurement;

import java.math.BigDecimal;
import java.util.UUID;

public record PreferredSupplierProduct(
        UUID productId,
        UUID supplierId,
        String supplierName,
        String supplierSku,
        BigDecimal unitCost,
        int minimumOrderQuantity,
        Integer leadTimeDays) {
}
