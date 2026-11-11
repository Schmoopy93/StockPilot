package com.stockpilot.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @jakarta.validation.constraints.NotNull
    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @NotBlank
    @Size(max = 64)
    @Column(nullable = false, length = 64)
    private String sku;

    @NotBlank
    @Size(max = 200)
    @Column(nullable = false, length = 200)
    private String name;

    @Size(max = 100)
    @Column(length = 100)
    private String category;

    @NotBlank
    @Size(max = 16)
    @Column(nullable = false, length = 16)
    private String unit;

    @Min(0)
    @Column(name = "minimum_stock", nullable = false)
    private int minimumStock;

    @Min(0)
    @Column(name = "lead_time_days", nullable = false)
    private int leadTimeDays;

    @DecimalMin("0.0")
    @Column(name = "unit_cost", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitCost;

    @Column(nullable = false)
    private boolean active = true;

    protected Product() {
    }

    public Product(UUID organizationId, String sku, String name, String category, String unit, int minimumStock,
                   int leadTimeDays, BigDecimal unitCost) {
        this.organizationId = organizationId;
        this.sku = sku;
        this.name = name;
        this.category = category;
        this.unit = unit;
        this.minimumStock = minimumStock;
        this.leadTimeDays = leadTimeDays;
        this.unitCost = unitCost;
        this.active = true;
    }

    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public String getSku() { return sku; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public String getUnit() { return unit; }
    public int getMinimumStock() { return minimumStock; }
    public int getLeadTimeDays() { return leadTimeDays; }
    public BigDecimal getUnitCost() { return unitCost; }
    public boolean isActive() { return active; }

    public void update(String sku, String name, String category, String unit, int minimumStock,
                       int leadTimeDays, BigDecimal unitCost) {
        this.sku = sku;
        this.name = name;
        this.category = category;
        this.unit = unit;
        this.minimumStock = minimumStock;
        this.leadTimeDays = leadTimeDays;
        this.unitCost = unitCost;
    }
}
