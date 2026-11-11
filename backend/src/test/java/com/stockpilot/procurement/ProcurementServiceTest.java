package com.stockpilot.procurement;

import static com.stockpilot.procurement.ProcurementDtos.ReceivePurchaseOrderRequest;
import static com.stockpilot.procurement.ProcurementDtos.ReceivedLineRequest;
import static com.stockpilot.procurement.ProcurementDtos.PurchaseOrderLineRequest;
import static com.stockpilot.procurement.ProcurementDtos.PurchaseOrderRequest;
import static com.stockpilot.procurement.ProcurementDtos.SupplierProductRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stockpilot.inventory.Product;
import com.stockpilot.currency.CurrencyService;
import com.stockpilot.inventory.ProductRepository;
import com.stockpilot.inventory.StockMovement;
import com.stockpilot.inventory.StockMovementRepository;
import com.stockpilot.organization.OrganizationContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ProcurementServiceTest {

    @Mock private SupplierRepository suppliers;
    @Mock private SupplierProductRepository supplierProducts;
    @Mock private PurchaseOrderRepository orders;
    @Mock private PurchaseOrderLineRepository lines;
    @Mock private ProductRepository products;
    @Mock private StockMovementRepository movements;
    @Mock private OrganizationContext organizationContext;
    @Mock private CurrencyService currency;

    private ProcurementService procurement;
    private final UUID organizationId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        procurement = new ProcurementService(suppliers, supplierProducts, orders, lines,
                products, movements, organizationContext, currency);
        when(organizationContext.currentOrganizationId()).thenReturn(organizationId);
    }

    @Test
    void receivesPartialDeliveryAndCreatesInboundStockMovement() {
        UUID orderId = UUID.randomUUID();
        UUID supplierId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        Supplier supplier = new Supplier(organizationId, "Supplier", null, null, null);
        PurchaseOrder order = new PurchaseOrder(organizationId, supplierId, "PO-12345678",
                LocalDate.now().plusDays(3), null);
        order.markOrdered();
        ReflectionTestUtils.setField(order, "id", orderId);
        PurchaseOrderLine line = new PurchaseOrderLine(organizationId, orderId, productId,
                "Coffee", "COF-1", "pcs", 10, new BigDecimal("2.50"));
        Product product = new Product(organizationId, "COF-1", "Coffee", null, "pcs", 2, 4,
                new BigDecimal("2.50"));
        ReflectionTestUtils.setField(product, "id", productId);

        when(orders.findByIdForUpdateAndOrganizationId(orderId, organizationId)).thenReturn(Optional.of(order));
        when(lines.findAllByPurchaseOrderIdOrderByProductNameAsc(orderId)).thenReturn(List.of(line));
        when(products.findByIdForUpdateAndOrganizationId(productId, organizationId)).thenReturn(Optional.of(product));
        when(suppliers.findByIdAndOrganizationId(supplierId, organizationId)).thenReturn(Optional.of(supplier));

        var result = procurement.receiveOrder(orderId,
                new ReceivePurchaseOrderRequest(List.of(new ReceivedLineRequest(productId, 4))));

        assertThat(result.status()).isEqualTo(PurchaseOrderStatus.PARTIALLY_RECEIVED);
        assertThat(result.lines()).singleElement().satisfies(received -> {
            assertThat(received.receivedQuantity()).isEqualTo(4);
            assertThat(received.orderedQuantity()).isEqualTo(10);
        });
        verify(movements).save(any(StockMovement.class));
    }

    @Test
    void completesOrderWhenAllOutstandingQuantityIsReceived() {
        UUID orderId = UUID.randomUUID();
        UUID supplierId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        Supplier supplier = new Supplier(organizationId, "Supplier", null, null, null);
        PurchaseOrder order = new PurchaseOrder(organizationId, supplierId, "PO-87654321", null, null);
        order.markOrdered();
        ReflectionTestUtils.setField(order, "id", orderId);
        PurchaseOrderLine line = new PurchaseOrderLine(organizationId, orderId, productId,
                "Coffee", "COF-1", "pcs", 3, BigDecimal.ONE);
        Product product = new Product(organizationId, "COF-1", "Coffee", null, "pcs", 2, 4, BigDecimal.ONE);
        ReflectionTestUtils.setField(product, "id", productId);
        when(orders.findByIdForUpdateAndOrganizationId(orderId, organizationId)).thenReturn(Optional.of(order));
        when(lines.findAllByPurchaseOrderIdOrderByProductNameAsc(orderId)).thenReturn(List.of(line));
        when(products.findByIdForUpdateAndOrganizationId(productId, organizationId)).thenReturn(Optional.of(product));
        when(suppliers.findByIdAndOrganizationId(supplierId, organizationId)).thenReturn(Optional.of(supplier));

        var result = procurement.receiveOrder(orderId,
                new ReceivePurchaseOrderRequest(List.of(new ReceivedLineRequest(productId, 3))));

        assertThat(result.status()).isEqualTo(PurchaseOrderStatus.RECEIVED);
        assertThat(result.total()).isEqualByComparingTo("3.00");
    }

    @Test
    void rejectsReceivingMoreThanOutstandingWithoutChangingStock() {
        UUID orderId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        PurchaseOrder order = new PurchaseOrder(organizationId, UUID.randomUUID(), "PO-12345678", null, null);
        order.markOrdered();
        ReflectionTestUtils.setField(order, "id", orderId);
        PurchaseOrderLine line = new PurchaseOrderLine(organizationId, orderId, productId,
                "Coffee", "COF-1", "pcs", 3, BigDecimal.ONE);
        line.receive(2);
        when(orders.findByIdForUpdateAndOrganizationId(orderId, organizationId)).thenReturn(Optional.of(order));
        when(lines.findAllByPurchaseOrderIdOrderByProductNameAsc(orderId)).thenReturn(List.of(line));

        assertThatThrownBy(() -> procurement.receiveOrder(orderId,
                new ReceivePurchaseOrderRequest(List.of(new ReceivedLineRequest(productId, 2)))))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        verify(movements, never()).save(any(StockMovement.class));
    }

    @Test
    void doesNotExposePurchaseOrdersFromAnotherOrganization() {
        UUID orderId = UUID.randomUUID();
        when(orders.findByIdForUpdateAndOrganizationId(orderId, organizationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> procurement.cancelOrder(orderId))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void rejectsOrderLinesBelowSupplierMinimumBeforeCreatingOrder() {
        UUID supplierId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        Supplier supplier = new Supplier(organizationId, "Supplier", null, null, null);
        ReflectionTestUtils.setField(supplier, "id", supplierId);
        Product product = new Product(organizationId, "COF-1", "Coffee", null, "pcs", 2, 4,
                new BigDecimal("2.50"));
        ReflectionTestUtils.setField(product, "id", productId);
        SupplierProduct catalogItem = new SupplierProduct(organizationId, supplierId, productId,
                "SUP-COF", new BigDecimal("2.25"), 5, 3, true);
        when(suppliers.findByIdAndOrganizationId(supplierId, organizationId)).thenReturn(Optional.of(supplier));
        when(products.findAllByOrganizationIdAndIdIn(organizationId, List.of(productId)))
                .thenReturn(List.of(product));
        when(supplierProducts.findByOrganizationIdAndSupplierIdAndProductId(
                organizationId, supplierId, productId)).thenReturn(Optional.of(catalogItem));

        assertThatThrownBy(() -> procurement.createOrder(new PurchaseOrderRequest(supplierId, null, null,
                List.of(new PurchaseOrderLineRequest(productId, 4, null)))))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verify(orders, never()).saveAndFlush(any(PurchaseOrder.class));
    }

    @Test
    void makingSupplierPreferredClearsPreviousPreferredLinkForProduct() {
        UUID supplierId = UUID.randomUUID();
        UUID previousSupplierId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        Supplier supplier = new Supplier(organizationId, "New supplier", null, null, null);
        ReflectionTestUtils.setField(supplier, "id", supplierId);
        Product product = new Product(organizationId, "COF-2", "Coffee", null, "pcs", 2, 4,
                new BigDecimal("2.50"));
        ReflectionTestUtils.setField(product, "id", productId);
        SupplierProduct previousPreferred = new SupplierProduct(organizationId, previousSupplierId,
                productId, null, new BigDecimal("2.80"), 1, null, true);
        when(suppliers.findByIdAndOrganizationId(supplierId, organizationId)).thenReturn(Optional.of(supplier));
        when(products.findByIdForUpdateAndOrganizationId(productId, organizationId))
                .thenReturn(Optional.of(product));
        when(supplierProducts.findByOrganizationIdAndSupplierIdAndProductId(
                organizationId, supplierId, productId)).thenReturn(Optional.empty());
        when(supplierProducts.findAllByOrganizationIdAndProductIdAndPreferredSupplierTrue(
                organizationId, productId)).thenReturn(List.of(previousPreferred));
        when(supplierProducts.save(any(SupplierProduct.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var saved = procurement.saveSupplierProduct(supplierId, productId,
                new SupplierProductRequest(" NEW-COF ", new BigDecimal("2.35"), 4, 5, true));

        assertThat(previousPreferred.isPreferredSupplier()).isFalse();
        assertThat(saved.preferredSupplier()).isTrue();
        assertThat(saved.supplierSku()).isEqualTo("NEW-COF");
        assertThat(saved.unitCost()).isEqualByComparingTo("2.35");
        assertThat(saved.minimumOrderQuantity()).isEqualTo(4);
    }
}
