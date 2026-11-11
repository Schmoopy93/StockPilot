package com.stockpilot.organization;

import com.stockpilot.auth.StockPilotPrincipal;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@Profile("!demo")
public class AuthenticatedOrganizationContext implements OrganizationContext {

    @Override
    public UUID currentOrganizationId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof StockPilotPrincipal principal)) {
            throw new IllegalStateException("Authenticated organization context is required");
        }
        return principal.organizationId();
    }
}
