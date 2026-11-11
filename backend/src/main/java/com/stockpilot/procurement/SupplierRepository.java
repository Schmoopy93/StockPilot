package com.stockpilot.procurement;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplierRepository extends JpaRepository<Supplier, UUID> {
    List<Supplier> findAllByOrganizationIdOrderByNameAsc(UUID organizationId);
    Optional<Supplier> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
