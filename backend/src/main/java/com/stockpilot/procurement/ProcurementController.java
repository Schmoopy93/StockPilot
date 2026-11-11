package com.stockpilot.procurement;

import static com.stockpilot.procurement.ProcurementDtos.*;

import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api")
@Validated
public class ProcurementController {

    private final ProcurementService procurement;

    public ProcurementController(ProcurementService procurement) {
        this.procurement = procurement;
    }

    @GetMapping("/suppliers")
    public List<SupplierResponse> suppliers() {
        return procurement.listSuppliers();
    }

    @GetMapping("/suppliers/{supplierId}/performance")
    public SupplierPerformanceResponse supplierPerformance(@PathVariable UUID supplierId) {
        return procurement.supplierPerformance(supplierId);
    }

    @PostMapping("/suppliers")
    public ResponseEntity<SupplierResponse> createSupplier(@Valid @RequestBody SupplierRequest request) {
        SupplierResponse created = procurement.createSupplier(request);
        return ResponseEntity.created(URI.create("/api/suppliers/" + created.id())).body(created);
    }

    @PatchMapping("/suppliers/{supplierId}")
    public SupplierResponse updateSupplier(@PathVariable UUID supplierId,
                                           @Valid @RequestBody SupplierRequest request) {
        return procurement.updateSupplier(supplierId, request);
    }

    @GetMapping("/supplier-products")
    public List<SupplierProductResponse> supplierProducts() {
        return procurement.listSupplierProducts();
    }

    @PutMapping("/suppliers/{supplierId}/products/{productId}")
    public SupplierProductResponse saveSupplierProduct(
            @PathVariable UUID supplierId,
            @PathVariable UUID productId,
            @Valid @RequestBody SupplierProductRequest request) {
        return procurement.saveSupplierProduct(supplierId, productId, request);
    }

    @DeleteMapping("/suppliers/{supplierId}/products/{productId}")
    public ResponseEntity<Void> deleteSupplierProduct(
            @PathVariable UUID supplierId, @PathVariable UUID productId) {
        procurement.deleteSupplierProduct(supplierId, productId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/purchase-orders")
    public Page<PurchaseOrderResponse> purchaseOrders(
            @RequestParam(required = false) UUID supplierId,
            @RequestParam(required = false) PurchaseOrderStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "false") boolean overdue,
            @RequestParam(defaultValue = "0") @Min(0) @Max(1_000_000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Start date must not be after end date");
        }
        return procurement.listOrders(supplierId, status, from, to, overdue,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    @GetMapping("/purchase-orders/summary")
    public PurchaseOrderSummary purchaseOrderSummary() {
        return procurement.orderSummary();
    }

    @PostMapping("/purchase-orders")
    public ResponseEntity<PurchaseOrderResponse> createPurchaseOrder(
            @Valid @RequestBody PurchaseOrderRequest request) {
        PurchaseOrderResponse created = procurement.createOrder(request);
        return ResponseEntity.created(URI.create("/api/purchase-orders/" + created.id())).body(created);
    }

    @PostMapping("/purchase-orders/batch")
    public List<PurchaseOrderResponse> createPurchaseOrders(
            @Valid @RequestBody BatchPurchaseOrderRequest request) {
        return procurement.createOrders(request.orders());
    }

    @PostMapping("/purchase-orders/{orderId}/dispatch")
    public PurchaseOrderResponse dispatchPurchaseOrder(@PathVariable UUID orderId) {
        return procurement.dispatchOrder(orderId);
    }

    @PostMapping("/purchase-orders/{orderId}/receive")
    public PurchaseOrderResponse receivePurchaseOrder(@PathVariable UUID orderId,
                                                       @Valid @RequestBody ReceivePurchaseOrderRequest request) {
        return procurement.receiveOrder(orderId, request);
    }

    @PostMapping("/purchase-orders/{orderId}/cancel")
    public PurchaseOrderResponse cancelPurchaseOrder(@PathVariable UUID orderId) {
        return procurement.cancelOrder(orderId);
    }
}
