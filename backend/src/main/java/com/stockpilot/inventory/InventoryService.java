package com.stockpilot.inventory;

import com.stockpilot.inventory.InventoryDtos.MovementRequest;
import com.stockpilot.inventory.InventoryDtos.MovementResponse;
import com.stockpilot.inventory.InventoryDtos.ProductRequest;
import com.stockpilot.inventory.InventoryDtos.ProductResponse;
import com.stockpilot.inventory.InventoryDtos.Recommendation;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class InventoryService {

    private final ProductRepository products;
    private final StockMovementRepository movements;
    private final int demandWindowDays;
    private final int safetyStockDays;

    public InventoryService(ProductRepository products, StockMovementRepository movements,
                            @Value("${app.replenishment.demand-window-days:30}") int demandWindowDays,
                            @Value("${app.replenishment.safety-stock-days:3}") int safetyStockDays) {
        this.products = products;
        this.movements = movements;
        this.demandWindowDays = demandWindowDays;
        this.safetyStockDays = safetyStockDays;
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> listProducts() {
        Map<UUID, Long> balances = movements.getAllOnHand().stream()
                .collect(Collectors.toMap(StockAggregate::productId, StockAggregate::quantity));
        return products.findAllByActiveTrueOrderByNameAsc().stream()
                .map(product -> toProductResponse(product, balances.getOrDefault(product.getId(), 0L)))
                .toList();
    }

    @Transactional
    public ProductResponse createProduct(ProductRequest request) {
        String sku = request.sku().trim().toUpperCase(Locale.ROOT);
        if (products.findBySkuIgnoreCase(sku).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "SKU already exists");
        }
        try {
            Product product = products.saveAndFlush(new Product(
                    sku, request.name().trim(), request.category(),
                    request.unit().trim(), request.minimumStock(), request.leadTimeDays(),
                    request.unitCost()));
            return toProductResponse(product, 0);
        } catch (DataIntegrityViolationException exception) {
            if (isSkuConflict(exception)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "SKU already exists", exception);
            }
            throw exception;
        }
    }

    @Transactional
    public MovementResponse recordMovement(MovementRequest request) {
        Product product = products.findByIdForUpdate(request.productId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
        if (request.quantity() == 0
                || (request.type() != StockMovementType.ADJUSTMENT && request.quantity() < 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Quantity must be positive for inbound/outbound movements; adjustments must be non-zero");
        }
        if (request.type() == StockMovementType.OUTBOUND && request.quantity() > onHand(product)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Insufficient stock for outbound movement");
        }
        if (request.type() == StockMovementType.ADJUSTMENT
                && request.quantity() < 0 && -(long) request.quantity() > onHand(product)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Adjustment cannot reduce stock below zero");
        }
        StockMovement movement = movements.save(new StockMovement(
                product.getId(), request.type(), request.quantity(), Instant.now(), request.note()));
        return toMovementResponse(movement, product);
    }

    @Transactional(readOnly = true)
    public List<Recommendation> recommendations() {
        Instant since = Instant.now().minus(demandWindowDays, ChronoUnit.DAYS);
        Map<UUID, Long> balances = movements.getAllOnHand().stream()
                .collect(Collectors.toMap(StockAggregate::productId, StockAggregate::quantity));
        Map<UUID, Long> demand = movements.getAllOutboundSince(since).stream()
                .collect(Collectors.toMap(StockAggregate::productId, StockAggregate::quantity));
        return products.findAllByActiveTrueOrderByNameAsc().stream()
                .map(product -> recommendation(product, balances.getOrDefault(product.getId(), 0L),
                        demand.getOrDefault(product.getId(), 0L)))
                .filter(recommendation -> recommendation.recommendedQuantity() > 0)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MovementResponse> recentMovements() {
        return movements.findTop8ByOrderByOccurredAtDesc().stream()
                .map(movement -> {
                    Product product = products.findById(movement.getProductId())
                            .orElseThrow(() -> new IllegalStateException("Movement refers to a missing product"));
                    return toMovementResponse(movement, product);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public long onHand(Product product) {
        return movements.getOnHand(product.getId());
    }

    @Transactional(readOnly = true)
    public Recommendation recommendation(Product product, Instant since) {
        return recommendation(product, onHand(product), movements.getOutboundQuantitySince(product.getId(), since));
    }

    private Recommendation recommendation(Product product, long onHand, long demand) {
        double averageDailyDemand = (double) demand / demandWindowDays;
        boolean lowConfidence = demand == 0;
        long targetStock = lowConfidence
                ? product.getMinimumStock()
                : (long) Math.ceil(averageDailyDemand * (product.getLeadTimeDays() + safetyStockDays));
        long recommendedQuantity = Math.max(0, Math.max(targetStock, product.getMinimumStock()) - onHand);
        BigDecimal estimatedCost = product.getUnitCost()
                .multiply(BigDecimal.valueOf(recommendedQuantity))
                .setScale(2, RoundingMode.HALF_UP);

        return new Recommendation(product.getId(), product.getSku(), product.getName(), product.getUnit(),
                onHand, demand, averageDailyDemand, product.getLeadTimeDays(), safetyStockDays,
                recommendedQuantity, lowConfidence, estimatedCost);
    }

    private ProductResponse toProductResponse(Product product, long onHand) {
        return new ProductResponse(product.getId(), product.getSku(), product.getName(),
                product.getCategory(), product.getUnit(), product.getMinimumStock(),
                product.getLeadTimeDays(), product.getUnitCost(), onHand);
    }

    private MovementResponse toMovementResponse(StockMovement movement, Product product) {
        return new MovementResponse(movement.getId(), product.getId(), product.getName(),
                movement.getType(), movement.getQuantity(), movement.getOccurredAt(), movement.getNote());
    }

    private boolean isSkuConflict(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException violation
                    && "products_sku_key".equals(violation.getConstraintName())) {
                return true;
            }
        }
        return false;
    }
}
