package com.stockpilot.inventory;

import com.stockpilot.inventory.InventoryDtos.MovementRequest;
import com.stockpilot.inventory.InventoryDtos.MovementResponse;
import com.stockpilot.inventory.InventoryDtos.ProductRequest;
import com.stockpilot.inventory.InventoryDtos.ProductResponse;
import com.stockpilot.inventory.InventoryDtos.Recommendation;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Validated
public class InventoryController {

    private final InventoryService inventory;
    private final DashboardService dashboard;

    public InventoryController(InventoryService inventory, DashboardService dashboard) {
        this.inventory = inventory;
        this.dashboard = dashboard;
    }

    @GetMapping("/products")
    public List<ProductResponse> products() {
        return inventory.listProducts();
    }

    @PostMapping("/products")
    public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody ProductRequest request) {
        ProductResponse created = inventory.createProduct(request);
        return ResponseEntity.created(URI.create("/api/products/" + created.id())).body(created);
    }

    @PostMapping("/stock-movements")
    public ResponseEntity<MovementResponse> recordMovement(@Valid @RequestBody MovementRequest request) {
        return ResponseEntity.status(201).body(inventory.recordMovement(request));
    }

    @GetMapping("/replenishment-recommendations")
    public List<Recommendation> recommendations() {
        return inventory.recommendations();
    }

    @GetMapping("/dashboard")
    public com.stockpilot.inventory.InventoryDtos.DashboardResponse dashboard() {
        return dashboard.getDashboard();
    }
}
