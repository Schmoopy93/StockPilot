package com.stockpilot.procurement;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SupplierProductRepository extends JpaRepository<SupplierProduct, UUID> {

    @Query("""
            select new com.stockpilot.procurement.SupplierProductResponse(
                supplierProduct.id, supplierProduct.supplierId, supplier.name,
                supplierProduct.productId, product.name, product.sku,
                supplierProduct.supplierSku, supplierProduct.unitCost,
                supplierProduct.minimumOrderQuantity, supplierProduct.leadTimeDays,
                supplierProduct.preferredSupplier)
            from SupplierProduct supplierProduct, Supplier supplier, Product product
            where supplierProduct.organizationId = :organizationId
              and supplierProduct.supplierId = supplier.id
              and supplier.organizationId = :organizationId
              and supplierProduct.productId = product.id
              and product.organizationId = :organizationId
            order by supplier.name, product.name
            """)
    List<SupplierProductResponse> findAllResponsesByOrganizationId(
            @Param("organizationId") UUID organizationId);

    @Query("""
            select new com.stockpilot.procurement.PreferredSupplierProduct(
                supplierProduct.productId, supplierProduct.supplierId, supplier.name,
                supplierProduct.supplierSku, supplierProduct.unitCost,
                supplierProduct.minimumOrderQuantity, supplierProduct.leadTimeDays)
            from SupplierProduct supplierProduct, Supplier supplier
            where supplierProduct.organizationId = :organizationId
              and supplierProduct.preferredSupplier = true
              and supplierProduct.supplierId = supplier.id
              and supplier.organizationId = :organizationId
              and supplier.active = true
            """)
    List<PreferredSupplierProduct> findPreferredByOrganizationId(
            @Param("organizationId") UUID organizationId);

    @Query("""
            select new com.stockpilot.procurement.PreferredSupplierProduct(
                supplierProduct.productId, supplierProduct.supplierId, supplier.name,
                supplierProduct.supplierSku, supplierProduct.unitCost,
                supplierProduct.minimumOrderQuantity, supplierProduct.leadTimeDays)
            from SupplierProduct supplierProduct, Supplier supplier
            where supplierProduct.organizationId = :organizationId
              and supplierProduct.productId = :productId
              and supplierProduct.preferredSupplier = true
              and supplierProduct.supplierId = supplier.id
              and supplier.organizationId = :organizationId
              and supplier.active = true
            """)
    Optional<PreferredSupplierProduct> findPreferredByOrganizationIdAndProductId(
            @Param("organizationId") UUID organizationId, @Param("productId") UUID productId);

    Optional<SupplierProduct> findByOrganizationIdAndSupplierIdAndProductId(
            UUID organizationId, UUID supplierId, UUID productId);

    List<SupplierProduct> findAllByOrganizationIdAndProductIdAndPreferredSupplierTrue(
            UUID organizationId, UUID productId);
}
