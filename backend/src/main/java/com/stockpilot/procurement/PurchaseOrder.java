package com.stockpilot.procurement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "purchase_orders")
public class PurchaseOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @NotNull
    @Column(name = "supplier_id", nullable = false, updatable = false)
    private UUID supplierId;

    @NotNull
    @Column(name = "order_number", nullable = false, length = 20, updatable = false)
    private String orderNumber;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private PurchaseOrderStatus status;

    @Column(name = "expected_delivery_date")
    private LocalDate expectedDeliveryDate;

    @Size(max = 1000)
    @Column(length = 1000)
    private String notes;

    @NotNull
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "ordered_at")
    private Instant orderedAt;

    @Column(name = "last_received_at")
    private Instant lastReceivedAt;

    protected PurchaseOrder() {
    }

    public PurchaseOrder(UUID organizationId, UUID supplierId, String orderNumber,
                         LocalDate expectedDeliveryDate, String notes) {
        this.organizationId = organizationId;
        this.supplierId = supplierId;
        this.orderNumber = orderNumber;
        this.status = PurchaseOrderStatus.DRAFT;
        this.expectedDeliveryDate = expectedDeliveryDate;
        this.notes = notes;
        this.createdAt = Instant.now();
    }

    public void markOrdered() {
        this.status = PurchaseOrderStatus.ORDERED;
        this.orderedAt = Instant.now();
    }

    public void markReceived(boolean partiallyReceived, Instant receivedAt) {
        this.status = partiallyReceived
                ? PurchaseOrderStatus.PARTIALLY_RECEIVED
                : PurchaseOrderStatus.RECEIVED;
        this.lastReceivedAt = receivedAt;
    }

    public void cancel() {
        this.status = PurchaseOrderStatus.CANCELLED;
    }

    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public UUID getSupplierId() { return supplierId; }
    public String getOrderNumber() { return orderNumber; }
    public PurchaseOrderStatus getStatus() { return status; }
    public LocalDate getExpectedDeliveryDate() { return expectedDeliveryDate; }
    public String getNotes() { return notes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getOrderedAt() { return orderedAt; }
    public Instant getLastReceivedAt() { return lastReceivedAt; }
}
