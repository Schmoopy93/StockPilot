package com.stockpilot.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stock_movements")
public class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @NotNull
    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private StockMovementType type;

    @Column(nullable = false)
    private int quantity;

    @NotNull
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(length = 300)
    private String note;

    @Column(name = "purchase_order_id")
    private UUID purchaseOrderId;

    @Column(name = "purchase_order_number", length = 20)
    private String purchaseOrderNumber;

    protected StockMovement() {
    }

    public StockMovement(UUID organizationId, UUID productId, StockMovementType type, int quantity,
                         Instant occurredAt, String note) {
        this(organizationId, productId, type, quantity, occurredAt, note, null, null);
    }

    public StockMovement(UUID organizationId, UUID productId, StockMovementType type, int quantity,
                         Instant occurredAt, String note, UUID purchaseOrderId, String purchaseOrderNumber) {
        this.organizationId = organizationId;
        this.productId = productId;
        this.type = type;
        this.quantity = quantity;
        this.occurredAt = occurredAt;
        this.note = note;
        this.purchaseOrderId = purchaseOrderId;
        this.purchaseOrderNumber = purchaseOrderNumber;
    }

    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public UUID getProductId() { return productId; }
    public StockMovementType getType() { return type; }
    public int getQuantity() { return quantity; }
    public Instant getOccurredAt() { return occurredAt; }
    public String getNote() { return note; }
    public UUID getPurchaseOrderId() { return purchaseOrderId; }
    public String getPurchaseOrderNumber() { return purchaseOrderNumber; }
}
