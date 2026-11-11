package com.stockpilot.inventory;

import com.stockpilot.inventory.InventoryDtos.MovementRequest;
import com.stockpilot.inventory.InventoryDtos.MovementResponse;
import com.stockpilot.inventory.InventoryDtos.ProductRequest;
import com.stockpilot.inventory.InventoryDtos.ProductResponse;
import com.stockpilot.inventory.InventoryDtos.Recommendation;
import com.stockpilot.currency.CurrencyService;
import com.stockpilot.organization.OrganizationContext;
import com.stockpilot.procurement.PreferredSupplierProduct;
import com.stockpilot.procurement.PurchaseOrderLineRepository;
import com.stockpilot.procurement.PurchaseOrderStatus;
import com.stockpilot.procurement.SupplierProductRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Predicate;
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
    private final PurchaseOrderLineRepository orderLines;
    private final SupplierProductRepository supplierProducts;
    private final OrganizationContext organizationContext;
    private final CurrencyService currency;
    private final int demandWindowDays;
    private final int safetyStockDays;

    public InventoryService(ProductRepository products, StockMovementRepository movements,
                            PurchaseOrderLineRepository orderLines,
                            SupplierProductRepository supplierProducts,
                            OrganizationContext organizationContext,
                            CurrencyService currency,
                            @Value("${app.replenishment.demand-window-days:30}") int demandWindowDays,
                            @Value("${app.replenishment.safety-stock-days:3}") int safetyStockDays) {
        this.products = products;
        this.movements = movements;
        this.orderLines = orderLines;
        this.supplierProducts = supplierProducts;
        this.organizationContext = organizationContext;
        this.currency = currency;
        this.demandWindowDays = demandWindowDays;
        this.safetyStockDays = safetyStockDays;
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> listProducts() {
        UUID organizationId = organizationContext.currentOrganizationId();
        Map<UUID, Long> balances = movements.getAllOnHand(organizationId).stream()
                .collect(Collectors.toMap(StockAggregate::productId, StockAggregate::quantity));
        Map<UUID, Long> onOrder = orderLines.getOutstandingQuantities(organizationId, openOrderStatuses()).stream()
                .collect(Collectors.toMap(StockAggregate::productId, StockAggregate::quantity));
        return products.findAllByOrganizationIdAndActiveTrueOrderByNameAsc(organizationId).stream()
                .map(product -> toProductResponse(product, balances.getOrDefault(product.getId(), 0L),
                        onOrder.getOrDefault(product.getId(), 0L)))
                .toList();
    }

    @Transactional
    public ProductResponse createProduct(ProductRequest request) {
        currency.requireRsdPricesReady();
        UUID organizationId = organizationContext.currentOrganizationId();
        String sku = request.sku().trim().toUpperCase(Locale.ROOT);
        if (products.findByOrganizationIdAndSkuIgnoreCase(organizationId, sku).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "SKU already exists");
        }
        try {
            Product product = products.saveAndFlush(new Product(
                    organizationId, sku, request.name().trim(), request.category(),
                    request.unit().trim(), request.minimumStock(), request.leadTimeDays(),
                    request.unitCost()));
            return toProductResponse(product, 0, 0);
        } catch (DataIntegrityViolationException exception) {
            if (isSkuConflict(exception)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "SKU already exists", exception);
            }
            throw exception;
        }
    }

    @Transactional
    public ProductResponse updateProduct(UUID productId, ProductRequest request) {
        currency.requireRsdPricesReady();
        UUID organizationId = organizationContext.currentOrganizationId();
        Product product = products.findByIdForUpdateAndOrganizationId(productId, organizationId)
                .filter(Product::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
        String sku = request.sku().trim().toUpperCase(Locale.ROOT);
        if (products.findByOrganizationIdAndSkuIgnoreCase(organizationId, sku)
                .filter(existing -> !existing.getId().equals(productId)).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "SKU already exists");
        }
        product.update(sku, request.name().trim(),
                request.category() == null || request.category().isBlank() ? null : request.category().trim(),
                request.unit().trim(), request.minimumStock(), request.leadTimeDays(), request.unitCost());
        try {
            products.saveAndFlush(product);
        } catch (DataIntegrityViolationException exception) {
            if (isSkuConflict(exception)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "SKU already exists", exception);
            }
            throw exception;
        }
        return toProductResponse(product, onHand(product),
                outstandingByProduct(organizationId).getOrDefault(productId, 0L));
    }

    @Transactional
    public MovementResponse recordMovement(MovementRequest request) {
        UUID organizationId = organizationContext.currentOrganizationId();
        Product product = products.findByIdForUpdateAndOrganizationId(request.productId(), organizationId)
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
                organizationId, product.getId(), request.type(), request.quantity(), Instant.now(), request.note()));
        return toMovementResponse(movement, product);
    }

    @Transactional(readOnly = true)
    public List<Recommendation> recommendations() {
        UUID organizationId = organizationContext.currentOrganizationId();
        Instant since = Instant.now().minus(demandWindowDays, ChronoUnit.DAYS);
        Map<UUID, Long> balances = movements.getAllOnHand(organizationId).stream()
                .collect(Collectors.toMap(StockAggregate::productId, StockAggregate::quantity));
        Map<UUID, Long> demand = movements.getAllOutboundSince(organizationId, since).stream()
                .collect(Collectors.toMap(StockAggregate::productId, StockAggregate::quantity));
        Map<UUID, Long> onOrder = orderLines.getOutstandingQuantities(organizationId, openOrderStatuses()).stream()
                .collect(Collectors.toMap(StockAggregate::productId, StockAggregate::quantity));
        Map<UUID, PreferredSupplierProduct> preferredSuppliers = supplierProducts
                .findPreferredByOrganizationId(organizationId).stream()
                .collect(Collectors.toMap(PreferredSupplierProduct::productId, item -> item));
        return products.findAllByOrganizationIdAndActiveTrueOrderByNameAsc(organizationId).stream()
                .map(product -> recommendation(product, balances.getOrDefault(product.getId(), 0L),
                        demand.getOrDefault(product.getId(), 0L), onOrder.getOrDefault(product.getId(), 0L),
                        preferredSuppliers.get(product.getId())))
                .filter(recommendation -> recommendation.recommendedQuantity() > 0)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MovementResponse> recentMovements() {
        UUID organizationId = organizationContext.currentOrganizationId();
        return movements.findTop8ByOrganizationIdOrderByOccurredAtDesc(organizationId).stream()
                .map(movement -> {
                    Product product = products.findByIdAndOrganizationId(movement.getProductId(), organizationId)
                            .orElseThrow(() -> new IllegalStateException("Movement refers to a missing product"));
                    return toMovementResponse(movement, product);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<MovementResponse> movementHistory(UUID productId, StockMovementType type,
                                                  Instant fromTime, Instant toTime, Pageable pageable) {
        UUID organizationId = organizationContext.currentOrganizationId();
        Specification<StockMovement> filters = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.equal(root.get("organizationId"), organizationId));
            if (productId != null) {
                predicates.add(criteriaBuilder.equal(root.get("productId"), productId));
            }
            if (type != null) {
                predicates.add(criteriaBuilder.equal(root.get("type"), type));
            }
            if (fromTime != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("occurredAt"), fromTime));
            }
            if (toTime != null) {
                predicates.add(criteriaBuilder.lessThan(root.get("occurredAt"), toTime));
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
        Page<StockMovement> page = movements.findAll(filters, pageable);
        List<UUID> productIds = page.getContent().stream().map(StockMovement::getProductId).distinct().toList();
        Map<UUID, Product> productById = new HashMap<>();
        products.findAllByOrganizationIdAndIdIn(organizationId, productIds)
                .forEach(product -> productById.put(product.getId(), product));
        return page.map(movement -> {
            Product product = productById.get(movement.getProductId());
            if (product == null) {
                throw new IllegalStateException("Movement refers to a missing product");
            }
            return toMovementResponse(movement, product);
        });
    }

    @Transactional(readOnly = true)
    public long onHand(Product product) {
        return movements.getOnHand(product.getOrganizationId(), product.getId());
    }

    @Transactional(readOnly = true)
    public Recommendation recommendation(Product product, Instant since) {
        return recommendation(product, onHand(product),
                movements.getOutboundQuantitySince(product.getOrganizationId(), product.getId(), since),
                orderLines.getOutstandingQuantities(product.getOrganizationId(), openOrderStatuses()).stream()
                        .filter(item -> item.productId().equals(product.getId()))
                        .mapToLong(StockAggregate::quantity)
                        .findFirst().orElse(0),
                supplierProducts.findPreferredByOrganizationIdAndProductId(
                        product.getOrganizationId(), product.getId()).orElse(null));
    }

    private Recommendation recommendation(Product product, long onHand, long demand, long onOrder,
                                          PreferredSupplierProduct preferredSupplier) {
        double averageDailyDemand = (double) demand / demandWindowDays;
        boolean lowConfidence = demand == 0;
        int leadTimeDays = preferredSupplier != null && preferredSupplier.leadTimeDays() != null
                ? preferredSupplier.leadTimeDays() : product.getLeadTimeDays();
        long targetStock = lowConfidence
                ? product.getMinimumStock()
                : (long) Math.ceil(averageDailyDemand * (leadTimeDays + safetyStockDays));
        long recommendedQuantity = Math.max(0, Math.max(targetStock, product.getMinimumStock()) - onHand - onOrder);
        int minimumOrderQuantity = preferredSupplier == null ? 1 : preferredSupplier.minimumOrderQuantity();
        if (recommendedQuantity > 0) {
            recommendedQuantity = Math.max(recommendedQuantity, minimumOrderQuantity);
        }
        BigDecimal unitCost = preferredSupplier == null ? product.getUnitCost() : preferredSupplier.unitCost();
        BigDecimal estimatedCost = unitCost
                .multiply(BigDecimal.valueOf(recommendedQuantity))
                .setScale(2, RoundingMode.HALF_UP);

        return new Recommendation(product.getId(), product.getSku(), product.getName(), product.getUnit(),
                onHand, onOrder, demand, averageDailyDemand, product.getMinimumStock(), targetStock,
                leadTimeDays, safetyStockDays,
                preferredSupplier == null ? null : preferredSupplier.supplierId(),
                preferredSupplier == null ? null : preferredSupplier.supplierName(),
                preferredSupplier == null ? null : preferredSupplier.supplierSku(),
                minimumOrderQuantity, unitCost,
                recommendedQuantity, lowConfidence, estimatedCost);
    }

    private List<PurchaseOrderStatus> openOrderStatuses() {
        return List.of(PurchaseOrderStatus.DRAFT, PurchaseOrderStatus.ORDERED,
                PurchaseOrderStatus.PARTIALLY_RECEIVED);
    }

    private Map<UUID, Long> outstandingByProduct(UUID organizationId) {
        return orderLines.getOutstandingQuantities(organizationId, openOrderStatuses()).stream()
                .collect(Collectors.toMap(StockAggregate::productId, StockAggregate::quantity));
    }

    private ProductResponse toProductResponse(Product product, long onHand, long onOrder) {
        return new ProductResponse(product.getId(), product.getSku(), product.getName(),
                product.getCategory(), product.getUnit(), product.getMinimumStock(),
                product.getLeadTimeDays(), product.getUnitCost(), onHand, onOrder);
    }

    private MovementResponse toMovementResponse(StockMovement movement, Product product) {
        return new MovementResponse(movement.getId(), product.getId(), product.getName(),
                movement.getType(), movement.getQuantity(), movement.getOccurredAt(), movement.getNote(),
                movement.getPurchaseOrderId(), movement.getPurchaseOrderNumber());
    }

    private boolean isSkuConflict(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException violation
                    && "uq_products_organization_sku".equals(violation.getConstraintName())) {
                return true;
            }
        }
        return false;
    }
}
