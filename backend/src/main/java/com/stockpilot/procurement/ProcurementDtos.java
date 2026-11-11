package com.stockpilot.procurement;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotEmpty;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ProcurementDtos {
    private ProcurementDtos() {}

    public record SupplierRequest(
            @NotBlank @Size(max = 200) String name,
            @Email @Size(max = 320) String email,
            @Size(max = 50) String phone,
            @Size(max = 1000) String notes,
            boolean active) {}

    public record SupplierResponse(
            UUID id, String name, String email, String phone, String notes,
            boolean active, Instant createdAt) {}

    public record SupplierOrderHistory(
            UUID id, String orderNumber, PurchaseOrderStatus status, Instant createdAt,
            LocalDate expectedDeliveryDate, Instant lastReceivedAt, BigDecimal total) {}

    public record SupplierPriceHistory(
            UUID productId, String productName, String sku, BigDecimal unitCost,
            String orderNumber, Instant orderedAt) {}

    public record SupplierPerformanceResponse(
            long totalOrders, long openOrders, long onTimeDeliveries, long lateDeliveries,
            List<SupplierOrderHistory> recentOrders, List<SupplierPriceHistory> recentPrices) {}

    public record SupplierProductRequest(
            @Size(max = 100) String supplierSku,
            @NotNull @DecimalMin("0.0") @Digits(integer = 10, fraction = 2) BigDecimal unitCost,
            @Min(1) int minimumOrderQuantity,
            @Min(0) Integer leadTimeDays,
            boolean preferredSupplier) {}

    public record PurchaseOrderLineRequest(
            @NotNull UUID productId,
            @Min(1) int quantity,
            @DecimalMin("0.0") @Digits(integer = 10, fraction = 2) BigDecimal unitCost) {}

    public record PurchaseOrderRequest(
            @NotNull UUID supplierId,
            LocalDate expectedDeliveryDate,
            @Size(max = 1000) String notes,
            @NotEmpty @Size(max = 100) List<@Valid PurchaseOrderLineRequest> lines) {}

    public record BatchPurchaseOrderRequest(
            @NotEmpty @Size(max = 50) List<@Valid PurchaseOrderRequest> orders) {}

    public record ReceivedLineRequest(
            @NotNull UUID productId,
            @Min(1) int quantity) {}

    public record ReceivePurchaseOrderRequest(
            @NotEmpty @Size(max = 100) List<@Valid ReceivedLineRequest> lines) {}

    public record PurchaseOrderLineResponse(
            UUID productId, String productName, String sku, String supplierSku, String unit,
            int orderedQuantity, int receivedQuantity, BigDecimal unitCost,
            BigDecimal lineTotal) {}

    public record PurchaseOrderResponse(
            UUID id, String orderNumber, UUID supplierId, String supplierName,
            PurchaseOrderStatus status, LocalDate expectedDeliveryDate,
            String notes, Instant createdAt, Instant orderedAt, Instant lastReceivedAt,
            List<PurchaseOrderLineResponse> lines, BigDecimal total) {}

    public record PurchaseOrderSummary(long openCount, BigDecimal openValue) {}
}
