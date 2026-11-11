package com.stockpilot.procurement;

import com.stockpilot.currency.CurrencyService;
import com.stockpilot.inventory.Product;
import com.stockpilot.inventory.ProductRepository;
import com.stockpilot.inventory.StockMovement;
import com.stockpilot.inventory.StockMovementRepository;
import com.stockpilot.inventory.StockMovementType;
import com.stockpilot.organization.OrganizationContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static com.stockpilot.procurement.ProcurementDtos.*;

@Service
public class ProcurementService {

    private final SupplierRepository suppliers;
    private final SupplierProductRepository supplierProducts;
    private final PurchaseOrderRepository orders;
    private final PurchaseOrderLineRepository orderLines;
    private final ProductRepository products;
    private final StockMovementRepository movements;
    private final OrganizationContext organizationContext;
    private final CurrencyService currency;

    public ProcurementService(SupplierRepository suppliers, SupplierProductRepository supplierProducts,
                              PurchaseOrderRepository orders,
                              PurchaseOrderLineRepository orderLines, ProductRepository products,
                              StockMovementRepository movements, OrganizationContext organizationContext,
                              CurrencyService currency) {
        this.suppliers = suppliers;
        this.supplierProducts = supplierProducts;
        this.orders = orders;
        this.orderLines = orderLines;
        this.products = products;
        this.movements = movements;
        this.organizationContext = organizationContext;
        this.currency = currency;
    }

