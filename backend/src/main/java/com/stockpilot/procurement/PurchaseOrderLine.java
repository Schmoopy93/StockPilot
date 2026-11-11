package com.stockpilot.procurement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "purchase_order_lines")
public class PurchaseOrderLine {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @NotNull
    @Column(name = "purchase_order_id", nullable = false, updatable = false)
    private UUID purchaseOrderId;

    @NotNull
    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;

    @NotNull
    @Size(max = 200)
    @Column(name = "product_name", nullable = false, length = 200, updatable = false)
    private String productName;

    @NotNull
    @Size(max = 64)
    @Column(nullable = false, length = 64, updatable = false)
    private String sku;

    @Size(max = 100)
    @Column(name = "supplier_sku", length = 100, updatable = false)
    private String supplierSku;

    @NotNull
    @Size(max = 16)
    @Column(nullable = false, length = 16, updatable = false)
    private String unit;

    @Min(1)
    @Column(name = "ordered_quantity", nullable = false)
    private int orderedQuantity;

    @Min(0)
    @Column(name = "received_quantity", nullable = false)
    private int receivedQuantity;

    @NotNull
    @DecimalMin("0.0")
    @Column(name = "unit_cost", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitCost;

    protected PurchaseOrderLine() {
    }

    public PurchaseOrderLine(UUID organizationId, UUID purchaseOrderId, UUID productId,
                             String productName, String sku, String unit, int orderedQuantity,
                             BigDecimal unitCost) {
        this(organizationId, purchaseOrderId, productId, productName, sku, null,
                unit, orderedQuantity, unitCost);
    }

    public PurchaseOrderLine(UUID organizationId, UUID purchaseOrderId, UUID productId,
                             String productName, String sku, String supplierSku, String unit,
                             int orderedQuantity, BigDecimal unitCost) {
        this.organizationId = organizationId;
        this.purchaseOrderId = purchaseOrderId;
        this.productId = productId;
        this.productName = productName;
        this.sku = sku;
        this.supplierSku = supplierSku;
        this.unit = unit;
        this.orderedQuantity = orderedQuantity;
        this.receivedQuantity = 0;
        this.unitCost = unitCost;
    }

    public void receive(int quantity) {
        this.receivedQuantity += quantity;
    }

    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public UUID getPurchaseOrderId() { return purchaseOrderId; }
    public UUID getProductId() { return productId; }
    public String getProductName() { return productName; }
    public String getSku() { return sku; }
    public String getSupplierSku() { return supplierSku; }
    public String getUnit() { return unit; }
    public int getOrderedQuantity() { return orderedQuantity; }
    public int getReceivedQuantity() { return receivedQuantity; }
    public BigDecimal getUnitCost() { return unitCost; }
}
