package com.stockpilot.inventory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, UUID> {
    List<Product> findAllByOrganizationIdAndActiveTrueOrderByNameAsc(UUID organizationId);
    Optional<Product> findByOrganizationIdAndSkuIgnoreCase(UUID organizationId, String sku);
    Optional<Product> findByIdAndOrganizationId(UUID id, UUID organizationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id and p.organizationId = :organizationId")
    Optional<Product> findByIdForUpdateAndOrganizationId(
            @Param("id") UUID id, @Param("organizationId") UUID organizationId);
}
