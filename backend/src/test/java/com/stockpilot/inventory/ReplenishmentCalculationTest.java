package com.stockpilot.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import com.stockpilot.currency.CurrencyService;
import com.stockpilot.procurement.PurchaseOrderLineRepository;
import com.stockpilot.procurement.PreferredSupplierProduct;
import com.stockpilot.procurement.SupplierProductRepository;
import com.stockpilot.inventory.InventoryDtos.MovementRequest;
import com.stockpilot.organization.OrganizationContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ReplenishmentCalculationTest {

    @Mock
    private ProductRepository products;

    @Mock
    private StockMovementRepository movements;

    @Mock
    private PurchaseOrderLineRepository orderLines;

    @Mock
    private SupplierProductRepository supplierProducts;

    @Mock
    private OrganizationContext organizationContext;

    @Mock
    private CurrencyService currency;

    private InventoryService inventory;
    private final UUID organizationId = UUID.randomUUID();

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        inventory = new InventoryService(products, movements, orderLines, supplierProducts,
                organizationContext, currency, 30, 3);
    }

    @Test
    void recommendsDemandDuringLeadTimeAndSafetyBuffer() {
        UUID id = UUID.randomUUID();
        Product product = new Product(organizationId, "TEST-1", "Test item", "Test", "pcs", 5, 6, new BigDecimal("2.50"));
        ReflectionTestUtils.setField(product, "id", id);
        when(movements.getOnHand(organizationId, id)).thenReturn(10L);
        when(movements.getOutboundQuantitySince(org.mockito.ArgumentMatchers.eq(organizationId),
                org.mockito.ArgumentMatchers.eq(id),
                org.mockito.ArgumentMatchers.any(Instant.class))).thenReturn(60L);

        var result = inventory.recommendation(product, Instant.now().minusSeconds(30 * 86400L));

        assertThat(result.averageDailyDemand()).isEqualTo(2.0);
        assertThat(result.recommendedQuantity()).isEqualTo(8L);
        assertThat(result.estimatedCost()).isEqualByComparingTo("20.00");
        assertThat(result.lowConfidence()).isFalse();
    }

    @Test
    void usesMinimumStockAndMarksRecommendationLowConfidenceWithoutDemand() {
        UUID id = UUID.randomUUID();
        Product product = new Product(organizationId, "TEST-2", "Slow item", "Test", "pcs", 8, 4, new BigDecimal("1.00"));
        ReflectionTestUtils.setField(product, "id", id);
        when(movements.getOnHand(organizationId, id)).thenReturn(3L);
        when(movements.getOutboundQuantitySince(org.mockito.ArgumentMatchers.eq(organizationId),
                org.mockito.ArgumentMatchers.eq(id),
                org.mockito.ArgumentMatchers.any(Instant.class))).thenReturn(0L);

        var result = inventory.recommendation(product, Instant.now().minusSeconds(30 * 86400L));

        assertThat(result.recommendedQuantity()).isEqualTo(5L);
        assertThat(result.lowConfidence()).isTrue();
    }

    @Test
    void subtractsOutstandingPurchaseOrderQuantitiesFromRecommendations() {
        UUID id = UUID.randomUUID();
        Product product = new Product(organizationId, "TEST-4", "Ordered item", "Test", "pcs", 8, 4,
                new BigDecimal("1.00"));
        ReflectionTestUtils.setField(product, "id", id);
        when(organizationContext.currentOrganizationId()).thenReturn(organizationId);
        when(movements.getAllOnHand(organizationId)).thenReturn(java.util.List.of());
        when(movements.getAllOutboundSince(org.mockito.ArgumentMatchers.eq(organizationId),
                org.mockito.ArgumentMatchers.any(Instant.class))).thenReturn(java.util.List.of());
        when(orderLines.getOutstandingQuantities(org.mockito.ArgumentMatchers.eq(organizationId),
                org.mockito.ArgumentMatchers.anyList())).thenReturn(java.util.List.of(new StockAggregate(id, 2)));
        when(products.findAllByOrganizationIdAndActiveTrueOrderByNameAsc(organizationId))
                .thenReturn(java.util.List.of(product));

        var result = inventory.recommendations().getFirst();

        assertThat(result.onOrder()).isEqualTo(2);
        assertThat(result.recommendedQuantity()).isEqualTo(6);
    }

    @Test
    void usesPreferredSupplierLeadTimePriceAndMinimumOrderQuantity() {
        UUID id = UUID.randomUUID();
        UUID supplierId = UUID.randomUUID();
        Product product = new Product(organizationId, "TEST-5", "Preferred item", "Test", "pcs", 8, 8,
                new BigDecimal("4.00"));
        ReflectionTestUtils.setField(product, "id", id);
        when(organizationContext.currentOrganizationId()).thenReturn(organizationId);
        when(movements.getAllOnHand(organizationId)).thenReturn(java.util.List.of());
        when(movements.getAllOutboundSince(org.mockito.ArgumentMatchers.eq(organizationId),
                org.mockito.ArgumentMatchers.any(Instant.class))).thenReturn(java.util.List.of());
        when(orderLines.getOutstandingQuantities(org.mockito.ArgumentMatchers.eq(organizationId),
                org.mockito.ArgumentMatchers.anyList())).thenReturn(java.util.List.of());
        when(supplierProducts.findPreferredByOrganizationId(organizationId)).thenReturn(java.util.List.of(
                new PreferredSupplierProduct(id, supplierId, "Primary supplier", "SUP-5",
                        new BigDecimal("2.75"), 10, 2)));
        when(products.findAllByOrganizationIdAndActiveTrueOrderByNameAsc(organizationId))
                .thenReturn(java.util.List.of(product));

        var result = inventory.recommendations().getFirst();

        assertThat(result.preferredSupplierId()).isEqualTo(supplierId);
        assertThat(result.preferredSupplierName()).isEqualTo("Primary supplier");
        assertThat(result.leadTimeDays()).isEqualTo(2);
        assertThat(result.supplierUnitCost()).isEqualByComparingTo("2.75");
        assertThat(result.recommendedQuantity()).isEqualTo(10);
        assertThat(result.estimatedCost()).isEqualByComparingTo("27.50");
    }

    @Test
    void rejectsOutboundMovementThatWouldMakeStockNegative() {
        UUID id = UUID.randomUUID();
        Product product = new Product(organizationId, "TEST-3", "Limited item", "Test", "pcs", 2, 4, BigDecimal.ONE);
        ReflectionTestUtils.setField(product, "id", id);
        when(organizationContext.currentOrganizationId()).thenReturn(organizationId);
        when(products.findByIdForUpdateAndOrganizationId(id, organizationId)).thenReturn(Optional.of(product));
        when(movements.getOnHand(organizationId, id)).thenReturn(2L);

        assertThatThrownBy(() -> inventory.recordMovement(
                new MovementRequest(id, StockMovementType.OUTBOUND, 3, "Sale")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        verify(movements, never()).save(org.mockito.ArgumentMatchers.any(StockMovement.class));
    }

    @Test
    void scopesProductAndStockReadsToCurrentOrganization() {
        when(organizationContext.currentOrganizationId()).thenReturn(organizationId);
        when(movements.getAllOnHand(organizationId)).thenReturn(java.util.List.of());
        when(products.findAllByOrganizationIdAndActiveTrueOrderByNameAsc(organizationId))
                .thenReturn(java.util.List.of());

        assertThat(inventory.listProducts()).isEmpty();

        verify(movements).getAllOnHand(organizationId);
        verify(products).findAllByOrganizationIdAndActiveTrueOrderByNameAsc(organizationId);
    }

    @Test
    void cannotRecordMovementForProductOutsideCurrentOrganization() {
        UUID foreignProductId = UUID.randomUUID();
        when(organizationContext.currentOrganizationId()).thenReturn(organizationId);
        when(products.findByIdForUpdateAndOrganizationId(foreignProductId, organizationId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> inventory.recordMovement(
                new MovementRequest(foreignProductId, StockMovementType.INBOUND, 1, "Foreign product")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        verify(movements, never()).save(any(StockMovement.class));
    }
}
