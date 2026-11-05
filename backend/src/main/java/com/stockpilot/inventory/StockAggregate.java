package com.stockpilot.inventory;

import java.util.UUID;

public record StockAggregate(UUID productId, long quantity) {
}
