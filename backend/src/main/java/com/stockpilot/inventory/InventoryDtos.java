package com.stockpilot.inventory;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class InventoryDtos {
    private InventoryDtos() {}

    public record ProductRequest(
            @NotBlank @Size(max = 64) String sku,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 100) String category,
            @NotBlank @Size(max = 16) String unit,
            @Min(0) int minimumStock,
            @Min(0) int leadTimeDays,
            @NotNull @DecimalMin("0.0") BigDecimal unitCost) {}

    public record ProductResponse(
            UUID id, String sku, String name, String category, String unit,
            int minimumStock, int leadTimeDays, BigDecimal unitCost, long onHand) {}

    public record MovementRequest(
            @NotNull UUID productId,
            @NotNull StockMovementType type,
            int quantity,
            @Size(max = 300) String note) {}

    public record MovementResponse(
            UUID id, UUID productId, String productName, StockMovementType type,
            int quantity, Instant occurredAt, String note) {}

    public record Recommendation(
            UUID productId, String sku, String productName, String unit,
            long onHand, long demandLast30Days, double averageDailyDemand,
            int leadTimeDays, int safetyStockDays, long recommendedQuantity,
            boolean lowConfidence, BigDecimal estimatedCost) {}

    public record DashboardResponse(
            int activeProducts, long unitsOnHand, long productsBelowMinimum,
            long productsToReorder, BigDecimal estimatedReorderCost,
            java.util.List<ProductResponse> lowStock,
            java.util.List<Recommendation> recommendations,
            java.util.List<MovementResponse> recentMovements) {}
}
