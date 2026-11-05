package com.stockpilot.inventory;

import com.stockpilot.inventory.InventoryDtos.DashboardResponse;
import com.stockpilot.inventory.InventoryDtos.ProductResponse;
import com.stockpilot.inventory.InventoryDtos.Recommendation;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    private final ProductRepository products;
    private final InventoryService inventory;

    public DashboardService(ProductRepository products, InventoryService inventory) {
        this.products = products;
        this.inventory = inventory;
    }

    @Transactional(readOnly = true)
    public DashboardResponse getDashboard() {
        List<ProductResponse> productList = inventory.listProducts();
        List<Recommendation> recommendations = inventory.recommendations();
        List<ProductResponse> lowStock = productList.stream()
                .filter(product -> product.onHand() <= product.minimumStock())
                .limit(6)
                .toList();
        long unitsOnHand = productList.stream().mapToLong(ProductResponse::onHand).sum();
        long productsBelowMinimum = productList.stream()
                .filter(product -> product.onHand() <= product.minimumStock()).count();
        BigDecimal estimatedCost = recommendations.stream()
                .map(Recommendation::estimatedCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new DashboardResponse(productList.size(), unitsOnHand, productsBelowMinimum,
                recommendations.size(), estimatedCost, lowStock, recommendations,
                inventory.recentMovements());
    }
}
