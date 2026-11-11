package com.stockpilot.auth;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationMembershipRepository
        extends JpaRepository<OrganizationMembership, OrganizationMembershipId> {

    Optional<OrganizationMembership> findFirstByIdUserIdOrderByCreatedAtAsc(UUID userId);
}
