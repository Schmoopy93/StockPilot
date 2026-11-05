package com.stockpilot.inventory;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {

    @Query("""
            select new com.stockpilot.inventory.StockAggregate(m.productId, sum(case
                when m.type = com.stockpilot.inventory.StockMovementType.INBOUND then m.quantity
                when m.type = com.stockpilot.inventory.StockMovementType.OUTBOUND then -m.quantity
                else m.quantity
            end))
            from StockMovement m
            group by m.productId
            """)
    List<StockAggregate> getAllOnHand();

    @Query("""
            select new com.stockpilot.inventory.StockAggregate(m.productId, sum(m.quantity))
            from StockMovement m
            where m.type = com.stockpilot.inventory.StockMovementType.OUTBOUND
              and m.occurredAt >= :since
            group by m.productId
            """)
    List<StockAggregate> getAllOutboundSince(@Param("since") Instant since);

    @Query("""
            select coalesce(sum(case
                when m.type = com.stockpilot.inventory.StockMovementType.INBOUND then m.quantity
                when m.type = com.stockpilot.inventory.StockMovementType.OUTBOUND then -m.quantity
                else m.quantity
            end), 0)
            from StockMovement m
            where m.productId = :productId
            """)
    long getOnHand(@Param("productId") UUID productId);

    @Query("""
            select coalesce(sum(m.quantity), 0)
            from StockMovement m
            where m.productId = :productId
              and m.type = com.stockpilot.inventory.StockMovementType.OUTBOUND
              and m.occurredAt >= :since
            """)
    long getOutboundQuantitySince(@Param("productId") UUID productId, @Param("since") Instant since);

    List<StockMovement> findTop8ByOrderByOccurredAtDesc();
}
