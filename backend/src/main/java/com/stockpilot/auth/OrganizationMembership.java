package com.stockpilot.auth;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "organization_memberships")
public class OrganizationMembership {

    @EmbeddedId
    private OrganizationMembershipId id;

    @Enumerated(EnumType.STRING)
    private AuthRole role;

    private Instant createdAt;

    protected OrganizationMembership() {
    }

    public OrganizationMembership(OrganizationMembershipId id, AuthRole role) {
        this.id = id;
        this.role = role;
        this.createdAt = Instant.now();
    }

    public OrganizationMembershipId getId() { return id; }
    public AuthRole getRole() { return role; }
}
