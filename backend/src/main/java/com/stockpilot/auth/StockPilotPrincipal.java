package com.stockpilot.auth;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.time.Instant;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class StockPilotPrincipal implements UserDetails {

    private final UUID userId;
    private final UUID organizationId;
    private final String email;
    private final String passwordHash;
    private final String displayName;
    private final AuthRole role;
    private final Instant emailVerifiedAt;
    private final int sessionVersion;

    public StockPilotPrincipal(AccountUser user, OrganizationMembership membership) {
        this.userId = user.getId();
        this.organizationId = membership.getId().getOrganizationId();
        this.email = user.getEmail();
        this.passwordHash = user.getPasswordHash();
        this.displayName = user.getDisplayName();
        this.role = membership.getRole();
        this.emailVerifiedAt = user.getEmailVerifiedAt();
        this.sessionVersion = user.getSessionVersion();
    }

    public UUID userId() { return userId; }
    public UUID organizationId() { return organizationId; }
    public String displayName() { return displayName; }
    public AuthRole role() { return role; }
    public Instant emailVerifiedAt() { return emailVerifiedAt; }
    public int sessionVersion() { return sessionVersion; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() { return passwordHash; }

    @Override
    public String getUsername() { return email; }

    @Override
    public boolean isEnabled() { return emailVerifiedAt != null; }
}