    @Transactional(readOnly = true)
    public List<SupplierResponse> listSuppliers() {
        UUID organizationId = organizationContext.currentOrganizationId();
        return suppliers.findAllByOrganizationIdOrderByNameAsc(organizationId).stream()
                .map(ProcurementService::toSupplierResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public SupplierPerformanceResponse supplierPerformance(UUID supplierId) {
        UUID organizationId = organizationContext.currentOrganizationId();
        suppliers.findByIdAndOrganizationId(supplierId, organizationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Supplier not found"));
        List<PurchaseOrder> recentOrders = orders
                .findTop100ByOrganizationIdAndSupplierIdOrderByCreatedAtDesc(organizationId, supplierId);
        List<UUID> orderIds = recentOrders.stream().map(PurchaseOrder::getId).toList();
        List<PurchaseOrderLine> lines = orderIds.isEmpty()
                ? List.of()
                : orderLines.findAllByOrganizationIdAndPurchaseOrderIdIn(organizationId, orderIds);

        Map<UUID, List<PurchaseOrderLine>> linesByOrder = new HashMap<>();
        Map<UUID, SupplierPriceHistory> latestPrices = new HashMap<>();
        Map<UUID, PurchaseOrder> orderById = new HashMap<>();
        recentOrders.forEach(order -> orderById.put(order.getId(), order));
        lines.sort((first, second) -> orderById.get(second.getPurchaseOrderId()).getCreatedAt()
                .compareTo(orderById.get(first.getPurchaseOrderId()).getCreatedAt()));
        for (PurchaseOrderLine line : lines) {
            linesByOrder.computeIfAbsent(line.getPurchaseOrderId(), ignored -> new ArrayList<>()).add(line);
            PurchaseOrder order = orderById.get(line.getPurchaseOrderId());
            latestPrices.putIfAbsent(line.getProductId(), new SupplierPriceHistory(
                    line.getProductId(), line.getProductName(), line.getSku(), line.getUnitCost(),
                    order.getOrderNumber(), order.getCreatedAt()));
        }

        List<SupplierOrderHistory> history = recentOrders.stream()
                .map(order -> new SupplierOrderHistory(
                        order.getId(), order.getOrderNumber(), order.getStatus(), order.getCreatedAt(),
                        order.getExpectedDeliveryDate(), order.getLastReceivedAt(),
                        linesByOrder.getOrDefault(order.getId(), List.of()).stream()
                                .map(line -> line.getUnitCost().multiply(
                                        BigDecimal.valueOf(line.getOrderedQuantity())))
                                .reduce(BigDecimal.ZERO, BigDecimal::add)))
                .toList();
        return new SupplierPerformanceResponse(
                orders.countByOrganizationIdAndSupplierId(organizationId, supplierId),
                orders.countByOrganizationIdAndSupplierIdAndStatusIn(
                        organizationId, supplierId, openOrderStatuses()),
                orders.countOnTimeDeliveries(organizationId, supplierId),
                orders.countLateDeliveries(organizationId, supplierId),
                history, List.copyOf(latestPrices.values()));
    }

    @Transactional
    public SupplierResponse createSupplier(SupplierRequest request) {
        UUID organizationId = organizationContext.currentOrganizationId();
        Supplier supplier = suppliers.save(new Supplier(organizationId, clean(request.name()),
                cleanNullable(request.email()), cleanNullable(request.phone()), cleanNullable(request.notes())));
        return toSupplierResponse(supplier);
    }

    @Transactional
    public SupplierResponse updateSupplier(UUID supplierId, SupplierRequest request) {
        UUID organizationId = organizationContext.currentOrganizationId();
        Supplier supplier = suppliers.findByIdAndOrganizationId(supplierId, organizationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Supplier not found"));
        supplier.update(clean(request.name()), cleanNullable(request.email()),
                cleanNullable(request.phone()), cleanNullable(request.notes()), request.active());
        return toSupplierResponse(supplier);
    }

    @Transactional(readOnly = true)
    public List<SupplierProductResponse> listSupplierProducts() {
        return supplierProducts.findAllResponsesByOrganizationId(
                organizationContext.currentOrganizationId());
    }

    @Transactional
    public SupplierProductResponse saveSupplierProduct(UUID supplierId, UUID productId,
                                                       SupplierProductRequest request) {
        currency.requireRsdPricesReady();
        UUID organizationId = organizationContext.currentOrganizationId();
        Supplier supplier = suppliers.findByIdAndOrganizationId(supplierId, organizationId)
                .filter(Supplier::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Active supplier not found"));
        Product product = products.findByIdForUpdateAndOrganizationId(productId, organizationId)
                .filter(Product::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));

        SupplierProduct supplierProduct = supplierProducts
                .findByOrganizationIdAndSupplierIdAndProductId(organizationId, supplierId, productId)
                .orElseGet(() -> new SupplierProduct(organizationId, supplierId, productId, null,
                        request.unitCost(), request.minimumOrderQuantity(), request.leadTimeDays(),
                        request.preferredSupplier()));
        if (request.preferredSupplier()) {
            supplierProducts.findAllByOrganizationIdAndProductIdAndPreferredSupplierTrue(
                            organizationId, productId).stream()
                    .filter(existing -> !existing.getSupplierId().equals(supplierId))
                    .forEach(existing -> existing.setPreferredSupplier(false));
        }
        supplierProduct.update(cleanNullable(request.supplierSku()),
                request.unitCost().setScale(2, RoundingMode.HALF_UP),
                request.minimumOrderQuantity(), request.leadTimeDays(), request.preferredSupplier());
        SupplierProduct saved = supplierProducts.save(supplierProduct);
        return new SupplierProductResponse(saved.getId(), supplier.getId(), supplier.getName(),
                product.getId(), product.getName(), product.getSku(), saved.getSupplierSku(),
                saved.getUnitCost(), saved.getMinimumOrderQuantity(), saved.getLeadTimeDays(),
                saved.isPreferredSupplier());
    }

    @Transactional
    public void deleteSupplierProduct(UUID supplierId, UUID productId) {
        UUID organizationId = organizationContext.currentOrganizationId();
        SupplierProduct supplierProduct = supplierProducts
                .findByOrganizationIdAndSupplierIdAndProductId(organizationId, supplierId, productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Supplier product not found"));
        supplierProducts.delete(supplierProduct);
    }

    @Transactional(readOnly = true)
    public Page<PurchaseOrderResponse> listOrders(UUID supplierId, PurchaseOrderStatus status,
                                                   LocalDate from, LocalDate to, boolean overdue,
                                                   Pageable pageable) {
        UUID organizationId = organizationContext.currentOrganizationId();
        Specification<PurchaseOrder> filters = (root, query, criteriaBuilder) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.equal(root.get("organizationId"), organizationId));
            if (supplierId != null) {
                predicates.add(criteriaBuilder.equal(root.get("supplierId"), supplierId));
            }
            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }
            ZoneId zone = ZoneId.of("Europe/Belgrade");
            if (from != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"),
                        from.atStartOfDay(zone).toInstant()));
            }
            if (to != null) {
                predicates.add(criteriaBuilder.lessThan(root.get("createdAt"),
                        to.plusDays(1).atStartOfDay(zone).toInstant()));
            }
            if (overdue) {
                predicates.add(criteriaBuilder.lessThan(root.get("expectedDeliveryDate"), LocalDate.now(zone)));
                predicates.add(root.get("status").in(PurchaseOrderStatus.ORDERED,
                        PurchaseOrderStatus.PARTIALLY_RECEIVED));
            }
            return criteriaBuilder.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        return orders.findAll(filters, pageable).map(this::toOrderResponse);
    }

    @Transactional(readOnly = true)
    public PurchaseOrderSummary orderSummary() {
        UUID organizationId = organizationContext.currentOrganizationId();
        return new PurchaseOrderSummary(
                orders.countByOrganizationIdAndStatusIn(organizationId, openOrderStatuses()),
                orderLines.getOutstandingValue(organizationId,
                        List.of(PurchaseOrderStatus.ORDERED, PurchaseOrderStatus.PARTIALLY_RECEIVED)));
    }

    @Transactional
    public PurchaseOrderResponse createOrder(PurchaseOrderRequest request) {
        currency.requireRsdPricesReady();
        UUID organizationId = organizationContext.currentOrganizationId();
        Supplier supplier = suppliers.findByIdAndOrganizationId(request.supplierId(), organizationId)
                .filter(Supplier::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Active supplier not found"));

        Map<UUID, PurchaseOrderLineRequest> requestedLineByProduct = new HashMap<>();
        for (PurchaseOrderLineRequest line : request.lines()) {
            if (requestedLineByProduct.putIfAbsent(line.productId(), line) != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A product may only appear once per order");
            }
        }
        Set<UUID> productIds = requestedLineByProduct.keySet();

        List<Product> selectedProducts = products.findAllByOrganizationIdAndIdIn(
                organizationId, List.copyOf(productIds));
        Map<UUID, Product> productById = new HashMap<>();
        for (Product product : selectedProducts) {
            if (!product.isActive()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
            }
            productById.put(product.getId(), product);
        }
        if (productById.size() != productIds.size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
        }

        Map<UUID, SupplierProduct> supplierProductByProduct = new HashMap<>();
        for (UUID productId : productIds) {
            SupplierProduct supplierProduct = supplierProducts
                    .findByOrganizationIdAndSupplierIdAndProductId(organizationId, supplier.getId(), productId)
                    .orElse(null);
            if (supplierProduct != null
                    && requestedLineByProduct.get(productId).quantity() < supplierProduct.getMinimumOrderQuantity()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Quantity for " + productById.get(productId).getName()
                                + " is below the supplier minimum of "
                                + supplierProduct.getMinimumOrderQuantity());
            }
            if (supplierProduct != null) {
                supplierProductByProduct.put(productId, supplierProduct);
            }
        }

        PurchaseOrder order = orders.saveAndFlush(new PurchaseOrder(organizationId, supplier.getId(),
                nextOrderNumber(organizationId), request.expectedDeliveryDate(), cleanNullable(request.notes())));
        List<PurchaseOrderLine> newLines = new ArrayList<>();
        for (PurchaseOrderLineRequest requestedLine : request.lines()) {
            Product product = productById.get(requestedLine.productId());
            SupplierProduct supplierProduct = supplierProductByProduct.get(product.getId());
            BigDecimal unitCost = requestedLine.unitCost() == null
                    ? supplierProduct == null
                        ? product.getUnitCost() : supplierProduct.getUnitCost()
                    : requestedLine.unitCost().setScale(2, RoundingMode.HALF_UP);
            newLines.add(new PurchaseOrderLine(organizationId, order.getId(), product.getId(),
                    product.getName(), product.getSku(),
                    supplierProduct == null ? null : supplierProduct.getSupplierSku(), product.getUnit(),
                    requestedLine.quantity(), unitCost));
        }
        orderLines.saveAll(newLines);
        return toOrderResponse(order);
    }

    @Transactional
    public List<PurchaseOrderResponse> createOrders(List<PurchaseOrderRequest> requests) {
        if (requests.stream().map(PurchaseOrderRequest::supplierId).distinct().count() != requests.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only one draft per supplier may be created in a batch");
        }
        return requests.stream().map(this::createOrder).toList();
    }

    @Transactional
    public PurchaseOrderResponse dispatchOrder(UUID orderId) {
        PurchaseOrder order = getOrderForUpdate(orderId);
        if (order.getStatus() != PurchaseOrderStatus.DRAFT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only draft orders can be marked as ordered");
        }
        order.markOrdered();
        return toOrderResponse(order);
    }

    @Transactional
    public PurchaseOrderResponse receiveOrder(UUID orderId, ReceivePurchaseOrderRequest request) {
        PurchaseOrder order = getOrderForUpdate(orderId);
        if (order.getStatus() != PurchaseOrderStatus.ORDERED
                && order.getStatus() != PurchaseOrderStatus.PARTIALLY_RECEIVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order is not awaiting delivery");
        }
        List<PurchaseOrderLine> existingLines = orderLines.findAllByPurchaseOrderIdOrderByProductNameAsc(orderId);
        Map<UUID, PurchaseOrderLine> lineByProduct = new HashMap<>();
        existingLines.forEach(line -> lineByProduct.put(line.getProductId(), line));

        Map<UUID, Integer> quantitiesByProduct = new HashMap<>();
        for (ReceivedLineRequest received : request.lines()) {
            if (received.quantity() <= 0
                    || quantitiesByProduct.putIfAbsent(received.productId(), received.quantity()) != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Received quantities must be positive and each product may appear once");
            }
            PurchaseOrderLine line = lineByProduct.get(received.productId());
            if (line == null || received.quantity() > line.getOrderedQuantity() - line.getReceivedQuantity()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Received quantity exceeds the outstanding order quantity");
            }
        }

        quantitiesByProduct.keySet().stream().sorted().forEach(productId -> {
            products.findByIdForUpdateAndOrganizationId(productId, order.getOrganizationId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
        });

        Instant receivedAt = Instant.now();
        quantitiesByProduct.forEach((productId, quantity) -> {
            PurchaseOrderLine line = lineByProduct.get(productId);
            line.receive(quantity);
            movements.save(new StockMovement(order.getOrganizationId(), productId, StockMovementType.INBOUND,
                    quantity, receivedAt, "Prijem porudžbine " + order.getOrderNumber(),
                    order.getId(), order.getOrderNumber()));
        });
        boolean hasOutstandingItems = existingLines.stream()
                .anyMatch(line -> line.getReceivedQuantity() < line.getOrderedQuantity());
        order.markReceived(hasOutstandingItems, receivedAt);
        return toOrderResponse(order);
    }

    @Transactional
    public PurchaseOrderResponse cancelOrder(UUID orderId) {
        PurchaseOrder order = getOrderForUpdate(orderId);
        if (order.getStatus() != PurchaseOrderStatus.DRAFT
                && order.getStatus() != PurchaseOrderStatus.ORDERED
                && order.getStatus() != PurchaseOrderStatus.PARTIALLY_RECEIVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This order cannot be cancelled");
        }
        order.cancel();
        return toOrderResponse(order);
    }

    private PurchaseOrder getOrderForUpdate(UUID orderId) {
        UUID organizationId = organizationContext.currentOrganizationId();
        return orders.findByIdForUpdateAndOrganizationId(orderId, organizationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Purchase order not found"));
    }

    private String nextOrderNumber(UUID organizationId) {
        String number;
        do {
            number = "PO-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        } while (orders.existsByOrganizationIdAndOrderNumber(organizationId, number));
        return number;
    }

    private List<PurchaseOrderStatus> openOrderStatuses() {
        return List.of(PurchaseOrderStatus.DRAFT, PurchaseOrderStatus.ORDERED,
                PurchaseOrderStatus.PARTIALLY_RECEIVED);
    }

    private PurchaseOrderResponse toOrderResponse(PurchaseOrder order) {
        UUID organizationId = order.getOrganizationId();
        Supplier supplier = suppliers.findByIdAndOrganizationId(order.getSupplierId(), organizationId)
                .orElseThrow(() -> new IllegalStateException("Purchase order refers to a missing supplier"));
        List<PurchaseOrderLineResponse> lines = orderLines
                .findAllByPurchaseOrderIdOrderByProductNameAsc(order.getId()).stream()
                .map(line -> new PurchaseOrderLineResponse(line.getProductId(), line.getProductName(),
                        line.getSku(), line.getSupplierSku(), line.getUnit(),
                        line.getOrderedQuantity(), line.getReceivedQuantity(),
                        line.getUnitCost(), line.getUnitCost().multiply(BigDecimal.valueOf(line.getOrderedQuantity()))))
                .toList();
        BigDecimal total = lines.stream().map(PurchaseOrderLineResponse::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new PurchaseOrderResponse(order.getId(), order.getOrderNumber(), supplier.getId(),
                supplier.getName(), order.getStatus(), order.getExpectedDeliveryDate(), order.getNotes(),
                order.getCreatedAt(), order.getOrderedAt(), order.getLastReceivedAt(), lines, total);
    }

    private static SupplierResponse toSupplierResponse(Supplier supplier) {
        return new SupplierResponse(supplier.getId(), supplier.getName(), supplier.getEmail(),
                supplier.getPhone(), supplier.getNotes(), supplier.isActive(), supplier.getCreatedAt());
    }

    private static String clean(String value) {
        return value.trim();
    }

    private static String cleanNullable(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }
}
