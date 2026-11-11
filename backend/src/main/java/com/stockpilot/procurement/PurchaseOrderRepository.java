package com.stockpilot.procurement;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID>,
        JpaSpecificationExecutor<PurchaseOrder> {
    long countByOrganizationIdAndStatusIn(UUID organizationId, List<PurchaseOrderStatus> statuses);
    long countByOrganizationIdAndSupplierId(UUID organizationId, UUID supplierId);
    long countByOrganizationIdAndSupplierIdAndStatusIn(
            UUID organizationId, UUID supplierId, List<PurchaseOrderStatus> statuses);
    boolean existsByOrganizationIdAndOrderNumber(UUID organizationId, String orderNumber);
    Optional<PurchaseOrder> findByIdAndOrganizationId(UUID id, UUID organizationId);
    List<PurchaseOrder> findAllByOrganizationIdAndSupplierIdOrderByCreatedAtDesc(UUID organizationId, UUID supplierId);
    List<PurchaseOrder> findTop100ByOrganizationIdAndSupplierIdOrderByCreatedAtDesc(
            UUID organizationId, UUID supplierId);
    List<PurchaseOrder> findAllByOrganizationIdAndSupplierIdInOrderByCreatedAtDesc(
            UUID organizationId, List<UUID> supplierIds);
    List<PurchaseOrder> findAllByOrganizationIdAndIdIn(UUID organizationId, List<UUID> ids);

    @Query(value = """
            select count(*)
            from purchase_orders
            where organization_id = :organizationId
              and supplier_id = :supplierId
              and status = 'RECEIVED'
              and last_received_at is not null
              and expected_delivery_date is not null
              and cast(last_received_at at time zone 'Europe/Belgrade' as date) <= expected_delivery_date
            """, nativeQuery = true)
    long countOnTimeDeliveries(@Param("organizationId") UUID organizationId,
                               @Param("supplierId") UUID supplierId);

    @Query(value = """
            select count(*)
            from purchase_orders
            where organization_id = :organizationId
              and supplier_id = :supplierId
              and status = 'RECEIVED'
              and last_received_at is not null
              and expected_delivery_date is not null
              and cast(last_received_at at time zone 'Europe/Belgrade' as date) > expected_delivery_date
            """, nativeQuery = true)
    long countLateDeliveries(@Param("organizationId") UUID organizationId,
                             @Param("supplierId") UUID supplierId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from PurchaseOrder o where o.id = :id and o.organizationId = :organizationId")
    Optional<PurchaseOrder> findByIdForUpdateAndOrganizationId(
            @Param("id") UUID id, @Param("organizationId") UUID organizationId);
}
