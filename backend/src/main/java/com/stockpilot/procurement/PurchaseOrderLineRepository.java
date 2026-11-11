package com.stockpilot.procurement;

import com.stockpilot.inventory.StockAggregate;
import java.util.List;
import java.util.UUID;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PurchaseOrderLineRepository extends JpaRepository<PurchaseOrderLine, UUID> {
    List<PurchaseOrderLine> findAllByPurchaseOrderIdOrderByProductNameAsc(UUID purchaseOrderId);
    List<PurchaseOrderLine> findAllByOrganizationIdAndPurchaseOrderIdIn(
            UUID organizationId, Collection<UUID> purchaseOrderIds);

    @Query("""
            select new com.stockpilot.inventory.StockAggregate(line.productId,
                sum(line.orderedQuantity - line.receivedQuantity))
            from PurchaseOrderLine line, PurchaseOrder purchaseOrder
            where line.purchaseOrderId = purchaseOrder.id
              and line.organizationId = :organizationId
              and purchaseOrder.organizationId = :organizationId
              and purchaseOrder.status in :statuses
            group by line.productId
            """)
    List<StockAggregate> getOutstandingQuantities(
            @Param("organizationId") UUID organizationId,
            @Param("statuses") List<PurchaseOrderStatus> statuses);

    @Query("""
            select coalesce(sum(line.unitCost * (line.orderedQuantity - line.receivedQuantity)), 0)
            from PurchaseOrderLine line, PurchaseOrder purchaseOrder
            where line.purchaseOrderId = purchaseOrder.id
              and line.organizationId = :organizationId
              and purchaseOrder.organizationId = :organizationId
              and purchaseOrder.status in :statuses
            """)
    java.math.BigDecimal getOutstandingValue(
            @Param("organizationId") UUID organizationId,
            @Param("statuses") List<PurchaseOrderStatus> statuses);
}
