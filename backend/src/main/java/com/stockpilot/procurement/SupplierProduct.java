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
@Table(name = "supplier_products")
public class SupplierProduct {

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
    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;

    @Size(max = 100)
    @Column(name = "supplier_sku", length = 100)
    private String supplierSku;

    @NotNull
    @DecimalMin("0.0")
    @Column(name = "unit_cost", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitCost;

    @Min(1)
    @Column(name = "minimum_order_quantity", nullable = false)
    private int minimumOrderQuantity;

    @Min(0)
    @Column(name = "lead_time_days")
    private Integer leadTimeDays;

    @Column(name = "preferred_supplier", nullable = false)
    private boolean preferredSupplier;

    protected SupplierProduct() {
    }

    public SupplierProduct(UUID organizationId, UUID supplierId, UUID productId, String supplierSku,
                           BigDecimal unitCost, int minimumOrderQuantity, Integer leadTimeDays,
                           boolean preferredSupplier) {
        this.organizationId = organizationId;
        this.supplierId = supplierId;
        this.productId = productId;
        update(supplierSku, unitCost, minimumOrderQuantity, leadTimeDays, preferredSupplier);
    }

    public void update(String supplierSku, BigDecimal unitCost, int minimumOrderQuantity,
                       Integer leadTimeDays, boolean preferredSupplier) {
        this.supplierSku = supplierSku;
        this.unitCost = unitCost;
        this.minimumOrderQuantity = minimumOrderQuantity;
        this.leadTimeDays = leadTimeDays;
        this.preferredSupplier = preferredSupplier;
    }

    public void setPreferredSupplier(boolean preferredSupplier) {
        this.preferredSupplier = preferredSupplier;
    }

    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public UUID getSupplierId() { return supplierId; }
    public UUID getProductId() { return productId; }
    public String getSupplierSku() { return supplierSku; }
    public BigDecimal getUnitCost() { return unitCost; }
    public int getMinimumOrderQuantity() { return minimumOrderQuantity; }
    public Integer getLeadTimeDays() { return leadTimeDays; }
    public boolean isPreferredSupplier() { return preferredSupplier; }
}
