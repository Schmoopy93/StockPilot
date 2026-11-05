package com.stockpilot.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.Optional;
import com.stockpilot.inventory.InventoryDtos.MovementRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReplenishmentCalculationTest {

    @Mock
    private ProductRepository products;

    @Mock
    private StockMovementRepository movements;

    private InventoryService inventory;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        inventory = new InventoryService(products, movements, 30, 3);
    }

    @Test
    void recommendsDemandDuringLeadTimeAndSafetyBuffer() {
        UUID id = UUID.randomUUID();
        Product product = new Product("TEST-1", "Test item", "Test", "pcs", 5, 6, new BigDecimal("2.50"));
        ReflectionTestUtils.setField(product, "id", id);
        when(movements.getOnHand(id)).thenReturn(10L);
        when(movements.getOutboundQuantitySince(org.mockito.ArgumentMatchers.eq(id),
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
        Product product = new Product("TEST-2", "Slow item", "Test", "pcs", 8, 4, new BigDecimal("1.00"));
        ReflectionTestUtils.setField(product, "id", id);
        when(movements.getOnHand(id)).thenReturn(3L);
        when(movements.getOutboundQuantitySince(org.mockito.ArgumentMatchers.eq(id),
                org.mockito.ArgumentMatchers.any(Instant.class))).thenReturn(0L);

        var result = inventory.recommendation(product, Instant.now().minusSeconds(30 * 86400L));

        assertThat(result.recommendedQuantity()).isEqualTo(5L);
        assertThat(result.lowConfidence()).isTrue();
    }

    @Test
    void rejectsOutboundMovementThatWouldMakeStockNegative() {
        UUID id = UUID.randomUUID();
        Product product = new Product("TEST-3", "Limited item", "Test", "pcs", 2, 4, BigDecimal.ONE);
        ReflectionTestUtils.setField(product, "id", id);
        when(products.findByIdForUpdate(id)).thenReturn(Optional.of(product));
        when(movements.getOnHand(id)).thenReturn(2L);

        assertThatThrownBy(() -> inventory.recordMovement(
                new MovementRequest(id, StockMovementType.OUTBOUND, 3, "Sale")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        verify(movements, never()).save(org.mockito.ArgumentMatchers.any(StockMovement.class));
    }
}
