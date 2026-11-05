package com.stockpilot.organization;

import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("demo")
public class DemoOrganizationContext implements OrganizationContext {

    public static final UUID ORGANIZATION_ID = UUID.fromString("20000000-0000-4000-8000-000000000001");

    @Override
    public UUID currentOrganizationId() {
        return ORGANIZATION_ID;
    }
}
